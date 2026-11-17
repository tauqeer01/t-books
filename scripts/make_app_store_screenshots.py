#!/usr/bin/env python3
"""Builds App Store listing screenshots from raw iOS simulator captures.

Each listing image has the same look as the Google Play set: brand-purple gradient, a headline and a
one-line description, and the app screen with rounded corners running off the bottom edge.

Input:  store/app-store/screenshots/raw/<device>/<NN-name>.png  (full-resolution simulator screenshots)
Output: store/app-store/screenshots/<device>/<NN-name>.png      (exact App Store Connect sizes, no alpha)

    python3 scripts/make_app_store_screenshots.py
"""
import pathlib

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHOTS = ROOT / "store/app-store/screenshots"
FONT = "/System/Library/Fonts/SFNS.ttf"  # SF Pro (macOS system font)

# Gradient sampled from the Play Store screenshots, so both stores match
TOP = (0x58, 0x34, 0xFE)
BOTTOM = (0x2A, 0x17, 0xB8)
GLOW = (0x8A, 0x70, 0xFF)

# Headline and description for each screen, in upload order
CAPTIONS = {
    "01-home": ("Your library, beautifully organized", "Pick up right where you left off"),
    "02-reader": ("Smooth, beautiful PDF reading", "Search, bookmarks and four page colors"),
    "03-notes": ("Handwritten notes that feel natural", "Templates, Apple Pencil ink and study tape"),
    "04-flashcards": ("Study smarter with flashcards", "Spaced repetition schedules every review"),
    "05-fill-sign": ("Fill and sign any PDF", "Save your signature once, sign in seconds"),
    "06-reading-goal": ("Build a daily reading habit", "Set a goal, keep your streak, see your week"),
    "07-library": ("Find any document fast", "Search, sort, favorites and collections"),
    "08-dark-mode": ("Easy on the eyes, day or night", "A full dark mode across the whole app"),
}
# The iPad set shows the notes list instead of the editor
IPAD_CAPTIONS = dict(CAPTIONS, **{
    "03-notes": ("All your notebooks in one place", "Folders, tags and search across every note"),
})

DEVICES = {
    # canvas size, headline px, description px, screen width as share of canvas, screen top, corner radius.
    # App Store Connect slots: "iPhone with Dynamic Island (medium display)" is the required iPhone slot
    # (1206x2622 or 1179x2556); "large display" (1320x2868) is optional. "raw" names the capture folder.
    "iphone-6.3": dict(size=(1206, 2622), head=88, sub=46, screen=0.84, top=512, radius=88, captions=CAPTIONS, raw="iphone-6.9"),
    "iphone-6.9": dict(size=(1320, 2868), head=96, sub=50, screen=0.84, top=560, radius=96, captions=CAPTIONS),
    "ipad-13": dict(size=(2064, 2752), head=118, sub=60, screen=0.80, top=560, radius=64, captions=IPAD_CAPTIONS),
}


def font(px, weight):
    f = ImageFont.truetype(FONT, px)
    f.set_variation_by_name(weight)
    return f


def background(size):
    w, h = size
    grad = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / (h - 1)
        grad.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    bg = grad.resize(size)
    # Soft light glow in the top-right corner, as on the Play Store images
    glow = Image.new("L", size, 0)
    ImageDraw.Draw(glow).ellipse((int(w * 0.45), -int(h * 0.25), int(w * 1.35), int(h * 0.25)), fill=150)
    glow = glow.filter(ImageFilter.GaussianBlur(w * 0.12))
    return Image.composite(Image.new("RGB", size, GLOW), bg, glow)


def wrap(draw, text, fnt, max_width):
    """One line if it fits; otherwise two lines of similar length (no lone word on the second line)."""
    words = text.split()
    if draw.textlength(text, font=fnt) <= max_width:
        return [text]
    best = None
    for i in range(1, len(words)):
        a, b = " ".join(words[:i]), " ".join(words[i:])
        widest = max(draw.textlength(a, font=fnt), draw.textlength(b, font=fnt))
        if widest <= max_width and (best is None or widest < best[0]):
            best = (widest, [a, b])
    if best:
        return best[1]
    return greedy_wrap(draw, text, fnt, max_width)


def greedy_wrap(draw, text, fnt, max_width):
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=fnt) <= max_width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + [line]


def compose(raw_path, out_path, cfg, caption):
    w, h = cfg["size"]
    canvas = background((w, h))
    draw = ImageDraw.Draw(canvas)

    # Headline and description, centered
    head_font, sub_font = font(cfg["head"], "Bold"), font(cfg["sub"], "Regular")
    y = int(h * 0.055)
    for line in wrap(draw, caption[0], head_font, w * 0.86):
        draw.text((w / 2, y), line, font=head_font, fill="white", anchor="ma")
        y += int(cfg["head"] * 1.14)
    y += int(cfg["sub"] * 0.35)
    for line in wrap(draw, caption[1], sub_font, w * 0.86):
        draw.text((w / 2, y), line, font=sub_font, fill=(255, 255, 255, 220), anchor="ma")
        y += int(cfg["sub"] * 1.25)

    # App screen: scaled, rounded corners, soft shadow, running off the bottom edge
    screen = Image.open(raw_path).convert("RGB")
    sw = int(w * cfg["screen"])
    sh = round(screen.height * sw / screen.width)
    screen = screen.resize((sw, sh), Image.LANCZOS)
    top = max(cfg["top"], y + int(h * 0.025))
    left = (w - sw) // 2
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), radius=cfg["radius"], fill=255)

    shadow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((left, top + 18, left + sw, top + sh + 18), radius=cfg["radius"], fill=110)
    shadow = shadow.filter(ImageFilter.GaussianBlur(36))
    canvas = Image.composite(Image.new("RGB", (w, h), (20, 10, 70)), canvas, shadow)
    canvas.paste(screen, (left, top), mask)

    canvas.save(out_path, optimize=True)


def main():
    for device, cfg in DEVICES.items():
        src, dst = SHOTS / "raw" / cfg.get("raw", device), SHOTS / device
        dst.mkdir(parents=True, exist_ok=True)
        for raw in sorted(src.glob("*.png")):
            caption = cfg["captions"].get(raw.stem)
            if not caption:
                print(f"skip {raw.name}: no caption")
                continue
            compose(raw, dst / raw.name, cfg, caption)
            print(f"{device}/{raw.name}")


if __name__ == "__main__":
    main()
