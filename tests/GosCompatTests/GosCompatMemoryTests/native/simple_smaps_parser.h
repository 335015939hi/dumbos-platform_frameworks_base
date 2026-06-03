#ifndef GOSCOMPAT_SIMPLE_SMAPS_PARSER_H
#define GOSCOMPAT_SIMPLE_SMAPS_PARSER_H

#include "smaps_parser_common.h"

namespace simple_smaps_parser {

smaps_parser::ParseResult run(int caller_tid, const smaps_parser::ParseLimits& limits);

} // namespace simple_smaps_parser

#endif // GOSCOMPAT_SIMPLE_SMAPS_PARSER_H
