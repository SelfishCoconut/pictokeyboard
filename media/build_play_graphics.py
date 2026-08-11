"""Draw the Play store graphics from the app's own palette.

Run from the repository root:

    python3 media/build_play_graphics.py

Writes media/logo.png (the 512x512 store icon) and
media/play/feature-graphic.png (1024x500). Both are derived from the same
motif as the launcher icon in app/src/main/res/drawable/ic_launcher_foreground.xml
-- four pictogram tiles in AAC frame colours on the app's blue -- so the icon a
caregiver taps and the banner they saw on the store are the same object.

Keep this in step with ic_launcher_foreground.xml and ic_launcher_background.xml
by hand. It is two files and a colour; a build step to guarantee it would cost
more than it saves.
"""

import pathlib

from PIL import Image, ImageDraw, ImageFont

# --- the palette, from ui/theme/Tokens.kt --------------------------------------

BLUE = (0x1A, 0x56, 0xA8)
WHITE = (0xFF, 0xFF, 0xFF)
INK_ON_BLUE = (0xDC, 0xE5, 0xF0)

# The first four seeded categories, in their own frame colours. Blue is
# deliberately not among them: a blue frame on the blue ground is the one pairing
# that loses its edge, and these four are what the board actually opens on.
TILES = [
    (0xFF, 0xC1, 0x07),  # Personas
    (0x4C, 0xAF, 0x50),  # Acciones
    (0xFF, 0x98, 0x00),  # Comida
    (0xF4, 0x43, 0x36),  # Sentimientos
]

BOLD = "/usr/share/fonts/noto/NotoSans-Bold.ttf"
REGULAR = "/usr/share/fonts/noto/NotoSans-Regular.ttf"

ROOT = pathlib.Path(__file__).resolve().parent


def draw_grid(img, origin, tile, gap, border, radius):
    """The 2x2 of framed tiles: a white face with a coloured frame around it."""
    draw = ImageDraw.Draw(img)
    ox, oy = origin
    for index, colour in enumerate(TILES):
        x = ox + (index % 2) * (tile + gap)
        y = oy + (index // 2) * (tile + gap)
        draw.rounded_rectangle(
            [x, y, x + tile, y + tile],
            radius=radius,
            fill=WHITE,
            outline=colour,
            width=border,
        )


def store_icon(size=512):
    """512x512, full bleed. Play applies its own rounding, so this does not."""
    img = Image.new("RGB", (size, size), BLUE)
    tile, gap, border = 150, 24, 14
    span = tile * 2 + gap
    origin = ((size - span) // 2, (size - span) // 2)
    draw_grid(img, origin, tile, gap, border, radius=30)
    return img


TITLE = "PictoKeyboard"
TAGLINE = "Pictogramas que escriben y hablan"

#: Play crops this graphic for some placements. Nothing that has to survive goes
#: nearer than this to an edge.
MARGIN = 72


def _width(draw, text, font):
    box = draw.textbbox((0, 0), text, font=font)
    return box[2] - box[0]


def feature_graphic(width=1024, height=500):
    """
    1024x500, laid out from the middle out: the motif and the words are measured
    together and the pair is centred, so neither runs off an edge Play may crop.
    """
    img = Image.new("RGB", (width, height), BLUE)
    draw = ImageDraw.Draw(img)

    title_font = ImageFont.truetype(BOLD, 76)
    tag_font = ImageFont.truetype(REGULAR, 30)
    tile, gap, border, column_gap = 118, 18, 11, 68
    span = tile * 2 + gap

    text_width = max(_width(draw, TITLE, title_font), _width(draw, TAGLINE, tag_font))
    block = span + column_gap + text_width
    left = max(MARGIN, (width - block) // 2)

    draw_grid(img, (left, (height - span) // 2), tile, gap, border, radius=24)

    text_x = left + span + column_gap
    draw.text((text_x, height // 2 - 62), TITLE, font=title_font, fill=WHITE)
    draw.text((text_x, height // 2 + 26), TAGLINE, font=tag_font, fill=INK_ON_BLUE)
    return img


def main():
    icon = ROOT / "logo.png"
    store_icon().save(icon)
    print(f"{icon.relative_to(ROOT.parent)}  {Image.open(icon).size}")

    banner = ROOT / "play" / "feature-graphic.png"
    banner.parent.mkdir(parents=True, exist_ok=True)
    feature_graphic().save(banner)
    print(f"{banner.relative_to(ROOT.parent)}  {Image.open(banner).size}")


if __name__ == "__main__":
    main()
