#include "simple_smaps_parser.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/syscall.h>
#include <unistd.h>

extern "C" void android_set_abort_message(const char* msg);

// Minimal smaps reproducer: allocate and retain one 80-byte C++ object for
// each smaps line observed while the kernel is streaming /proc/self/smaps.
namespace simple_smaps_parser {
namespace {

using smaps_parser::ParseLimits;
using smaps_parser::ParseResult;
using smaps_parser::ParseStats;

enum ElementKind : std::uint32_t {
    kOtherLine = 0,
    kMappingHeader = 1,
    kDetailLine = 2,
};

struct SmapsElement {
    SmapsElement* next = nullptr;
    std::uint64_t sequence = 0;
    std::uint32_t line_length = 0;
    std::uint32_t kind = kOtherLine;
    char line_prefix[56] = {};
};

static_assert(sizeof(SmapsElement) == 80,
        "smaps element allocation size must stay at 80 bytes");

// Handrolled to make allocations more clear. Only SmapsElements are getting allocated in the 
// parser loop
struct ElementList {
    ~ElementList() {
        while (head != nullptr) {
            SmapsElement* element = head;
            head = head->next;
            delete element;
        }
    }

    void append(SmapsElement* element) {
        if (tail == nullptr) {
            head = element;
        } else {
            tail->next = element;
        }
        tail = element;
    }

    SmapsElement* head = nullptr;
    SmapsElement* tail = nullptr;
};

int get_thread_id() {
    return static_cast<int>(syscall(SYS_gettid));
}

void set_error(ParseResult* result, const char* message) {
    if (result->error[0] == '\0') {
        std::snprintf(result->error, sizeof(result->error), "%s", message);
    }
}

void abort_for_limit(ParseStats* stats, const ParseLimits& limits, const char* reason) {
    const bool accepted_limit_reached =
            limits.max_accepted_vma_records != 0
            && stats->accepted_vma_records > limits.max_accepted_vma_records;
    const bool byte_limit_reached =
            limits.max_bytes_allocated != 0
            && stats->bytes_allocated > limits.max_bytes_allocated;
    if (!accepted_limit_reached && !byte_limit_reached) {
        return;
    }

    char message[256];
    std::snprintf(message, sizeof(message),
            "simple_smaps_parser guard abort after %s: accepted_vma_records=%llu "
            "max_accepted_vma_records=%llu bytes_allocated=%llu "
            "max_bytes_allocated=%llu",
            reason,
            static_cast<unsigned long long>(stats->accepted_vma_records),
            static_cast<unsigned long long>(limits.max_accepted_vma_records),
            static_cast<unsigned long long>(stats->bytes_allocated),
            static_cast<unsigned long long>(limits.max_bytes_allocated));
    android_set_abort_message(message);
    std::fprintf(stderr, "%s\n", message);
    std::fflush(stderr);
    std::abort();
}

bool is_hex_digit(char c) {
    return (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
}

bool is_mapping_header(const char* line, std::size_t length) {
    std::size_t i = 0;
    while (i < length && is_hex_digit(line[i])) {
        i++;
    }
    if (i == 0 || i >= length || line[i] != '-') {
        return false;
    }
    i++;

    const std::size_t end_start = i;
    while (i < length && is_hex_digit(line[i])) {
        i++;
    }
    return i > end_start && i < length && (line[i] == ' ' || line[i] == '\t');
}

bool is_detail_line(const char* line, std::size_t length) {
    std::size_t i = 0;
    while (i < length && (line[i] == ' ' || line[i] == '\t')) {
        i++;
    }
    const std::size_t key_start = i;
    while (i < length && line[i] != ':' && line[i] != ' ' && line[i] != '\t') {
        i++;
    }
    return i > key_start && i < length && line[i] == ':';
}

std::size_t trim_line_length(const char* line, std::size_t length) {
    while (length > 0 && (line[length - 1] == '\n' || line[length - 1] == '\r')) {
        length--;
    }
    return length;
}

SmapsElement* allocate_element(ParseStats* stats, const ParseLimits& limits,
        const char* line, std::size_t line_length, ElementKind kind,
        std::uint64_t sequence) {
    SmapsElement* element = new SmapsElement();
    element->sequence = sequence;
    element->line_length = static_cast<std::uint32_t>(
            std::min<std::size_t>(line_length, UINT32_MAX));
    element->kind = kind;
    const std::size_t prefix_length =
            std::min(line_length, sizeof(element->line_prefix));
    if (prefix_length != 0) {
        std::memcpy(element->line_prefix, line, prefix_length);
    }

    stats->bytes_allocated += sizeof(SmapsElement);
    if (kind == kMappingHeader) {
        stats->candidate_vma_records++;
        stats->accepted_vma_records++;
    } else if (kind == kDetailLine) {
        stats->detail_keys_inserted++;
    } else {
        stats->other_allocations++;
    }

    // The guard fires only after the triggering allocation has succeeded.
    abort_for_limit(stats, limits, "element allocation guard");
    return element;
}

void consume_line_remainder(FILE* file) {
    int ch;
    do {
        ch = std::fgetc(file);
    } while (ch != '\n' && ch != EOF);
}

void run_parser(ParseResult* result, const ParseLimits& limits) {
    result->worker_tid = get_thread_id();

    FILE* file = std::fopen("/proc/self/smaps", "r");
    if (file == nullptr) {
        set_error(result, "failed to open /proc/self/smaps");
        return;
    }

    ElementList elements;
    char line[8192];
    std::uint64_t sequence = 0;
    std::size_t current_details_per_vma = 0;
    bool saw_vma = false;

    while (std::fgets(line, sizeof(line), file) != nullptr) {
        const std::size_t raw_length = std::strlen(line);
        if (raw_length == sizeof(line) - 1 && line[raw_length - 1] != '\n') {
            consume_line_remainder(file);
        }

        const std::size_t line_length = trim_line_length(line, raw_length);
        result->stats.lines_read++;
        result->stats.max_line_length =
                std::max(result->stats.max_line_length, line_length);
        if (line_length == 0) {
            continue;
        }

        ElementKind kind = kOtherLine;
        if (is_mapping_header(line, line_length)) {
            if (saw_vma) {
                result->stats.max_details_per_vma =
                        std::max(result->stats.max_details_per_vma,
                                current_details_per_vma);
            }
            saw_vma = true;
            current_details_per_vma = 0;
            kind = kMappingHeader;
        } else if (is_detail_line(line, line_length)) {
            result->stats.detail_lines_seen++;
            current_details_per_vma++;
            kind = kDetailLine;
        }

        elements.append(allocate_element(&result->stats, limits, line, line_length,
                kind, sequence));
        sequence++;
    }

    if (saw_vma) {
        result->stats.max_details_per_vma =
                std::max(result->stats.max_details_per_vma,
                        current_details_per_vma);
    }

    std::fclose(file);
    result->completed = result->stats.accepted_vma_records > 0
            && result->stats.detail_keys_inserted > 0
            && result->stats.bytes_allocated > 0;
    if (!result->completed && result->error[0] == '\0') {
        set_error(result, "simple smaps parser returned incomplete counters");
    }
}

} // namespace

ParseResult run(int caller_tid, const ParseLimits& limits) {
    ParseResult result;
    result.caller_tid = caller_tid;
    run_parser(&result, limits);
    return result;
}

} // namespace simple_smaps_parser
