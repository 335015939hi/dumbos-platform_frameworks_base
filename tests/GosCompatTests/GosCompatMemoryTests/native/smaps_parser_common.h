#ifndef GOSCOMPAT_SMAPS_PARSER_COMMON_H
#define GOSCOMPAT_SMAPS_PARSER_COMMON_H

#include <cstddef>
#include <cstdint>

namespace smaps_parser {

struct ParseStats {
    std::uint64_t lines_read = 0;
    std::uint64_t candidate_vma_records = 0;
    std::uint64_t accepted_vma_records = 0;
    std::uint64_t detail_lines_seen = 0;
    std::uint64_t detail_keys_inserted = 0;
    std::uint64_t detail_values_updated = 0;
    std::uint64_t vector_entries_pushed = 0;
    std::uint64_t vector_buffer_allocations = 0;
    std::uint64_t string_allocations = 0;
    std::uint64_t other_allocations = 0;
    std::uint64_t bytes_allocated = 0;
    std::size_t max_details_per_vma = 0;
    std::size_t max_line_length = 0;
};

struct ParseLimits {
    std::uint64_t max_accepted_vma_records = 0;
    std::uint64_t max_bytes_allocated = 0;
};

struct ParseResult {
    bool completed = false;
    int pointer_size = sizeof(void*);
    int caller_tid = 0;
    int worker_tid = 0;
    ParseStats stats;
    char error[160] = {};
};

} // namespace smaps_parser

#endif // GOSCOMPAT_SMAPS_PARSER_COMMON_H
