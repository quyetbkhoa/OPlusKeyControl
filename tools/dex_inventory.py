"""Read-only, dependency-free DEX member inventory. Never redistributes OEM code.

Usage: python tools/dex_inventory.py private-evidence/vector.dex XposedInterface
       python tools/dex_inventory.py private-evidence/oplus-services.jar PhoneWindow
Only emits matching class/method signatures, not all installed-device data.
"""
import struct
import sys
import zipfile
from pathlib import Path


class Dex:
    def __init__(self, data):
        self.data = data
        assert data[:4] == b"dex\n"
        count, offset = self.u4(56), self.u4(60)
        self.strings = [self.string(self.u4(offset + i * 4)) for i in range(count)]
        count, offset = self.u4(64), self.u4(68)
        self.types = [self.strings[self.u4(offset + i * 4)] for i in range(count)]
        self.protos = []
        count, offset = self.u4(72), self.u4(76)
        for i in range(count):
            _, result, params = struct.unpack_from("<III", data, offset + i * 12)
            args = [] if not params else [self.types[self.u2(params + 4 + j * 2)] for j in range(self.u4(params))]
            self.protos.append("(" + "".join(args) + ")" + self.types[result])

    def u4(self, off): return struct.unpack_from("<I", self.data, off)[0]
    def u2(self, off): return struct.unpack_from("<H", self.data, off)[0]

    def string(self, off):
        while self.data[off] & 128: off += 1
        off += 1
        return self.data[off:self.data.index(b"\0", off)].decode("utf-8", errors="replace")

    def matches(self, term):
        count, offset = self.u4(88), self.u4(92)
        for i in range(count):
            cls, proto, name = struct.unpack_from("<HHI", self.data, offset + i * 8)
            label = self.types[cls] + "->" + self.strings[name] + self.protos[proto]
            if term.lower() in label.lower(): yield label


if __name__ == "__main__":
    path, term = Path(sys.argv[1]), sys.argv[2]
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            blobs = [(n, archive.read(n)) for n in archive.namelist() if n.endswith(".dex")]
    else:
        blobs = [(path.name, path.read_bytes())]
    for name, blob in blobs:
        for match in Dex(blob).matches(term): print(name, match)
