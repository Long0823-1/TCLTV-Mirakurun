'use strict';
// ARMv7/musl fallback: @node-rs/crc32 1.4.3 only ships glibc ARM binaries.
function make(polynomial) {
  const table = new Uint32Array(256);
  for (let n=0;n<256;n++) {
    let c=n;
    for (let k=0;k<8;k++) c=(c&1)?polynomial^(c>>>1):c>>>1;
    table[n]=c>>>0;
  }
  return (input, initialState=0) => {
    const bytes = typeof input === 'string' ? Buffer.from(input) : input;
    let crc=(initialState == null ? 0 : initialState)^0xffffffff;
    for (const byte of bytes) crc=table[(crc^byte)&255]^(crc>>>8);
    return (crc^0xffffffff)>>>0;
  };
}
exports.crc32=make(0xedb88320);
exports.crc32c=make(0x82f63b78);
