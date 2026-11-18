"""Reads and writes the zip files that Sorta add-ons come in.

Reading starts from the end record and the central directory, so the rules
in check.py run before any file is unpacked. Writing builds an archive from
a list of entries. pack.py uses it with fixed values, and zipfix.py with the
odd values its test cases need. Standard library only.
"""
import struct
import zlib
from dataclasses import dataclass

END = b"PK\x05\x06"
CENTRAL = b"PK\x01\x02"
LOCAL = b"PK\x03\x04"
ZIP64_LOCATOR = b"PK\x06\x07"
END_SIZE = 22
CENTRAL_SIZE = 46
LOCAL_SIZE = 30
DOS_DATE_1980 = (0 << 9) | (1 << 5) | 1  # 1 Jan 1980, the earliest a zip can say
STORE, DEFLATE = 0, 8


class Unreadable(Exception):
    """The bytes aren't a zip this kit can read."""


@dataclass
class End:
    count: int
    directory_at: int
    directory_size: int
    end_at: int


@dataclass
class Entry:
    raw_name: bytes
    flags: int
    method: int
    crc: int
    packed: int
    size: int
    external: int
    local_at: int

    @property
    def name(self) -> str:
        return self.raw_name.decode("latin-1")


def end_record(data: bytes) -> End:
    """The end of central directory record, read from the archive's tail."""
    if len(data) < END_SIZE:
        raise Unreadable("too short to be a zip")
    lowest = max(0, len(data) - END_SIZE - 0xFFFF)
    at = len(data) - END_SIZE
    while at >= lowest:
        if data[at:at + 4] == END:
            (_, disk, directory_disk, here, count, size, offset,
             comment) = struct.unpack_from("<IHHHHIIH", data, at)
            if at + END_SIZE + comment == len(data):
                if disk or directory_disk or here != count:
                    raise Unreadable("an archive split over several disks")
                if 0xFFFF in (here, count) or 0xFFFFFFFF in (size, offset) or \
                        (at >= 20 and data[at - 20:at - 16] == ZIP64_LOCATOR):
                    raise Unreadable("a ZIP64 archive")
                if offset + size != at:
                    raise Unreadable("the central directory doesn't end where the end record starts")
                return End(count, offset, size, at)
        at -= 1
    raise Unreadable("no end of central directory record")


def central_directory(data: bytes, end: End) -> list[Entry]:
    """Every entry the central directory lists, in its order."""
    entries, at, stop = [], end.directory_at, end.directory_at + end.directory_size
    for _ in range(end.count):
        if at + CENTRAL_SIZE > stop or data[at:at + 4] != CENTRAL:
            raise Unreadable("a central directory entry is missing or damaged")
        (_, _, _, flags, method, _, _, crc, packed, size, name_len, extra_len, comment_len,
         _, _, external, local_at) = struct.unpack_from("<IHHHHHHIIIHHHHHII", data, at)
        name_at = at + CENTRAL_SIZE
        at = name_at + name_len + extra_len + comment_len
        if at > stop:
            raise Unreadable("a central directory entry runs past the directory")
        entries.append(Entry(data[name_at:name_at + name_len], flags, method, crc, packed, size,
                             external, local_at))
    if at != stop:
        raise Unreadable("the central directory holds more than its entries")
    return entries


def unpack(data: bytes, end: End, entries: list[Entry]) -> list[bytes]:
    """Each entry's contents, checked against its local header and its CRC.
    Entries whose stored bytes overlap are refused: that is how some zip
    bombs reuse one compressed stream many times."""
    spans, out = [], []
    for entry in entries:
        at = entry.local_at
        if at + LOCAL_SIZE > end.directory_at or data[at:at + 4] != LOCAL:
            raise Unreadable(f"{entry.name!r}: no local header where the directory points")
        (_, _, _, method, _, _, _, _, _, name_len, extra_len) = struct.unpack_from("<IHHHHHIIIHH", data, at)
        start = at + LOCAL_SIZE + name_len + extra_len
        stop = start + entry.packed
        if data[at + LOCAL_SIZE:at + LOCAL_SIZE + name_len] != entry.raw_name or method != entry.method:
            raise Unreadable(f"{entry.name!r}: the local header doesn't match the directory")
        if stop > end.directory_at:
            raise Unreadable(f"{entry.name!r}: its data runs into the central directory")
        spans.append((at, stop, entry.name))
        packed = data[start:stop]
        if entry.method == STORE:
            if entry.packed != entry.size:
                raise Unreadable(f"{entry.name!r}: stored, but its two sizes differ")
            content = packed
        elif entry.method == DEFLATE:
            inflater = zlib.decompressobj(-15)
            try:
                content = inflater.decompress(packed, entry.size + 1)
            except zlib.error as error:
                raise Unreadable(f"{entry.name!r}: {error}") from None
            if len(content) != entry.size or not inflater.eof:
                raise Unreadable(f"{entry.name!r}: unpacks to a different size than the directory says")
        else:
            raise Unreadable(f"{entry.name!r}: compression method {entry.method}")
        if zlib.crc32(content) != entry.crc:
            raise Unreadable(f"{entry.name!r}: the CRC doesn't match")
        out.append(content)
    spans.sort()
    for (_, first_stop, first), (second_start, _, second) in zip(spans, spans[1:]):
        if second_start < first_stop:
            raise Unreadable(f"{first!r} and {second!r} share bytes")
    return out


@dataclass
class Item:
    """One entry to write. `same_as` makes a central directory entry that
    points at an earlier item's local header and data, and `bad_crc` writes
    a CRC one bit off."""
    raw_name: bytes
    data: bytes = b""
    method: int = STORE
    flags: int = 0
    external: int = 0
    made_by: int = 20
    same_as: int | None = None
    bad_crc: bool = False


def build(items: list[Item]) -> bytes:
    """The archive's bytes: local headers and data in the given order, then
    the central directory and the end record. Times are 1 Jan 1980, and no
    extra fields or comments are written."""
    body, directory, written = bytearray(), bytearray(), []
    for item in items:
        if item.same_as is not None:
            crc, packed_len, size, local_at, method = written[item.same_as]
        else:
            if item.method == DEFLATE:
                deflater = zlib.compressobj(9, zlib.DEFLATED, -15)
                packed = deflater.compress(item.data) + deflater.flush()
            else:
                packed = item.data
            crc, packed_len, size, local_at, method = (zlib.crc32(item.data) ^ item.bad_crc, len(packed),
                                                       len(item.data), len(body), item.method)
            needed = 20 if item.method == DEFLATE else 10
            body += struct.pack("<IHHHHHIIIHH", 0x04034B50, needed, item.flags, method, 0, DOS_DATE_1980,
                                crc, packed_len, size, len(item.raw_name), 0)
            body += item.raw_name + packed
        written.append((crc, packed_len, size, local_at, method))
        needed = 20 if method == DEFLATE else 10
        directory += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, item.made_by, needed, item.flags, method,
                                 0, DOS_DATE_1980, crc, packed_len, size, len(item.raw_name), 0, 0, 0, 0,
                                 item.external, local_at)
        directory += item.raw_name
    end = struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(items), len(items), len(directory), len(body), 0)
    return bytes(body + directory + end)
