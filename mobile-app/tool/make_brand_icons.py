"""Generates NepalGo brand images (app icon, adaptive foreground, splash logo).

Run from mobile-app/:  python tool/make_brand_icons.py
Then:                  dart run flutter_launcher_icons && dart run flutter_native_splash:create
Colours come from lib/core/theme.dart (primary green #387B53, secondary gold #E2B338).
Drawn at 4x and downsampled = cheap anti-aliasing.
"""
from PIL import Image, ImageDraw

GREEN, GOLD, WHITE, SNOW = (56, 123, 83, 255), (226, 179, 56, 255), (255, 255, 255, 255), (228, 239, 232, 255)
S = 4096  # supersampled canvas (4 x 1024)


def mark(d: ImageDraw.ImageDraw, cx: float, cy: float, size: float, peak=WHITE, snow=SNOW):
    """Himalaya mark: sun behind two peaks, centred on (cx, cy), `size` = width."""
    u = size / 100.0
    # sun (gold) behind the right shoulder
    d.ellipse([cx + 8 * u, cy - 38 * u, cx + 36 * u, cy - 10 * u], fill=GOLD)
    # back peak (lighter)
    d.polygon([(cx - 6 * u, cy + 30 * u), (cx + 22 * u, cy - 14 * u), (cx + 50 * u, cy + 30 * u)], fill=snow)
    # main peak
    d.polygon([(cx - 50 * u, cy + 30 * u), (cx - 10 * u, cy - 34 * u), (cx + 30 * u, cy + 30 * u)], fill=peak)
    # snow-line notch on main peak (green zig-zag gives the "snow cap" look)
    d.polygon([(cx - 23 * u, cy - 13 * u), (cx - 16 * u, cy - 6 * u), (cx - 10 * u, cy - 14 * u),
               (cx - 4 * u, cy - 6 * u), (cx + 3 * u, cy - 13 * u), (cx - 10 * u, cy - 34 * u)], fill=peak)
    d.line([(cx - 23 * u, cy - 13 * u), (cx - 16 * u, cy - 6 * u), (cx - 10 * u, cy - 14 * u),
            (cx - 4 * u, cy - 6 * u), (cx + 3 * u, cy - 13 * u)], fill=GREEN, width=int(2.2 * u))


def save(img: Image.Image, path: str):
    img.resize((1024, 1024), Image.LANCZOS).save(path)
    print("wrote", path)


# 1) full icon: green square with the mark (iOS + legacy Android). iOS rounds corners itself.
icon = Image.new("RGBA", (S, S), GREEN)
mark(ImageDraw.Draw(icon), S / 2, S / 2 + S * 0.03, S * 0.62)
save(icon.convert("RGB"), "assets/branding/icon.png")  # iOS icons must not have alpha

# 2) adaptive foreground: transparent, mark inside the 66% safe zone (launcher may crop to circle)
fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mark(ImageDraw.Draw(fg), S / 2, S / 2 + S * 0.02, S * 0.42)
save(fg, "assets/branding/icon_foreground.png")

# 3) splash logo: mark only, on transparent; splash background colour set in pubspec
splash = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mark(ImageDraw.Draw(splash), S / 2, S / 2 + S * 0.03, S * 0.62)
save(splash, "assets/branding/splash.png")
