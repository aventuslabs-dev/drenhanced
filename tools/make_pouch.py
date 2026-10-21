#!/usr/bin/env python3
"""16x16 pixel-art gold gem pouch (replaces DR's ink-sac pouch icon). Run from repo root."""
from PIL import Image
ART = [
    "....OO.OO.OO....",
    "....OLOWLOGO....",
    ".....OLGGDO.....",
    ".....OSssSO.....",
    "......OSSO......",
    ".....OLGGDO.....",
    "....OLWGGGDO....",
    "...OLWGGGGGDO...",
    "..OLWGGGEGGGDO..",
    "..OLGGGEeEGGDO..",
    ".OLGGGEeeeEGGDO.",
    ".OLGGGGEeEGGGDO.",
    ".OGGGGGGEGGGDDO.",
    ".ODGGGGGGGGDDDO.",
    "..ODDDDDDDDDDO..",
    "...OOOOOOOOOO...",
]
PAL = {
    "O": (59, 38, 6, 255), "D": (163, 108, 14, 255), "G": (228, 168, 32, 255), "L": (255, 214, 92, 255),
    "W": (255, 246, 190, 255), "S": (110, 58, 18, 255), "s": (168, 98, 42, 255),
    "E": (60, 235, 140, 255), "e": (16, 140, 72, 255), ".": (0, 0, 0, 0),
}
img = Image.new("RGBA", (16, 16))
for y, row in enumerate(ART):
    assert len(row) == 16, (y, len(row))
    for x, ch in enumerate(row):
        img.putpixel((x, y), PAL[ch])
img.save("src/main/resources/assets/dr_improvement_mod/textures/item/gem_pouch.png")
img.resize((256, 256), Image.NEAREST).save("/tmp/claude-0/-home-claude/47aaea1b-310f-5e78-8e1e-a36d8bca24c1/scratchpad/pouch.png")
