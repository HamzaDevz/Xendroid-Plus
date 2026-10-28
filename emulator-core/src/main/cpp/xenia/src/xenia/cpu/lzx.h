/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2013 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_CPU_LZX_H_
#define XENIA_CPU_LZX_H_

#include <string>
#include <vector>

#include "xenia/cpu/module.h"

namespace xe {
struct xex2_delta_patch;
}  // namespace xe

int lzx_decompress(const void* lzx_data, size_t lzx_len, void* dest,
                   size_t dest_len, uint32_t window_size, void* window_data,
                   size_t window_data_len);

// Applies the delta patch records in the first patch_len bytes at patch to the
// dest_len bytes at dest, or none of them if any reaches outside either.
int lzxdelta_apply_patch(xe::xex2_delta_patch* patch, size_t patch_len,
                         uint32_t window_size, void* dest, size_t dest_len);

#endif  // XENIA_CPU_LZX_H_
