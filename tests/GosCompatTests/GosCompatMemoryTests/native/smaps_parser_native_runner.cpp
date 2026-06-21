#include "simple_smaps_parser.h"

#include <cstdint>
#include <iostream>
#include <sstream>
#include <string>
#include <sys/syscall.h>
#include <thread>
#include <unistd.h>

namespace {

constexpr std::uint64_t kMaxAcceptedVmaRecordsBeforeAbort = 16'384;
constexpr std::uint64_t kMaxBytesAllocatedBeforeAbort = 192ULL * 1024 * 1024;
constexpr const char* kParserMode = "simple";

int get_thread_id() {
    return static_cast<int>(syscall(SYS_gettid));
}

std::string describe_result(const smaps_parser::ParseResult& result) {
    std::ostringstream out;
    out << "completed=" << result.completed
            << ", pointerSize=" << result.pointer_size
            << ", callerTid=" << result.caller_tid
            << ", workerTid=" << result.worker_tid
            << ", linesRead=" << result.stats.lines_read
            << ", acceptedVmaRecords=" << result.stats.accepted_vma_records
            << ", candidateVmaRecords=" << result.stats.candidate_vma_records
            << ", detailLinesSeen=" << result.stats.detail_lines_seen
            << ", detailKeysInserted=" << result.stats.detail_keys_inserted
            << ", vectorEntriesPushed=" << result.stats.vector_entries_pushed
            << ", bytesAllocated=" << result.stats.bytes_allocated
            << ", error=" << result.error;
    return out.str();
}

} // namespace

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "usage: " << argv[0] << " <test-name> <token>\n";
        return 2;
    }

    const std::string test_name = argv[1];
    const std::string token = argv[2];

    std::cout << "parserMode=" << kParserMode
            << " testName=" << test_name
            << " token=" << token
            << " pid=" << getpid()
            << "\n";

    const int caller_tid = get_thread_id();
    smaps_parser::ParseLimits limits;
    limits.max_accepted_vma_records = kMaxAcceptedVmaRecordsBeforeAbort;
    limits.max_bytes_allocated = kMaxBytesAllocatedBeforeAbort;

    smaps_parser::ParseResult result;
    bool have_result = false;
    std::thread worker([&] {
        result = simple_smaps_parser::run(caller_tid, limits);
        have_result = true;
    });
    worker.join();

    const std::string message = describe_result(result);
    std::cout << message << "\n";

    if (!have_result) {
        std::cerr << "parser worker did not produce a result\n";
        return 3;
    }
    if (result.error[0] != '\0') {
        std::cerr << result.error << "\n";
        return 4;
    }
    if (!result.completed) {
        std::cerr << "parser did not complete\n";
        return 5;
    }
    if (result.caller_tid == result.worker_tid) {
        std::cerr << "parser did not run on the worker thread\n";
        return 7;
    }
    if (result.stats.accepted_vma_records == 0
            || result.stats.detail_keys_inserted == 0
            || result.stats.bytes_allocated == 0) {
        std::cerr << "parser did not exercise the expected smaps workload\n";
        return 8;
    }
    if (result.stats.candidate_vma_records < result.stats.accepted_vma_records) {
        std::cerr << "candidate VMA count is smaller than accepted VMA count\n";
        return 9;
    }
    if (result.stats.detail_keys_inserted != result.stats.detail_lines_seen) {
        std::cerr << "simple parser detail count does not match detail lines seen\n";
        return 10;
    }
    if (result.stats.vector_entries_pushed != 0) {
        std::cerr << "simple parser unexpectedly used vector entries\n";
        return 11;
    }

    return 0;
}
