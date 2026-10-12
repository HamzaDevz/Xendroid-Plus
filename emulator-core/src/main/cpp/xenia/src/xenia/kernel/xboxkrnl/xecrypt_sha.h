/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2026 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */

#ifndef XENIA_KERNEL_XBOXKRNL_XECRYPT_SHA_H_
#define XENIA_KERNEL_XBOXKRNL_XECRYPT_SHA_H_

#include <algorithm>
#include <cstdint>
#include <cstring>

#include "xenia/base/assert.h"
#include "xenia/base/byte_order.h"

#include "third_party/mbedtls/include/mbedtls/sha1.h"

namespace xe {
namespace kernel {
namespace xboxkrnl {

struct XECRYPT_SHA_STATE {
  xe::be<uint32_t> count;     // 0x0
  xe::be<uint32_t> state[5];  // 0x4
  uint8_t buffer[64];         // 0x18
};
static_assert_size(XECRYPT_SHA_STATE, 0x58);

inline mbedtls_sha1_context LoadSha(const XECRYPT_SHA_STATE& state) {
  mbedtls_sha1_context context;
  mbedtls_sha1_init(&context);
  context.MBEDTLS_PRIVATE(total)[0] = state.count;
  std::ranges::copy(state.state, context.MBEDTLS_PRIVATE(state));
  std::ranges::copy(state.buffer, context.MBEDTLS_PRIVATE(buffer));
  return context;
}

inline void StoreSha(const mbedtls_sha1_context& context,
                     XECRYPT_SHA_STATE& state) {
  state.count = context.MBEDTLS_PRIVATE(total)[0];
  std::ranges::copy(context.MBEDTLS_PRIVATE(state), state.state);
  std::ranges::copy(context.MBEDTLS_PRIVATE(buffer), state.buffer);
}

inline void XeCryptShaInit(XECRYPT_SHA_STATE* state) {
  mbedtls_sha1_context context;
  mbedtls_sha1_init(&context);
  mbedtls_sha1_starts(&context);
  StoreSha(context, *state);
}

inline void XeCryptShaUpdate(XECRYPT_SHA_STATE* state, const uint8_t* input,
                             uint32_t input_size) {
  auto context = LoadSha(*state);
  mbedtls_sha1_update(&context, input, input_size);
  StoreSha(context, *state);
}

// Pads like the console, in place with a 32-bit bit count and the count kept.
// The last block it compressed stays in the context buffer, not the message
// tail. Only shows up when a title finalizes the same context twice and the
// padding needed a second block, where the tail no longer survives the first
// call.
inline void XeCryptShaFinal(XECRYPT_SHA_STATE* state, uint8_t* out,
                            uint32_t out_size) {
  const uint32_t count = state->count;
  const uint32_t used = count % 64;
  const uint32_t length_offset = (used < 56 ? 56 : 120) - used;
  uint8_t padding[72] = {0x80};
  const xe::be<uint32_t> bit_count = count << 3;
  std::memcpy(padding + length_offset + 4, &bit_count, sizeof(bit_count));

  auto context = LoadSha(*state);
  mbedtls_sha1_update(&context, padding, length_offset);
  // On its own the length completes the block in the buffer instead of being
  // compressed straight from the padding.
  mbedtls_sha1_update(&context, padding + length_offset, 8);
  StoreSha(context, *state);
  state->count = count;

  if (out_size) {
    std::memcpy(out, state->state, std::min<uint32_t>(out_size, 0x14));
  }
}

inline void XeCryptSha(const uint8_t* input_1, uint32_t input_1_size,
                       const uint8_t* input_2, uint32_t input_2_size,
                       const uint8_t* input_3, uint32_t input_3_size,
                       uint8_t* out, uint32_t out_size) {
  XECRYPT_SHA_STATE state;
  XeCryptShaInit(&state);
  if (input_1 && input_1_size) {
    XeCryptShaUpdate(&state, input_1, input_1_size);
  }
  if (input_2 && input_2_size) {
    XeCryptShaUpdate(&state, input_2, input_2_size);
  }
  if (input_3 && input_3_size) {
    XeCryptShaUpdate(&state, input_3, input_3_size);
  }
  XeCryptShaFinal(&state, out, out_size);
}

}  // namespace xboxkrnl
}  // namespace kernel
}  // namespace xe

#endif  // XENIA_KERNEL_XBOXKRNL_XECRYPT_SHA_H_
