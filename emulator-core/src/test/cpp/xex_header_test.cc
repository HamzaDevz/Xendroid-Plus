// The XEX header bounds (xenia/kernel/util/xex2_util.h, XexHeaderProblem): the
// loader and the library scan read the optional headers and the security info by
// the counts and offsets in the file, so a header that sends any of them outside
// itself is refused before they are read.

#include <cstdint>
#include <cstdio>
#include <vector>

#include "xenia/kernel/util/xex2_util.h"

namespace {

int failures = 0;

void expect(bool ok, const char* what) {
  if (!ok) {
    std::fprintf(stderr, "FAILED: %s\n", what);
    ++failures;
  }
}

void put32(std::vector<uint8_t>& file, size_t at, uint32_t value) {
  file[at] = uint8_t(value >> 24);
  file[at + 1] = uint8_t(value >> 16);
  file[at + 2] = uint8_t(value >> 8);
  file[at + 3] = uint8_t(value);
}

constexpr uint32_t kHeaderSize = 0x300;
constexpr uint32_t kSecurity = 0x80;
constexpr uint32_t kPageCount = 0x180;  // xex2_security_info::page_descriptor_count

// A XEX2 whose header holds an entry point (in the entry), the execution info
// (6 dwords at 0x40), the file format info (0x20 bytes, sized, at 0x60) and the
// security info at 0x80 with two page descriptors; the image follows the header.
std::vector<uint8_t> GoodXex() {
  std::vector<uint8_t> file(0x400);
  put32(file, 0x00, 0x58455832);  // 'XEX2'
  put32(file, 0x08, kHeaderSize);
  put32(file, 0x10, kSecurity);
  put32(file, 0x14, 3);
  put32(file, 0x18, 0x00010100);  // XEX_HEADER_ENTRY_POINT, value in the entry
  put32(file, 0x1C, 0x82000000);
  put32(file, 0x20, 0x00040006);  // XEX_HEADER_EXECUTION_INFO, 6 dwords
  put32(file, 0x24, 0x40);
  put32(file, 0x28, 0x000003FF);  // XEX_HEADER_FILE_FORMAT_INFO, sized
  put32(file, 0x2C, 0x60);
  put32(file, 0x60, 0x20);
  put32(file, kSecurity + kPageCount, 2);
  return file;
}

const char* Problem(const std::vector<uint8_t>& file, size_t length = 0) {
  return xe::XexHeaderProblem(file.data(), length ? length : file.size());
}

}  // namespace

int main() {
  static_assert(offsetof(xe::xex2_security_info, page_descriptors) == kPageCount + 4);

  auto file = GoodXex();
  expect(!Problem(file), "a well-formed header passes");
  xe::xex2_opt_execution_info* exec = nullptr;
  expect(xe::GetXexOptHeader(reinterpret_cast<const xe::xex2_header*>(file.data()),
                             xe::XEX_HEADER_EXECUTION_INFO, &exec) &&
             reinterpret_cast<uint8_t*>(exec) == file.data() + 0x40,
         "its execution info is found where the entry points");
  expect(!Problem(file, kHeaderSize), "the header alone is enough");
  expect(Problem(file, kHeaderSize - 1), "a file cut inside the header is refused");
  expect(Problem(file, 0x10), "a file shorter than the fixed fields is refused");

  file = GoodXex();
  put32(file, 0x08, 0x10);
  expect(Problem(file), "a header size below the fixed fields is refused");

  file = GoodXex();
  put32(file, 0x14, (kHeaderSize - 0x18) / 8 + 1);
  expect(Problem(file), "an optional header list longer than the header is refused");
  put32(file, 0x14, 0x20000000);
  expect(Problem(file), "a count that wraps 32 bits is refused");

  file = GoodXex();
  put32(file, 0x24, kHeaderSize - 0x14);
  expect(Problem(file), "fixed-size data that runs past the header is refused");
  put32(file, 0x24, 0xFFFFFFF0);
  expect(Problem(file), "an offset that wraps 32 bits is refused");
  put32(file, 0x24, kHeaderSize - 0x18);
  expect(!Problem(file), "fixed-size data that ends with the header passes");

  file = GoodXex();
  put32(file, 0x60, kHeaderSize);
  expect(Problem(file), "a sized block that runs past the header is refused");
  put32(file, 0x60, 2);
  expect(Problem(file), "a sized block shorter than its size field is refused");
  put32(file, 0x60, 0x20);
  put32(file, 0x2C, kHeaderSize - 2);
  expect(Problem(file), "a size field that runs past the header is refused");

  file = GoodXex();
  put32(file, 0x10, kHeaderSize);
  expect(Problem(file), "security info past the header is refused");
  put32(file, 0x10, 0xFFFFFF00);
  expect(Problem(file), "a security offset that wraps 32 bits is refused");

  file = GoodXex();
  put32(file, kSecurity + kPageCount, 0x100000);
  expect(Problem(file), "page descriptors past the header are refused");
  put32(file, kSecurity + kPageCount,
        (kHeaderSize - kSecurity - kPageCount - 4) / 0x18);
  expect(!Problem(file), "page descriptors that end inside the header pass");

  // XEX1 keeps its page descriptors elsewhere in the security info.
  file = GoodXex();
  put32(file, 0x00, 0x58455831);  // 'XEX1'
  const size_t xex1_count = offsetof(xe::xex1_security_info, page_descriptors) - 4;
  put32(file, kSecurity + xex1_count, 2);
  expect(!Problem(file), "a well-formed XEX1 header passes");
  put32(file, kSecurity + xex1_count, 0x100000);
  expect(Problem(file), "XEX1 page descriptors past the header are refused");

  if (failures) {
    std::fprintf(stderr, "xex_header_test: %d failed\n", failures);
    return 1;
  }
  std::printf("xex_header_test: ok\n");
  return 0;
}
