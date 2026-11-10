"""Overseer textures: Mekanism-style bevelled steel + AE2-style traces, in gold."""
import os
import sys
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/overseer/textures"
H = lambda s: tuple(int(s[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
DARK, STEEL, STEEL2, LIGHT, HI = H("#1E2126"), H("#3A3E46"), H("#454A53"), H("#5C616B"), H("#7E848F")
GOLD, GOLD_DIM, GOLD_HI = H("#E8B23A"), H("#8C6418"), H("#FFE08A")
SCREEN, GRID, AMBER, AMBER_DIM = H("#0C0B08"), H("#1E1708"), H("#FFC24A"), H("#7A5718")
CLEAR = (0, 0, 0, 0)


def img(n=16):
    return Image.new("RGBA", (n, n), CLEAR)


def rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            im.putpixel((x, y), c)


def frame(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        im.putpixel((x, y0), c); im.putpixel((x, y1), c)
    for y in range(y0, y1 + 1):
        im.putpixel((x0, y), c); im.putpixel((x1, y), c)


def bolt(im, x, y):
    im.putpixel((x, y), GOLD_HI); im.putpixel((x + 1, y), GOLD)
    im.putpixel((x, y + 1), GOLD); im.putpixel((x + 1, y + 1), GOLD_DIM)


def casing_base():
    im = img()
    rect(im, 0, 0, 15, 15, STEEL)
    frame(im, 0, 0, 15, 15, DARK)
    # bevel: light top/left, dark bottom/right
    for i in range(1, 15):
        im.putpixel((i, 1), HI); im.putpixel((1, i), LIGHT)
        im.putpixel((i, 14), DARK); im.putpixel((14, i), DARK)
    im.putpixel((1, 1), HI)
    # recessed inner plate
    rect(im, 3, 3, 12, 12, STEEL2)
    frame(im, 3, 3, 12, 12, DARK)
    for i in range(4, 12):
        im.putpixel((i, 12), LIGHT); im.putpixel((12, i), LIGHT)
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        bolt(im, x, y)
    return im


def casing():
    im = casing_base()
    # AE2-ish gold trace with nodes across the plate
    for x in range(5, 11):
        im.putpixel((x, 7), GOLD_DIM)
    for y in range(5, 8):
        im.putpixel((5, y), GOLD_DIM)
    for y in range(7, 11):
        im.putpixel((10, y), GOLD_DIM)
    im.putpixel((5, 5), GOLD); im.putpixel((10, 10), GOLD)
    im.putpixel((8, 7), GOLD_HI)
    return im


def casing_top():
    im = casing_base()
    # vent slots
    for y in (5, 7, 9):
        for x in range(5, 11):
            im.putpixel((x, y), DARK)
            im.putpixel((x, y + 1), GOLD_DIM if x in (5, 10) else LIGHT)
    return im


def front():
    im = img()
    rect(im, 0, 0, 15, 15, STEEL)
    frame(im, 0, 0, 15, 15, DARK)
    for i in range(1, 15):
        im.putpixel((i, 1), HI); im.putpixel((1, i), LIGHT)
        im.putpixel((i, 14), DARK); im.putpixel((14, i), DARK)
    frame(im, 1, 1, 14, 14, GOLD_DIM)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        im.putpixel((x, y), GOLD)
    rect(im, 2, 2, 13, 13, SCREEN)
    for y in range(3, 14, 2):  # faint scanlines
        for x in range(2, 14):
            im.putpixel((x, y), GRID)
    return im


def tablet():
    im = img()
    x0, y0, x1, y1 = 2, 1, 13, 14
    rect(im, x0, y0, x1, y1, STEEL)
    frame(im, x0, y0, x1, y1, DARK)
    for x in range(x0 + 1, x1):
        im.putpixel((x, y0 + 1), HI)
    for y in range(y0 + 1, y1):
        im.putpixel((x0 + 1, y), LIGHT)
    # gold trim around screen
    frame(im, x0 + 1, y0 + 2, x1 - 1, y1 - 3, GOLD_DIM)
    im.putpixel((x0 + 1, y0 + 2), GOLD); im.putpixel((x1 - 1, y0 + 2), GOLD)
    rect(im, x0 + 2, y0 + 3, x1 - 2, y1 - 4, SCREEN)
    # status rows
    rows = [(GOLD, 4), ((70, 196, 106, 255), 5), ((242, 210, 75, 255), 4), ((229, 72, 77, 255), 5), ((70, 196, 106, 255), 3)]
    y = y0 + 4
    for c, ln in rows:
        if y > y1 - 5:
            break
        im.putpixel((x0 + 3, y), c)
        for x in range(x0 + 5, min(x0 + 5 + ln, x1 - 2)):
            im.putpixel((x, y), AMBER_DIM)
        y += 2
    # home button
    im.putpixel((7, y1 - 1), GOLD); im.putpixel((8, y1 - 1), GOLD_HI)
    im.putpixel((7, y1 - 2), GOLD_DIM); im.putpixel((8, y1 - 2), GOLD)
    return im


def save(im, rel):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    Image.frombytes("RGBA", im.size, im.tobytes()).save(p)  # fresh PNG, no metadata


save(casing(), "block/monitor_casing.png")
save(casing_top(), "block/monitor_casing_top.png")
save(front(), "block/monitor_screen_front.png")
save(tablet(), "item/monitor_tablet.png")
print("ok")
