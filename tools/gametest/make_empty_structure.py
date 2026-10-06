"""Writes the 3x3x3 air structure used as the GameTest template (data/nostalgiaskills/structure/empty.nbt)."""
import gzip
import os
import struct

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(REPO, "src", "main", "resources", "data", "nostalgiaskills", "structure", "empty.nbt")
DATA_VERSION = 3955  # Minecraft 1.21.1


def s(name):
    b = name.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_int(name, v):
    return b"\x03" + s(name) + struct.pack(">i", v)


def tag_string(name, v):
    return b"\x08" + s(name) + s(v)


def list_of_ints(name, vals):
    return b"\x09" + s(name) + b"\x03" + struct.pack(">i", len(vals)) + b"".join(struct.pack(">i", v) for v in vals)


def list_of_compounds(name, payloads):
    return b"\x09" + s(name) + b"\x0a" + struct.pack(">i", len(payloads)) + b"".join(p + b"\x00" for p in payloads)


def empty_list(name):
    return b"\x09" + s(name) + b"\x00" + struct.pack(">i", 0)


blocks = []
for x in range(3):
    for y in range(3):
        for z in range(3):
            blocks.append(list_of_ints("pos", [x, y, z]) + tag_int("state", 0))
root = (b"\x0a" + s("") + tag_int("DataVersion", DATA_VERSION) + list_of_ints("size", [3, 3, 3])
        + list_of_compounds("palette", [tag_string("Name", "minecraft:air")])
        + list_of_compounds("blocks", blocks) + empty_list("entities") + b"\x00")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with gzip.GzipFile(OUT, "wb", mtime=0) as f:
    f.write(root)
print(OUT)
