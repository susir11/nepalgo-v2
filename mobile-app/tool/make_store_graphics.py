"""Google Play store graphics for NepalGo (icon 512 + feature graphic 1024x500).

Run from mobile-app/:   python tool/make_store_graphics.py <path-to-Inter.ttf> [out-dir]
Inter (SIL Open Font License) is the app's own font: https://github.com/google/fonts/tree/main/ofl/inter
Colours and the mountain mark match lib/core/theme.dart and tool/make_brand_icons.py.
Drawn at 4x and downsampled = cheap anti-aliasing.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

GREEN, GREEN_DARK, GOLD = (56, 123, 83), (38, 88, 59), (226, 179, 56)
WHITE, SNOW = (255, 255, 255), (228, 239, 232)


def mark(d, cx, cy, size):
    """Same Himalaya mark as the launcher icon: sun behind two peaks, centred on (cx, cy)."""
    u = size / 100.0
    d.ellipse([cx + 8 * u, cy - 38 * u, cx + 36 * u, cy - 10 * u], fill=GOLD)
    d.polygon([(cx - 6 * u, cy + 30 * u), (cx + 22 * u, cy - 14 * u), (cx + 50 * u, cy + 30 * u)], fill=SNOW)
    d.polygon([(cx - 50 * u, cy + 30 * u), (cx - 10 * u, cy - 34 * u), (cx + 30 * u, cy + 30 * u)], fill=WHITE)
    d.line([(cx - 23 * u, cy - 13 * u), (cx - 16 * u, cy - 6 * u), (cx - 10 * u, cy - 14 * u),
            (cx - 4 * u, cy - 6 * u), (cx + 3 * u, cy - 13 * u)], fill=GREEN, width=int(2.2 * u))


def inter(path, px, weight):
    f = ImageFont.truetype(path, px)
    try:
        f.set_variation_by_axes([min(max(px / 4, 14), 32), weight])   # optical size, weight
    except Exception:
        pass
    return f


def feature_graphic(font_path, out):
    W, H, S = 1024, 500, 4
    img = Image.new("RGB", (W * S, H * S), GREEN)
    d = ImageDraw.Draw(img)
    # Soft diagonal shade: lighter top-left -> darker bottom-right (no busy detail: Play crops/overlays text).
    for y in range(H * S):
        t = y / (H * S)
        r = int(GREEN[0] + (GREEN_DARK[0] - GREEN[0]) * t)
        g = int(GREEN[1] + (GREEN_DARK[1] - GREEN[1]) * t)
        b = int(GREEN[2] + (GREEN_DARK[2] - GREEN[2]) * t)
        d.line([(0, y), (W * S, y)], fill=(r, g, b))
    # Distant ridge silhouette along the bottom.
    ridge = [(0, H * S), (0, 420 * S), (140 * S, 360 * S), (230 * S, 400 * S), (360 * S, 330 * S), (470 * S, 390 * S),
             (600 * S, 340 * S), (720 * S, 395 * S), (840 * S, 350 * S), (1024 * S, 410 * S), (1024 * S, H * S)]
    d.polygon(ridge, fill=(46, 104, 70))
    mark(d, 250 * S, 255 * S, 300 * S)
    title = inter(font_path, 112 * S, 800)
    tagline = inter(font_path, 40 * S, 500)
    d.text((470 * S, 150 * S), "NepalGo", font=title, fill=WHITE)
    d.text((474 * S, 290 * S), "Hand-planned trips in Nepal", font=tagline, fill=(235, 243, 238))
    img.resize((W, H), Image.LANCZOS).save(os.path.join(out, "feature-graphic-1024x500.png"), optimize=True)


def icon_512(out):
    src = os.path.join(os.path.dirname(__file__), "..", "assets", "branding", "icon.png")
    Image.open(src).convert("RGB").resize((512, 512), Image.LANCZOS).save(os.path.join(out, "icon-512.png"), optimize=True)


if __name__ == "__main__":
    font = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join("..", "docs", "store-listing")
    os.makedirs(out_dir, exist_ok=True)
    icon_512(out_dir)
    feature_graphic(font, out_dir)
    for f in ("icon-512.png", "feature-graphic-1024x500.png"):
        p = os.path.join(out_dir, f)
        print(f, Image.open(p).size, os.path.getsize(p) // 1024, "KB")
