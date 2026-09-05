#!/usr/bin/env python3
"""Compose raw IDE captures into uniform Marketplace screenshots.

Reads docs/marketing/raw/*.png, writes docs/marketing/out/*.png, every output
exactly CANVAS px with a caption bar and a consistent inset card.

Captions: put "<filename><TAB><caption>" lines in raw/captions.tsv, otherwise the
caption is derived from the filename ("01-run-from-gutter.png" -> "Run from gutter").

Usage:  python3 docs/marketing/compose_screenshots.py
Needs:  pillow
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

CANVAS = (1600, 1000)          # 16:10, what the Marketplace carousel wants
BG = "#141517"                 # canvas behind the card
CARD_BORDER = "#3C3F41"        # IntelliJ-ish separator grey
CAPTION_COLOR = "#EAEAEA"
CAPTION_SIZE = 36
MARGIN = 56                    # canvas edge -> content
CAPTION_TOP = 44
CONTENT_TOP = 128              # below the caption bar
RADIUS = 12

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
OUT = HERE / "out"

FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",       # macOS
    "/System/Library/Fonts/SFNSDisplay.ttf",
    "/usr/share/fonts/truetype/lato/Lato-Semibold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def caption_for(path: Path, overrides: dict[str, str]) -> str:
    if path.name in overrides:
        return overrides[path.name]
    stem = path.stem
    if "-" in stem and stem.split("-", 1)[0].isdigit():
        stem = stem.split("-", 1)[1]
    return stem.replace("-", " ").replace("_", " ").strip().capitalize()


def load_captions() -> dict[str, str]:
    tsv = RAW / "captions.tsv"
    if not tsv.exists():
        return {}
    out = {}
    for line in tsv.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        name, _, caption = line.partition("\t")
        out[name.strip()] = caption.strip()
    return out


def rounded_card(img: Image.Image) -> Image.Image:
    """Round the corners and stroke a hairline border, on transparency."""
    card = img.convert("RGBA")
    mask = Image.new("L", card.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, card.size[0] - 1, card.size[1] - 1],
                                           radius=RADIUS, fill=255)
    card.putalpha(mask)
    ImageDraw.Draw(card).rounded_rectangle([0, 0, card.size[0] - 1, card.size[1] - 1],
                                           radius=RADIUS, outline=CARD_BORDER, width=1)
    return card


def compose(src: Path, caption: str) -> tuple[Image.Image, list[str]]:
    warnings: list[str] = []
    shot = Image.open(src).convert("RGB")

    box_w = CANVAS[0] - 2 * MARGIN
    box_h = CANVAS[1] - CONTENT_TOP - MARGIN
    scale = min(box_w / shot.width, box_h / shot.height)
    if scale > 1.0:
        # Never upscale: a blown-up capture reads as blurry on a retina display.
        scale = 1.0
        if shot.width < box_w * 0.75 and shot.height < box_h * 0.75:
            warnings.append(
                f"only {shot.width}x{shot.height}px - recapture larger, it will float in dead space")
    if shot.width * scale < 900:
        warnings.append("narrow capture; text may read small in the carousel")

    size = (max(1, round(shot.width * scale)), max(1, round(shot.height * scale)))
    card = rounded_card(shot.resize(size, Image.LANCZOS))

    canvas = Image.new("RGB", CANVAS, BG)
    x = (CANVAS[0] - card.width) // 2
    y = CONTENT_TOP  # top-aligned so the caption always sits right above the card
    canvas.paste(card, (x, y), card)

    draw = ImageDraw.Draw(canvas)
    draw.text((MARGIN, CAPTION_TOP), caption, font=load_font(CAPTION_SIZE), fill=CAPTION_COLOR)
    return canvas, warnings


def main() -> int:
    if not RAW.is_dir():
        print(f"no {RAW}", file=sys.stderr)
        return 1
    sources = sorted(p for p in RAW.iterdir()
                     if p.suffix.lower() in {".png", ".jpg", ".jpeg"})
    if not sources:
        print(f"no captures in {RAW}", file=sys.stderr)
        return 1

    overrides = load_captions()
    OUT.mkdir(exist_ok=True)
    for src in sources:
        caption = caption_for(src, overrides)
        canvas, warnings = compose(src, caption)
        dest = OUT / f"{src.stem}.png"
        canvas.save(dest, "PNG", optimize=True)
        print(f"{dest.name}  <- {src.name} ({caption})")
        for w in warnings:
            print(f"    warning: {w}")
    print(f"\n{len(sources)} image(s) in {OUT}, all {CANVAS[0]}x{CANVAS[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
