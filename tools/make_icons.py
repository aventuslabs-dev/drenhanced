#!/usr/bin/env python3
"""Paints the WoW-style ability icons for the cooldown bar.

Run from the repo root:  python3 tools/make_icons.py
Writes 64x64 PNGs (plus a blur .mcmeta so they scale smoothly) to
src/main/resources/assets/dr_improvement_mod/textures/gui/cd/.
Everything is drawn procedurally at 512px and downsampled, so there are no third-party assets.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

S = 512
OUT = 64
DST = os.path.join("src", "main", "resources", "assets", "dr_improvement_mod", "textures", "gui", "cd")


# ----------------------------------------------------------------------------- helpers

def rgba(hexstr, a=255):
    hexstr = hexstr.lstrip("#")
    return (int(hexstr[0:2], 16), int(hexstr[2:4], 16), int(hexstr[4:6], 16), a)


def layer():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def radial(inner, outer, cx=0.5, cy=0.45, power=1.3):
    y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / 0.75
    t = np.clip(d, 0, 1) ** power
    a = np.array(rgba(inner), np.float32)
    b = np.array(rgba(outer), np.float32)
    img = a[None, None, :] * (1 - t[..., None]) + b[None, None, :] * t[..., None]
    return Image.fromarray(img.astype(np.uint8), "RGBA")


def linear(top, bottom, angle=90):
    y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S
    r = math.radians(angle)
    t = np.clip((x - 0.5) * math.cos(r) + (y - 0.5) * math.sin(r) + 0.5, 0, 1)
    a = np.array(rgba(top), np.float32)
    b = np.array(rgba(bottom), np.float32)
    img = a[None, None, :] * (1 - t[..., None]) + b[None, None, :] * t[..., None]
    return Image.fromarray(img.astype(np.uint8), "RGBA")


def fill_mask(mask, paint):
    """paint (an RGBA image or colour) clipped to mask (L)."""
    if not isinstance(paint, Image.Image):
        paint = Image.new("RGBA", (S, S), paint)
    out = paint.copy()
    alpha = ImageChops.multiply(out.getchannel("A"), mask)
    out.putalpha(alpha)
    return out


def glow(mask, colour, radius, strength=1.0):
    m = mask.filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        m = m.point(lambda v: min(255, int(v * strength)))
    return fill_mask(m, rgba(colour))


def shadow(mask, radius=10, offset=(8, 10), opacity=0.75):
    m = Image.new("L", (S, S), 0)
    m.paste(mask, offset)
    m = m.filter(ImageFilter.GaussianBlur(radius)).point(lambda v: int(v * opacity))
    return fill_mask(m, (0, 0, 0, 255))


def poly_mask(points, mask=None, value=255):
    mask = mask or Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon([(x * S, y * S) for x, y in points], fill=value)
    return mask


def ellipse_mask(box, mask=None, value=255):
    mask = mask or Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([v * S for v in box], fill=value)
    return mask


def line_mask(points, width, mask=None, value=255):
    mask = mask or Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    pts = [(x * S, y * S) for x, y in points]
    d.line(pts, fill=value, width=int(width * S), joint="curve")
    r = width * S / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=value)
    return mask


def tapered(p0, p1, w0, w1, mask=None):
    """A stroke that narrows from w0 to w1 (fractions of S)."""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    l = math.hypot(dx, dy) or 1
    nx, ny = -dy / l, dx / l
    pts = [(x0 + nx * w0 / 2, y0 + ny * w0 / 2), (x1 + nx * w1 / 2, y1 + ny * w1 / 2),
           (x1 - nx * w1 / 2, y1 - ny * w1 / 2), (x0 - nx * w0 / 2, y0 - ny * w0 / 2)]
    return poly_mask(pts, mask)


def shade(mask, light, dark, angle=60):
    """Metal/material shading: a linear gradient clipped to the mask plus a bevel."""
    return fill_mask(mask, linear(light, dark, angle))


def bevel(mask, hi=110, lo=130, r=6):
    """Light top-left edge, dark bottom-right edge inside the mask."""
    m = np.asarray(mask, np.float32) / 255
    blur = np.asarray(mask.filter(ImageFilter.GaussianBlur(r)), np.float32) / 255
    sh = np.roll(np.roll(blur, r, 0), r, 1)
    sl = np.roll(np.roll(blur, -r, 0), -r, 1)
    light = np.clip(sh - sl, 0, 1) * m
    dark = np.clip(sl - sh, 0, 1) * m
    out = layer()
    out.alpha_composite(fill_mask(Image.fromarray((light * hi * 2).clip(0, 255).astype(np.uint8)), (255, 255, 255, 255)))
    out.alpha_composite(fill_mask(Image.fromarray((dark * lo * 2).clip(0, 255).astype(np.uint8)), (0, 0, 0, 255)))
    return out


def noise(amount=14, seed=1):
    rnd = np.random.default_rng(seed)
    n = rnd.normal(0, amount, (S // 4, S // 4)).astype(np.float32)
    n = np.asarray(Image.fromarray(n).resize((S, S), Image.BICUBIC), np.float32)
    return n


def background(inner, outer, seed=1, rays=None):
    img = radial(inner, outer)
    arr = np.asarray(img, np.float32)
    arr[..., :3] += noise(10, seed)[..., None]
    img = Image.fromarray(arr.clip(0, 255).astype(np.uint8), "RGBA")
    if rays:
        r = layer()
        d = ImageDraw.Draw(r)
        cx, cy = S * 0.5, S * 0.47
        n = 16
        for i in range(n):
            a0 = (i / n) * 2 * math.pi
            a1 = a0 + math.pi / n * 0.7
            d.polygon([(cx, cy), (cx + math.cos(a0) * S, cy + math.sin(a0) * S),
                       (cx + math.cos(a1) * S, cy + math.sin(a1) * S)], fill=rgba(rays, 34))
        img.alpha_composite(r.filter(ImageFilter.GaussianBlur(6)))
    return img


def finish(img, name, frame="#1a1208"):
    """Vignette, inner bevel and a thin dark-bronze frame, then downsample and save."""
    y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S
    v = np.clip((np.maximum(abs(x - 0.5), abs(y - 0.5)) - 0.30) / 0.2, 0, 1) ** 1.6
    vig = Image.fromarray((v * 150).astype(np.uint8))
    img.alpha_composite(fill_mask(vig, (0, 0, 0, 255)))
    # glossy top highlight
    hl = ellipse_mask((-0.25, -0.62, 1.25, 0.42)).filter(ImageFilter.GaussianBlur(30)).point(lambda p: int(p * 0.16))
    img.alpha_composite(fill_mask(hl, (255, 255, 255, 255)))
    # frame: dark outer line, bronze inner line, soft inner shadow
    d = ImageDraw.Draw(img)
    b = int(S * 0.035)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(frame), width=b)
    d.rectangle([b, b, S - 1 - b, S - 1 - b], outline=rgba("#8a6a2e", 200), width=max(2, b // 3))
    inner = Image.new("L", (S, S), 0)
    ImageDraw.Draw(inner).rectangle([0, 0, S - 1, S - 1], outline=255, width=b * 3)
    img.alpha_composite(fill_mask(inner.filter(ImageFilter.GaussianBlur(b * 1.5)).point(lambda p: int(p * 0.5)), (0, 0, 0, 255)))
    small = img.resize((OUT, OUT), Image.LANCZOS)
    os.makedirs(DST, exist_ok=True)
    small.save(os.path.join(DST, name + ".png"))
    with open(os.path.join(DST, name + ".png.mcmeta"), "w") as f:
        f.write('{"texture":{"blur":true,"clamp":true}}\n')
    return small


def emblem(img, mask, light, dark, glow_col=None, glow_r=18, angle=60, bevel_r=6, shadow_on=True):
    if shadow_on:
        img.alpha_composite(shadow(mask))
    if glow_col:
        img.alpha_composite(glow(mask, glow_col, glow_r, 1.6))
    img.alpha_composite(shade(mask, light, dark, angle))
    img.alpha_composite(bevel(mask, r=bevel_r))


def flame_mask(cx, base, w, h, seed=3, tongues=5, mask=None):
    """Licking flame: a stack of teardrops with random tips."""
    rnd = random.Random(seed)
    mask = mask or Image.new("L", (S, S), 0)
    for i in range(tongues):
        t = (i - (tongues - 1) / 2) / max(1, (tongues - 1) / 2)
        x = cx + t * w * 0.38
        hh = h * (1 - 0.45 * abs(t)) * rnd.uniform(0.85, 1.05)
        ww = w * 0.36 * (1 - 0.3 * abs(t))
        tipx = x + rnd.uniform(-0.05, 0.05) + t * 0.05
        pts = []
        for k in range(0, 41):
            a = math.pi * k / 40
            pts.append((x - math.cos(a) * ww / 2, base - math.sin(a) * ww * 0.55))
        pts = [(x + ww / 2, base)] + pts[::-1]
        pts = [(x - ww / 2, base), (x - ww * 0.48, base - hh * 0.35), (tipx - ww * 0.12, base - hh * 0.8),
               (tipx, base - hh), (tipx + ww * 0.05, base - hh * 0.78), (x + ww * 0.48, base - hh * 0.3),
               (x + ww / 2, base)]
        poly_mask(pts, mask)
        ellipse_mask((x - ww / 2, base - ww * 0.5, x + ww / 2, base + ww * 0.3), mask)
    return mask.filter(ImageFilter.GaussianBlur(3)).point(lambda p: 255 if p > 110 else 0)


def bezier(p0, p1, p2, n=24):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


def heater_shield(top, bottom, half, shoulder):
    """Heater shield outline: flat-ish top, straight sides, curving to a point at the bottom."""
    right = [(0.5, top), (0.5 + half, top + 0.06), (0.5 + half, shoulder)]
    right += bezier((0.5 + half, shoulder), (0.5 + half * 0.95, bottom - 0.08), (0.5, bottom))[1:]
    left = [(1 - x, y) for x, y in reversed(right[1:-1])]
    return right + left


# ----------------------------------------------------------------------------- icons

def dash():
    img = background("#2f8fd8", "#06142c", seed=2)
    # speed streaks
    streaks = Image.new("L", (S, S), 0)
    for y, x0, x1, w in ((0.30, 0.08, 0.46, 0.035), (0.44, 0.04, 0.42, 0.05), (0.58, 0.08, 0.44, 0.04), (0.72, 0.16, 0.48, 0.028)):
        tapered((x0, y), (x1, y), 0.002, w, streaks)
    img.alpha_composite(glow(streaks, "#9fe3ff", 10, 1.4))
    img.alpha_composite(fill_mask(streaks, linear("#ffffff00", "#e8fbff", 0)))
    # bold double chevron
    m = Image.new("L", (S, S), 0)
    for off in (0.0, 0.2):
        poly_mask([(0.38 + off, 0.22), (0.54 + off, 0.22), (0.78 + off, 0.5), (0.54 + off, 0.78),
                   (0.38 + off, 0.78), (0.62 + off, 0.5)], m)
    emblem(img, m, "#ffffff", "#5cc8ff", glow_col="#4fd2ff", glow_r=22, angle=70)
    return finish(img, "dash")


def the_fast():
    img = background("#4fb83a", "#0a2008", seed=3, rays="#d8ff9a")
    # swirl of wind
    wind = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(wind)
    for i, (r, w) in enumerate(((0.36, 14), (0.29, 10), (0.22, 7))):
        d.arc([(0.5 - r) * S, (0.5 - r) * S, (0.5 + r) * S, (0.5 + r) * S], 200 + i * 25, 340 + i * 25, fill=255, width=w)
        d.arc([(0.5 - r) * S, (0.5 - r) * S, (0.5 + r) * S, (0.5 + r) * S], 20 + i * 25, 160 + i * 25, fill=255, width=w)
    img.alpha_composite(glow(wind, "#e9ffb0", 8, 1.3))
    img.alpha_composite(fill_mask(wind, (240, 255, 210, 200)))
    # lightning bolt
    bolt = poly_mask([(0.58, 0.10), (0.30, 0.54), (0.48, 0.54), (0.38, 0.90), (0.72, 0.42), (0.53, 0.42), (0.66, 0.10)])
    emblem(img, bolt, "#fffbd0", "#f2b81c", glow_col="#fff27a", glow_r=24, angle=75)
    return finish(img, "the_fast")


def berserk():
    img = background("#d6331a", "#260403", seed=4)
    fire = flame_mask(0.5, 1.02, 1.05, 0.98, seed=7, tongues=7)
    img.alpha_composite(glow(fire, "#ff5a10", 26, 1.5))
    img.alpha_composite(fill_mask(fire, linear("#ffe27a", "#c8200a", 90)))
    # horns: thick tapered crescents sweeping up and out
    horns = Image.new("L", (S, S), 0)
    for flip in (1, -1):
        outer = bezier((0.5 + flip * 0.16, 0.42), (0.5 + flip * 0.46, 0.44), (0.5 + flip * 0.40, 0.08), 30)
        inner = bezier((0.5 + flip * 0.40, 0.08), (0.5 + flip * 0.34, 0.34), (0.5 + flip * 0.15, 0.30), 30)
        poly_mask(outer + inner[1:], horns)
    emblem(img, horns, "#fff4dc", "#8a6a44", glow_col="#ff9a40", glow_r=12, angle=60, bevel_r=5)
    # helmet: dome + cheek guards, dark iron
    helm = Image.new("L", (S, S), 0)
    ellipse_mask((0.27, 0.22, 0.73, 0.66), helm)
    poly_mask([(0.27, 0.44), (0.73, 0.44), (0.70, 0.78), (0.58, 0.90), (0.42, 0.90), (0.30, 0.78)], helm)
    emblem(img, helm, "#9aa4b0", "#1c2026", angle=55, bevel_r=7)
    # nose guard + brow ridge
    ridge = Image.new("L", (S, S), 0)
    ImageDraw.Draw(ridge).rectangle([0.47 * S, 0.24 * S, 0.53 * S, 0.80 * S], fill=255)
    ImageDraw.Draw(ridge).rectangle([0.29 * S, 0.44 * S, 0.71 * S, 0.49 * S], fill=255)
    emblem(img, ridge, "#e6c27a", "#6a4a14", angle=55, bevel_r=3, shadow_on=False)
    # glowing, angry eye slits
    eyes = Image.new("L", (S, S), 0)
    for flip in (1, -1):
        poly_mask([(0.5 + flip * 0.05, 0.53), (0.5 + flip * 0.19, 0.50), (0.5 + flip * 0.17, 0.58), (0.5 + flip * 0.06, 0.60)], eyes)
    img.alpha_composite(glow(eyes, "#ff3010", 16, 2.4))
    img.alpha_composite(fill_mask(eyes, linear("#fffbd0", "#ffb020", 90)))
    # embers
    emb = Image.new("L", (S, S), 0)
    rnd = random.Random(9)
    for _ in range(14):
        x, y, r = rnd.uniform(0.1, 0.9), rnd.uniform(0.05, 0.5), rnd.uniform(0.006, 0.014)
        if 0.25 < x < 0.75 and y > 0.2: continue
        ellipse_mask((x - r, y - r, x + r, y + r), emb)
    img.alpha_composite(glow(emb, "#ffb040", 6, 2.0))
    img.alpha_composite(fill_mask(emb, (255, 230, 160, 255)))
    return finish(img, "berserk")


def divine_protection():
    img = background("#f2d06b", "#3a2604", seed=5, rays="#fff6c0")
    halo = Image.new("L", (S, S), 0)
    ImageDraw.Draw(halo).ellipse([0.14 * S, 0.12 * S, 0.86 * S, 0.84 * S], outline=255, width=14)
    img.alpha_composite(glow(halo, "#fff8d0", 16, 1.6))
    shield = poly_mask(heater_shield(0.14, 0.88, 0.27, 0.46))
    emblem(img, shield, "#fff7d8", "#b8861c", glow_col="#fff2a0", glow_r=20, angle=70, bevel_r=8)
    inner = shield.filter(ImageFilter.MinFilter(41))
    img.alpha_composite(fill_mask(inner, linear("#3c6fd6", "#0e2560", 80)))
    img.alpha_composite(bevel(inner, r=5))
    cross = Image.new("L", (S, S), 0)
    ImageDraw.Draw(cross).rectangle([0.46 * S, 0.27 * S, 0.54 * S, 0.70 * S], fill=255)
    ImageDraw.Draw(cross).rectangle([0.34 * S, 0.38 * S, 0.66 * S, 0.46 * S], fill=255)
    emblem(img, cross, "#ffffff", "#e8c048", glow_col="#ffffff", glow_r=10, angle=70, bevel_r=4, shadow_on=False)
    return finish(img, "divine_protection")


def deaths_grasp():
    img = background("#5b2a8a", "#0a0414", seed=6)
    mist = Image.new("L", (S, S), 0)
    rnd = random.Random(5)
    for _ in range(26):
        x, y, r = rnd.uniform(0.1, 0.9), rnd.uniform(0.55, 1.0), rnd.uniform(0.06, 0.14)
        ellipse_mask((x - r, y - r * 0.6, x + r, y + r * 0.6), mist)
    img.alpha_composite(fill_mask(mist.filter(ImageFilter.GaussianBlur(22)).point(lambda p: int(p * 0.7)), rgba("#62ff9a")))
    # skeletal hand: palm + four jointed fingers + thumb, reaching up
    hand = Image.new("L", (S, S), 0)
    poly_mask([(0.36, 0.62), (0.64, 0.60), (0.62, 0.80), (0.56, 0.96), (0.44, 0.96), (0.38, 0.80)], hand)
    fingers = [((0.38, 0.60), (0.30, 0.42), (0.24, 0.24)), ((0.46, 0.58), (0.44, 0.36), (0.42, 0.14)),
               ((0.55, 0.58), (0.57, 0.36), (0.59, 0.15)), ((0.62, 0.60), (0.68, 0.43), (0.73, 0.28))]
    for a, b, c in fingers:
        line_mask([a, b], 0.055, hand)
        line_mask([b, c], 0.045, hand)
        # claw tip
        dx, dy = c[0] - b[0], c[1] - b[1]
        l = math.hypot(dx, dy)
        tip = (c[0] + dx / l * 0.07, c[1] + dy / l * 0.07)
        nx, ny = -dy / l * 0.022, dx / l * 0.022
        poly_mask([(c[0] + nx, c[1] + ny), tip, (c[0] - nx, c[1] - ny)], hand)
    line_mask([(0.62, 0.74), (0.76, 0.66), (0.84, 0.54)], 0.05, hand)
    emblem(img, hand, "#f2ffe8", "#7b8a74", glow_col="#5dff8e", glow_r=24, angle=60, bevel_r=5)
    joints = Image.new("L", (S, S), 0)
    for a, b, c in fingers:
        ellipse_mask((b[0] - 0.022, b[1] - 0.022, b[0] + 0.022, b[1] + 0.022), joints)
    img.alpha_composite(fill_mask(joints, (60, 70, 60, 160)))
    return finish(img, "deaths_grasp")


def potion():
    img = background("#7a2a3a", "#12040a", seed=7)
    flask = Image.new("L", (S, S), 0)
    ellipse_mask((0.22, 0.38, 0.78, 0.92), flask)
    poly_mask([(0.42, 0.20), (0.58, 0.20), (0.60, 0.46), (0.40, 0.46)], flask)
    img.alpha_composite(shadow(flask))
    img.alpha_composite(fill_mask(flask, (210, 230, 255, 90)))
    liquid = Image.new("L", (S, S), 0)
    ellipse_mask((0.26, 0.42, 0.74, 0.88), liquid)
    cut = Image.new("L", (S, S), 0)
    ImageDraw.Draw(cut).rectangle([0, 0, S, 0.56 * S], fill=255)
    liquid = ImageChops.subtract(liquid, cut)
    img.alpha_composite(glow(liquid, "#ff3040", 18, 1.4))
    img.alpha_composite(fill_mask(liquid, radial("#ff6a6a", "#a00818", cx=0.45, cy=0.65)))
    surf = Image.new("L", (S, S), 0)
    ellipse_mask((0.28, 0.54, 0.72, 0.60), surf)
    img.alpha_composite(fill_mask(surf, (255, 150, 150, 200)))
    img.alpha_composite(bevel(flask, r=6))
    hl = Image.new("L", (S, S), 0)
    ImageDraw.Draw(hl).arc([0.30 * S, 0.46 * S, 0.70 * S, 0.86 * S], 160, 230, fill=255, width=12)
    img.alpha_composite(fill_mask(hl.filter(ImageFilter.GaussianBlur(2)), (255, 255, 255, 220)))
    cork = Image.new("L", (S, S), 0)
    ImageDraw.Draw(cork).rounded_rectangle([0.40 * S, 0.12 * S, 0.60 * S, 0.24 * S], radius=12, fill=255)
    emblem(img, cork, "#d9a066", "#6b3f1a", angle=60, bevel_r=4)
    bubbles = Image.new("L", (S, S), 0)
    for x, y, r in ((0.45, 0.72, 0.025), (0.56, 0.66, 0.018), (0.52, 0.80, 0.015)):
        ellipse_mask((x - r, y - r, x + r, y + r), bubbles)
    img.alpha_composite(fill_mask(bubbles, (255, 200, 200, 200)))
    return finish(img, "potion")


def sword_mask(p0, p1, width, mask=None):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    l = math.hypot(dx, dy)
    ux, uy = dx / l, dy / l
    nx, ny = -uy, ux
    w = width / 2
    tip = (x1, y1)
    base = (x0 + ux * 0.0, y0 + uy * 0.0)
    shoulder = (x1 - ux * width * 1.6, y1 - uy * width * 1.6)
    return poly_mask([(base[0] + nx * w, base[1] + ny * w), (shoulder[0] + nx * w, shoulder[1] + ny * w), tip,
                      (shoulder[0] - nx * w, shoulder[1] - ny * w), (base[0] - nx * w, base[1] - ny * w)], mask)


def draw_sword(img, hilt, tip, width=0.075, blade=("#ffffff", "#8796a8"), glow_col=None):
    hx, hy = hilt
    tx, ty = tip
    dx, dy = tx - hx, ty - hy
    l = math.hypot(dx, dy)
    ux, uy, nx, ny = dx / l, dy / l, -dy / l, dx / l
    bl = sword_mask(hilt, tip, width)
    emblem(img, bl, blade[0], blade[1], glow_col=glow_col, glow_r=12, angle=45, bevel_r=4)
    fuller = line_mask([(hx + ux * 0.05, hy + uy * 0.05), (tx - ux * 0.15, ty - uy * 0.15)], 0.012)
    img.alpha_composite(fill_mask(fuller, (60, 70, 85, 150)))
    guard = tapered((hx + nx * 0.11, hy + ny * 0.11), (hx - nx * 0.11, hy - ny * 0.11), 0.045, 0.045)
    emblem(img, guard, "#ffe08a", "#8a5a10", angle=45, bevel_r=4)
    grip = tapered((hx, hy), (hx - ux * 0.13, hy - uy * 0.13), 0.04, 0.04)
    emblem(img, grip, "#8a4a26", "#2e140a", angle=45, bevel_r=3)
    pommel = ellipse_mask((hx - ux * 0.15 - 0.035, hy - uy * 0.15 - 0.035, hx - ux * 0.15 + 0.035, hy - uy * 0.15 + 0.035))
    emblem(img, pommel, "#ffe08a", "#8a5a10", angle=45, bevel_r=3)


def combat():
    img = background("#9a2a1e", "#1a0503", seed=8, rays="#ff9a6a")
    draw_sword(img, (0.30, 0.72), (0.82, 0.16), glow_col="#ff7040")
    draw_sword(img, (0.70, 0.72), (0.18, 0.16), glow_col="#ff7040")
    return finish(img, "combat")


def bonus():
    img = background("#2e5aa8", "#050c22", seed=9, rays="#bcd6ff")
    frame = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle([0.26 * S, 0.12 * S, 0.74 * S, 0.20 * S], radius=10, fill=255)
    d.rounded_rectangle([0.26 * S, 0.80 * S, 0.74 * S, 0.88 * S], radius=10, fill=255)
    for x in (0.31, 0.69):
        d.rectangle([(x - 0.02) * S, 0.18 * S, (x + 0.02) * S, 0.82 * S], fill=255)
    glass = poly_mask([(0.34, 0.21), (0.66, 0.21), (0.66, 0.28), (0.53, 0.50), (0.66, 0.72), (0.66, 0.79),
                       (0.34, 0.79), (0.34, 0.72), (0.47, 0.50), (0.34, 0.28)])
    img.alpha_composite(fill_mask(glass, (200, 230, 255, 70)))
    sand = poly_mask([(0.40, 0.33), (0.60, 0.33), (0.52, 0.47), (0.48, 0.47)])
    poly_mask([(0.50, 0.62), (0.63, 0.77), (0.37, 0.77)], sand)
    line_mask([(0.5, 0.48), (0.5, 0.70)], 0.012, sand)
    img.alpha_composite(glow(sand, "#ffd76a", 12, 1.4))
    img.alpha_composite(fill_mask(sand, linear("#fff2b0", "#e0a020", 90)))
    img.alpha_composite(bevel(glass, r=4))
    emblem(img, frame, "#ffe9a8", "#8a5a10", glow_col="#ffd76a", glow_r=10, angle=60, bevel_r=4)
    return finish(img, "bonus")


def monster_slayer():
    img = background("#c7901c", "#2a1602", seed=10, rays="#ffe7a0")
    skull = Image.new("L", (S, S), 0)
    ellipse_mask((0.24, 0.18, 0.76, 0.66), skull)
    ImageDraw.Draw(skull).rounded_rectangle([0.34 * S, 0.52 * S, 0.66 * S, 0.80 * S], radius=18, fill=255)
    holes = Image.new("L", (S, S), 0)
    ellipse_mask((0.31, 0.38, 0.46, 0.54), holes)
    ellipse_mask((0.54, 0.38, 0.69, 0.54), holes)
    poly_mask([(0.5, 0.56), (0.46, 0.64), (0.54, 0.64)], holes)
    for x in (0.41, 0.47, 0.53, 0.59):
        ImageDraw.Draw(holes).rectangle([(x - 0.008) * S, 0.70 * S, (x + 0.008) * S, 0.80 * S], fill=255)
    skull = ImageChops.subtract(skull, holes)
    emblem(img, skull, "#fffaf0", "#a89a80", angle=60, bevel_r=6)
    eyes = Image.new("L", (S, S), 0)
    ellipse_mask((0.35, 0.42, 0.42, 0.50), eyes)
    ellipse_mask((0.58, 0.42, 0.65, 0.50), eyes)
    img.alpha_composite(glow(eyes, "#ff3020", 10, 2.0))
    img.alpha_composite(fill_mask(eyes, (255, 120, 80, 255)))
    draw_sword(img, (0.24, 0.86), (0.86, 0.12), width=0.07, glow_col="#fff0b0")
    return finish(img, "monster_slayer")


def buff():
    img = background("#2aa36a", "#03140b", seed=11, rays="#b0ffd8")
    arrows = Image.new("L", (S, S), 0)
    for y in (0.16, 0.42):
        poly_mask([(0.5, y), (0.80, y + 0.26), (0.66, y + 0.26), (0.5, y + 0.12), (0.34, y + 0.26), (0.20, y + 0.26)], arrows)
    ImageDraw.Draw(arrows).rectangle([0.44 * S, 0.66 * S, 0.56 * S, 0.86 * S], fill=255)
    emblem(img, arrows, "#ffffff", "#5ae0a0", glow_col="#8affc8", glow_r=20, angle=80)
    return finish(img, "buff")


def heal():
    img = background("#36b84a", "#04180a", seed=12, rays="#d0ffd0")
    cross = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(cross)
    d.rounded_rectangle([0.40 * S, 0.18 * S, 0.60 * S, 0.82 * S], radius=16, fill=255)
    d.rounded_rectangle([0.18 * S, 0.40 * S, 0.82 * S, 0.60 * S], radius=16, fill=255)
    emblem(img, cross, "#ffffff", "#7ef08a", glow_col="#b6ffb0", glow_r=24, angle=70)
    return finish(img, "heal")


def shadow_icon():
    img = background("#3c3f58", "#05050a", seed=13)
    hood = Image.new("L", (S, S), 0)
    poly_mask([(0.5, 0.10), (0.80, 0.40), (0.86, 0.90), (0.14, 0.90), (0.20, 0.40)], hood)
    face = ellipse_mask((0.32, 0.34, 0.68, 0.76))
    emblem(img, hood, "#6a6e8e", "#14141f", angle=60, bevel_r=8)
    img.alpha_composite(fill_mask(face.filter(ImageFilter.GaussianBlur(10)), (0, 0, 0, 250)))
    eyes = Image.new("L", (S, S), 0)
    poly_mask([(0.37, 0.52), (0.47, 0.54), (0.45, 0.58), (0.38, 0.56)], eyes)
    poly_mask([(0.63, 0.52), (0.53, 0.54), (0.55, 0.58), (0.62, 0.56)], eyes)
    img.alpha_composite(glow(eyes, "#a070ff", 10, 2.0))
    img.alpha_composite(fill_mask(eyes, (230, 210, 255, 255)))
    return finish(img, "shadow")


def fire():
    img = background("#e0601a", "#200602", seed=14)
    f = flame_mask(0.5, 0.92, 0.8, 0.8, seed=21, tongues=5)
    img.alpha_composite(glow(f, "#ff6a10", 24, 1.5))
    img.alpha_composite(fill_mask(f, linear("#fff6b0", "#e0400c", 90)))
    g = flame_mask(0.5, 0.92, 0.45, 0.45, seed=23, tongues=3)
    img.alpha_composite(fill_mask(g.filter(ImageFilter.GaussianBlur(4)), linear("#ffffff", "#ffd040", 90)))
    return finish(img, "fire")


def shield():
    img = background("#5a6a80", "#0a0e16", seed=15)
    s = poly_mask(heater_shield(0.12, 0.90, 0.29, 0.44))
    emblem(img, s, "#e8eef6", "#5a6474", angle=60, bevel_r=8)
    inner = s.filter(ImageFilter.MinFilter(37))
    img.alpha_composite(fill_mask(inner, linear("#b02a20", "#4a0806", 70)))
    img.alpha_composite(bevel(inner, r=5))
    boss = ellipse_mask((0.42, 0.40, 0.58, 0.56))
    emblem(img, boss, "#ffe8a0", "#8a5a10", angle=60, bevel_r=4, shadow_on=False)
    return finish(img, "shield")


def rune():
    img = background("#6a3ab8", "#0c0420", seed=16, rays="#d8c0ff")
    r = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(r)
    d.ellipse([0.16 * S, 0.16 * S, 0.84 * S, 0.84 * S], outline=255, width=12)
    d.ellipse([0.26 * S, 0.26 * S, 0.74 * S, 0.74 * S], outline=255, width=6)
    for i in range(6):
        a = i / 6 * 2 * math.pi - math.pi / 2
        p = (0.5 + math.cos(a) * 0.34, 0.5 + math.sin(a) * 0.34)
        q = (0.5 + math.cos(a + 2 * math.pi / 6 * 2) * 0.34, 0.5 + math.sin(a + 2 * math.pi / 6 * 2) * 0.34)
        d.line([p[0] * S, p[1] * S, q[0] * S, q[1] * S], fill=255, width=6)
    img.alpha_composite(glow(r, "#c69cff", 14, 1.8))
    img.alpha_composite(fill_mask(r, (240, 225, 255, 255)))
    return finish(img, "rune")


ICONS = [dash, the_fast, berserk, divine_protection, deaths_grasp, potion, combat, bonus, monster_slayer, buff,
         heal, shadow_icon, fire, shield, rune]

if __name__ == "__main__":
    sheet = Image.new("RGBA", (OUT * 2 * 5 + 40, OUT * 2 * 3 + 30), (24, 24, 24, 255))
    for i, f in enumerate(ICONS):
        icon = f()
        sheet.paste(icon.resize((OUT * 2, OUT * 2), Image.LANCZOS), (10 + (i % 5) * (OUT * 2 + 6), 10 + (i // 5) * (OUT * 2 + 6)))
    sheet.save(os.environ.get("SHEET", "/tmp/cd_icons_sheet.png"))
    print("wrote", len(ICONS), "icons to", DST)
