"""I/Overseer CurseForge / README art: icon, banner and captioned gallery images.
python3 tools/listing.py <shots_dir> <out_dir>"""
import os
import sys
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

SH, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
FD = '/mnt/skills/examples/canvas-design/canvas-fonts/'
TITLE = lambda s: ImageFont.truetype(FD + 'Tektur-Medium.ttf', s)
BODY = lambda s: ImageFont.truetype(FD + 'Outfit-Bold.ttf', s)
SMALL = lambda s: ImageFont.truetype(FD + 'Outfit-Regular.ttf', s)
W, H = 1280, 720
GOLD, GOLD_HI, GOLD_DIM = (232, 178, 58), (255, 216, 119), (140, 100, 24)
STEEL, STEEL_DARK = (43, 46, 52), (27, 29, 33)
IO = (62, 200, 224)
STATUS = [(70, 196, 106), (242, 210, 75), (245, 158, 46), (229, 72, 77)]
TABLET = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/ioverseer/textures/item/monitor_tablet.png')


def shot(name):
    return Image.open(os.path.join(SH, name)).convert('RGB')


def bg(img):
    im = img.resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(16))
    im = ImageEnhance.Brightness(im).enhance(0.4)
    im = Image.blend(im, Image.new('RGB', (W, H), (20, 15, 6)), 0.4)
    grad = Image.linear_gradient('L').rotate(90).resize((W, H))
    shade = Image.new('RGB', (W, H), (8, 7, 5))
    im = Image.composite(im, shade, grad.point(lambda v: int(80 + v * 0.68)))
    return im.convert('RGBA')


def window(img, w, crop=None):
    if crop:
        img = img.crop(crop)
    h = round(img.height * w / img.width)
    img = img.resize((w, h), Image.LANCZOS)
    f = Image.new('RGBA', (w + 8, h + 8), STEEL_DARK + (255,))
    d = ImageDraw.Draw(f)
    d.rectangle([1, 1, w + 6, h + 6], outline=GOLD_DIM + (255,), width=2)
    for x, y in [(0, 0), (w + 2, 0), (0, h + 2), (w + 2, h + 2)]:
        d.rectangle([x, y, x + 5, y + 5], fill=GOLD + (255,))
    f.paste(img, (4, 4))
    return f


def place(canvas, win, x, y):
    sh = Image.new('RGBA', (win.width + 80, win.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([40, 50, 40 + win.width, 50 + win.height], fill=(0, 0, 0, 200))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)), (x - 40, y - 40))
    canvas.alpha_composite(win, (x, y))


def glow_text(canvas, xy, text, font, fill=(255, 255, 255), glow=GOLD, radius=16, strength=200):
    layer = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=font, fill=glow + (strength,))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))
    ImageDraw.Draw(canvas).text(xy, text, font=font, fill=fill + (255,))


def chips(canvas, x, y):
    d = ImageDraw.Draw(canvas)
    for i, c in enumerate(STATUS):
        d.rectangle([x + i * 46, y, x + i * 46 + 38, y + 12], fill=c + (255,))


def trace(canvas, x0, x1, y):
    d = ImageDraw.Draw(canvas)
    d.line([x0, y, x1, y], fill=GOLD_DIM + (255,), width=2)
    d.rectangle([x1 - 3, y - 3, x1 + 3, y + 3], fill=GOLD + (255,))


def brand_text(canvas, xy, font, radius=16, strength=190):
    """I/OVERSEER with the I/O in input/output cyan."""
    x, y = xy
    glow_text(canvas, (x, y), 'I/O', font, fill=IO, glow=IO, radius=radius, strength=strength)
    w = ImageDraw.Draw(canvas).textlength('I/O', font=font)
    glow_text(canvas, (x + w, y), 'VERSEER', font, glow=GOLD, radius=radius, strength=strength)


def text_block(canvas, x, y, title, lines, tsize=64):
    if title == 'I/OVERSEER':
        brand_text(canvas, (x, y), TITLE(tsize), radius=14, strength=160)
    else:
        glow_text(canvas, (x, y), title, TITLE(tsize), glow=GOLD, radius=14, strength=160)
    y += tsize + 26
    trace(canvas, x, x + 300, y)
    y += 24
    d = ImageDraw.Draw(canvas)
    for ln in lines:
        d.text((x, y), ln, font=BODY(26), fill=(233, 226, 208, 255))
        y += 38
    return y


