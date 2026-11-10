"""Builds the launcher icons from the user's design "shiny battery with leaf".

Output: legacy icon (Android 7), adaptive foreground and monochrome layer (Android 8+ / 13),
and a 512 px icon for the store listing.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools/icon/vorlage-glaenzend-blatt.png"
RES = ROOT / "app/src/main/res"
BOX = None  # the design fills the whole square
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def tile() -> Image.Image:
    t = Image.open(SRC).convert("RGBA")
    if BOX:
        t = t.crop(BOX)
    size = t.size[0]
    mask = Image.new("L", t.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.215), fill=255)
    t.putalpha(mask)
    return t


def on_canvas(img: Image.Image, canvas_px: int, img_px: int) -> Image.Image:
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    scaled = img.resize((img_px, img_px), Image.LANCZOS)
    off = (canvas_px - img_px) // 2
    out.alpha_composite(scaled, (off, off))
    return out


def monochrome(t: Image.Image) -> Image.Image:
    # The white and silver parts (pulse, battery frame) become the shape; the green stays empty.
    a = np.asarray(t).astype(float)
    whiteness = a[..., :3].min(axis=2)
    alpha = np.clip((whiteness - 150) * 3, 0, 255) * (a[..., 3] / 255)
    out = np.zeros_like(a)
    out[..., :3] = 255
    out[..., 3] = alpha
    return Image.fromarray(out.astype("uint8"), "RGBA")


def main() -> None:
    t = tile()
    mono = monochrome(t)
    for name, f in DENSITIES.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(exist_ok=True)
        # WebP with transparency works from Android 4.3 on and is much smaller than PNG.
        t.resize((round(48 * f),) * 2, Image.LANCZOS).save(d / "ic_launcher.webp", quality=90, method=6)
        # Adaptive layers: 108 dp canvas; the full-bleed picture at 72 dp covers the whole mask
        # and keeps the battery cap and the leaf inside it.
        on_canvas(t, round(108 * f), round(72 * f)).save(d / "ic_launcher_foreground.webp", quality=90, method=6)
        on_canvas(mono, round(108 * f), round(66 * f)).save(d / "ic_launcher_monochrome.webp", quality=90, method=6)
    t.resize((512, 512), Image.LANCZOS).save(ROOT / "tools/icon/icon-512.png", optimize=True)


if __name__ == "__main__":
    main()
