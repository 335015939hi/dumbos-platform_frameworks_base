# GosCompatMemoryTests

`GosCompatMemoryTests` is a compatibility test for memory allocator usage in apps.

## SmapsNativeHostTest

This tests streams `/proc/self/smaps` (a live walk of the process VMA tree) while allocating 
retained parser objects, and hardened_malloc turns that allocation pattern into many allocator VMAs.
Because the live `/proc/self/smaps` walk can see those newly-created VMAs, the parser can start 
parsing mappings created by its own parsing work, leading to a loop of allocations and eventually an 
out-of-memory error. The tests will fail when a very high limit on number of VMA records parsed is 
reached.