def save(canvas, name, size=None):
    im = canvas.convert('RGB') if canvas.mode != 'RGB' else canvas
    if size:
        im = im.resize(size, Image.LANCZOS)
    Image.frombytes('RGB', im.size, im.tobytes()).save(os.path.join(OUT, name), optimize=True)


def tablet_art(px):
    t = Image.open(TABLET).convert('RGBA')
    return t.resize((16 * px, 16 * px), Image.NEAREST)


# ---------- icon ----------
def icon():
    S = 1024
    c = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = 42
    t = tablet_art(px).crop((2 * px, 1 * px, 14 * px, 15 * px))
    x, y = (S - t.width) // 2, (S - t.height) // 2
    # gold glow from the tablet's own silhouette (blur the mask, not the colour, so the edge stays gold)
    mask = Image.new('L', (S, S), 0)
    mask.paste(t.split()[3], (x, y))
    for r, k, col in [(90, 1.7, GOLD), (45, 1.8, GOLD), (18, 1.6, GOLD_HI)]:
        a = mask.filter(ImageFilter.GaussianBlur(r)).point(lambda v, k=k: min(255, int(v * k)))
        layer = Image.new('RGBA', (S, S), col + (0,))
        layer.putalpha(a)
        c.alpha_composite(layer)
    # fade to nothing before the canvas edge so no box shows
    edge = Image.new('L', (S, S), 0)
    ImageDraw.Draw(edge).ellipse([60, 60, S - 60, S - 60], fill=255)
    edge = edge.filter(ImageFilter.GaussianBlur(50))
    c.putalpha(Image.composite(c.split()[3], Image.new('L', (S, S), 0), edge))
    c.alpha_composite(t, (x, y))
    c.save(os.path.join(OUT, 'icon-1024.png'))
    Image.frombytes('RGBA', c.size, c.tobytes()).resize((400, 400), Image.LANCZOS).save(os.path.join(OUT, 'icon.png'))
    return c


# ---------- banner ----------
def banner(ic):
    BW, BH = 1600, 400
    c = bg(shot('m9.png').resize((BW, BH * 2)).crop((0, BH // 2, BW, BH + BH // 2)))
    c = c.resize((BW, BH))
    c.alpha_composite(ic.resize((300, 300), Image.LANCZOS), (70, 50))
    brand_text(c, (420, 80), TITLE(130), radius=22, strength=200)
    trace(c, 424, 1000, 248)
    ImageDraw.Draw(c).text((424, 272), 'Watch every machine in your base, from any mod.', font=BODY(38), fill=(233, 226, 208, 255))
    chips(c, 426, 340)
    save(c, 'banner.png')


# ---------- gallery ----------
def gallery():
    pages = [
        ('01-ioverseer.png', 'hero.png', 'I/OVERSEER', ['Every machine in your base,', 'from any mod, at a glance.'], (0, 60, 1280, 720), 660),
        ('02-details.png', 'e1.png', 'Details', ['Working, idle, full or no power.', 'Energy, fluids, slots, position.', 'Pin machines to watch anywhere.'], (128, 38, 1152, 682), 640),
        ('03-find-it.png', 'e3.png', 'Find it', ['Ctrl-click a machine: gold outline', 'through walls, a beam and a HUD', 'arrow with the distance.'], (240, 0, 1120, 600), 680),
        ('04-live-preview.png', 'h2.png', 'Live preview', ['Hold the tablet to see the power', 'draw of nearby machines, stored', 'energy and the status mix.'], (760, 260, 1280, 720), 480),
        ('05-alerts.png', 'alert.png', 'Alerts', ['A toast when a pinned machine', 'runs out of power or fills up.'], (560, 0, 1280, 420), 680),
        ('06-screen.png', 'scr.png', 'Wall Screen', ['A wall monitor with a live', 'status list on its face.'], (330, 0, 900, 420), 600),
        ('07-mod-resources.png', 'p6.png', 'Mod resources', ['Mekanism chemicals and', 'Ars Nouveau Source,', 'next to FE and fluids.'], (780, 90, 1150, 690), 360),
    ]
    for name, src, title, lines, crop, ww in pages:
        img = shot(src)
        c = bg(img)
        y = text_block(c, 70, 200 if len(lines) < 3 else 180, title, lines, 64 if name.startswith('01') else 60)
        if name.startswith('01'):
            chips(c, 72, y + 16)
        win = window(img, ww, crop)
        x = W - win.width - 60
        yy = (H - win.height) // 2
        place(c, win, x, yy)
        save(c, name)


ic = icon()
banner(ic)
gallery()
print('done')
