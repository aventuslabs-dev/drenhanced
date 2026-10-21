#!/usr/bin/env python3
""""DR Bright Ores" resource pack: high-contrast ore textures, drawn from scratch (no Mojang assets).

Each ore gets big saturated chunks with a dark outline and a highlight, on a darker base than normal stone,
plus a thin glowing frame in the ore's colour so veins stand out from across a cave.
Usage: python3 tools/make_ore_pack.py OUT.zip [preview.png]
"""
import io
import random
import sys
import zipfile

from PIL import Image

BASES = {  # base noise colours
    "stone": [(84, 84, 88), (96, 96, 100), (74, 74, 78), (104, 104, 108)],
    "deepslate": [(52, 52, 60), (60, 60, 68), (44, 44, 52), (68, 68, 76)],
    "nether": [(98, 34, 34), (112, 40, 40), (84, 28, 28), (122, 46, 44)],
}
# ore: (main, highlight, shade, outline)
ORES = {
    "coal": ((24, 24, 26), (150, 150, 160), (8, 8, 10), (210, 210, 220)),
    "iron": ((236, 178, 132), (255, 228, 200), (176, 116, 76), (40, 20, 10)),
    "copper": ((232, 120, 58), (120, 236, 200), (160, 72, 30), (40, 16, 6)),
    "gold": ((255, 214, 0), (255, 250, 170), (200, 140, 0), (50, 30, 0)),
    "redstone": ((255, 32, 32), (255, 170, 170), (170, 0, 0), (40, 0, 0)),
    "emerald": ((28, 255, 110), (190, 255, 215), (0, 160, 60), (0, 40, 14)),
    "lapis": ((48, 96, 255), (170, 200, 255), (20, 46, 180), (4, 10, 50)),
    "diamond": ((80, 245, 255), (220, 255, 255), (20, 170, 190), (0, 40, 50)),
    "quartz": ((246, 240, 230), (255, 255, 255), (190, 176, 160), (50, 30, 26)),
}
BLOCKS = [  # texture name, base, ore
    ("coal_ore", "stone", "coal"), ("deepslate_coal_ore", "deepslate", "coal"),
    ("iron_ore", "stone", "iron"), ("deepslate_iron_ore", "deepslate", "iron"),
    ("copper_ore", "stone", "copper"), ("deepslate_copper_ore", "deepslate", "copper"),
    ("gold_ore", "stone", "gold"), ("deepslate_gold_ore", "deepslate", "gold"),
    ("redstone_ore", "stone", "redstone"), ("deepslate_redstone_ore", "deepslate", "redstone"),
    ("emerald_ore", "stone", "emerald"), ("deepslate_emerald_ore", "deepslate", "emerald"),
    ("lapis_ore", "stone", "lapis"), ("deepslate_lapis_ore", "deepslate", "lapis"),
    ("diamond_ore", "stone", "diamond"), ("deepslate_diamond_ore", "deepslate", "diamond"),
    ("nether_gold_ore", "nether", "gold"), ("nether_quartz_ore", "nether", "quartz"),
]
CHUNK = [".XX.", "XXXX", "XXXX", ".XX."]
SPOTS = [(1, 1), (9, 2), (2, 9), (10, 10), (6, 6)]


def texture(base, ore, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    pal = BASES[base]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), pal[rnd.randrange(len(pal))] + (255,))
    main, hi, shade, outline = ORES[ore]
    cells = set()
    for sx, sy in SPOTS:
        for dy, row in enumerate(CHUNK):
            for dx, ch in enumerate(row):
                if ch == "X":
                    cells.add((sx + dx, sy + dy))
    # outline ring around every chunk
    for (x, y) in list(cells):
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            p = (x + ox, y + oy)
            if p not in cells and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                img.putpixel(p, outline + (255,))
    for (x, y) in cells:
        c = main
        if (x - 1, y) not in cells and (x, y - 1) not in cells:
            c = hi                      # lit top-left edge
        elif (x + 1, y) not in cells or (x, y + 1) not in cells:
            c = shade                   # shaded bottom-right edge
        img.putpixel((x, y), c + (255,))
    # glowing frame in the ore colour
    frame = tuple(int(m * 0.85 + 255 * 0.15) for m in (hi if ore == "coal" else main))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, frame + (255,))
    return img


def build(out_zip, preview=None):
    z = zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED)
    z.writestr("pack.mcmeta", '{"pack":{"pack_format":46,"description":"\\u00a76DR Bright Ores\\u00a7r - ores you can spot from across the cave"}}\n')
    sheet = Image.new("RGBA", (16 * 6 * 4, 16 * 3 * 4), (20, 20, 20, 255))
    for i, (name, base, ore) in enumerate(BLOCKS):
        img = texture(base, ore, seed=i * 31 + 7)
        buf = io.BytesIO()
        img.save(buf, "PNG")
        z.writestr("assets/minecraft/textures/block/%s.png" % name, buf.getvalue())
        sheet.paste(img.resize((64, 64), Image.NEAREST), ((i % 6) * 64, (i // 6) * 64))
    icon = io.BytesIO()
    texture("stone", "diamond", 1).resize((64, 64), Image.NEAREST).save(icon, "PNG")
    z.writestr("pack.png", icon.getvalue())
    z.close()
    if preview:
        sheet.save(preview)


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
