"""Draws the 2048 Override app icon: the Override Code tile of the game (white,
with its lightning bolt and the number in Orbitron) glowing on the dark green
glass of the terminal, with the terminal's scanlines.

Writes the adaptive icon layers (background, foreground, monochrome), the
splash icon, the 512 px Play Store icon and a test sheet at real sizes.
Run with a Python that has Pillow:  python tools/make_app_icon.py
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
STORE = os.path.join(ROOT, 'tools/store')
FONT = os.path.join(ROOT, 'app/src/main/res/font/orbitron.ttf')
MONO = os.path.join(ROOT, 'app/src/main/res/font/share_tech_mono.ttf')

# palette (override.js, the green phosphor theme and the 2048 tile)
BG = (2, 10, 5)
BG2 = (6, 34, 16)
GREEN = (57, 255, 106)
TILE_TOP = (255, 255, 255)
TILE_BOTTOM = (205, 220, 232)
INK = (10, 20, 32)

SS = 16                      # supersampling: 1 dp = 16 px while drawing
D = 108 * SS                 # the adaptive icon canvas, 108 dp
C = D / 2


def dp(v):
    return v * SS


def tile_box(size):
    h = dp(size) / 2
    return [C - h, C - h, C + h, C + h]


def rounded_mask(box, r):
    m = Image.new('L', (D, D), 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=r, fill=255)
    return m


def bolt(draw, x, y, s, fill):
    """the lightning bolt of the browser's icon set, in its 22 x 14 box"""
    pts = [(9, 0), (3, 8), (8, 8), (5, 14), (13, 5), (8.5, 5), (11, 0)]
    draw.polygon([(x + px * s, y + py * s) for px, py in pts], fill=fill)
    for seg in ([(15, 2), (13, 6), (16, 6), (14, 10)], [(19, 5), (17.5, 8), (20, 8), (18.5, 11)]):
        draw.line([(x + px * s, y + py * s) for px, py in seg], fill=fill, width=int(s * 1.3), joint='curve')


def number(draw, text, cx, cy, width, fill):
    """the number in Orbitron ExtraBold, sized to [width]"""
    size = dp(20)
    while True:
        f = ImageFont.truetype(FONT, int(size))
        try:
            f.set_variation_by_axes([800])
        except Exception:
            pass
        if f.getlength(text) <= width or size < dp(4):
            break
        size *= 0.96
    draw.text((cx, cy), text, font=f, fill=fill, anchor='mm')


TILE = 56                    # dp, inside the 66 dp safe circle


def foreground():
    img = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    box = tile_box(TILE)
    r = dp(9)
    # phosphor glow around the tile
    glow = Image.new('L', (D, D), 0)
    ImageDraw.Draw(glow).rounded_rectangle([b + (-dp(1.5) if i < 2 else dp(1.5)) for i, b in enumerate(box)], radius=r + dp(1.5), fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(dp(3.5)))
    g = Image.new('RGBA', (D, D), GREEN + (0,))
    g.putalpha(glow)
    img = Image.alpha_composite(img, g)
    # the tile: white to pale steel, top to bottom
    grad = Image.new('RGBA', (D, D))
    gd = ImageDraw.Draw(grad)
    for y in range(int(box[1]), int(box[3]) + 1):
        t = (y - box[1]) / (box[3] - box[1])
        col = tuple(int(TILE_TOP[i] + (TILE_BOTTOM[i] - TILE_TOP[i]) * t) for i in range(3))
        gd.line([(0, y), (D, y)], fill=col + (255,))
    mask = rounded_mask(box, r)
    tile = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    tile.paste(grad, (0, 0), mask)
    img = Image.alpha_composite(img, tile)
    s = dp(TILE / 72)
    # label strip and the printed circuit, like the tile in the game
    strip_h = dp(TILE * 15 / 72)
    strip = Image.new('L', (D, D), 0)
    ImageDraw.Draw(strip).rectangle([box[0], box[3] - strip_h, box[2], box[3]], fill=26)
    shade = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    shade.putalpha(ImageChops.multiply(strip, mask))
    img = Image.alpha_composite(img, shade)
    d = ImageDraw.Draw(img)
    s = dp(TILE / 72)
    bolt(d, box[0] + 7 * s, box[1] + 7 * s, s * 1.3, INK + (235,))
    number(d, '2048', C, box[1] + (box[3] - box[1]) * 0.50, dp(TILE - 14), INK + (255,))
    # the label strip says what the tile is, like every tile in the game
    lf = ImageFont.truetype(MONO, int(dp(5.2)))
    d.text((C, box[3] - strip_h / 2), 'OVERRIDE', font=lf, fill=INK + (200,), anchor='mm')
    # edge of the tile
    d.rounded_rectangle(box, radius=r, outline=(255, 255, 255, 120), width=int(dp(0.6)))
    return img


def background():
    img = Image.new('RGBA', (D, D), BG + (255,))
    # radial glow of the tube
    glow = Image.new('L', (D, D), 0)
    ImageDraw.Draw(glow).ellipse([C - dp(50), C - dp(50), C + dp(50), C + dp(50)], fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(dp(18)))
    layer = Image.new('RGBA', (D, D), BG2 + (255,))
    layer.putalpha(glow)
    img = Image.alpha_composite(img, layer)
    d = ImageDraw.Draw(img)
    # scanlines, one dark line every 3 dp
    y = 0
    while y < D:
        d.rectangle([0, y, D, y + dp(1)], fill=(0, 0, 0, 70))
        y += dp(3)
    return img


def monochrome():
    img = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    box = tile_box(TILE)
    m = rounded_mask(box, dp(9))
    cut = Image.new('L', (D, D), 0)
    cd = ImageDraw.Draw(cut)
    s = dp(TILE / 72)
    bolt(cd, box[0] + 7 * s, box[1] + 7 * s, s * 1.3, 255)
    number(cd, '2048', C, box[1] + (box[3] - box[1]) * 0.50, dp(TILE - 14), 255)
    alpha = ImageChops.subtract(m, cut)
    img.paste(Image.new('RGBA', (D, D), (255, 255, 255, 255)), (0, 0), alpha)
    return img


def down(img, px):
    return img.resize((px, px), Image.LANCZOS)


os.makedirs(RES, exist_ok=True)
os.makedirs(STORE, exist_ok=True)
fg, bg, mono = foreground(), background(), monochrome()
down(bg, 432).save(os.path.join(RES, 'ic_launcher_background.png'))
down(fg, 432).save(os.path.join(RES, 'ic_launcher_foreground.png'))
down(mono, 432).save(os.path.join(RES, 'ic_launcher_monochrome.png'))
# splash: the 108 dp canvas maps onto the 288 dp splash area; content stays inside 192 dp
down(fg, 1152).save(os.path.join(RES, 'splash_icon.png'))

# Play Store: what a launcher shows is the middle 72 dp, so render that full bleed
full = Image.alpha_composite(bg, fg)
crop = full.crop((int(dp(18)), int(dp(18)), int(dp(90)), int(dp(90))))
crop.resize((512, 512), Image.LANCZOS).convert('RGB').save(os.path.join(STORE, 'icon-512.png'))

# test sheet: circle and squircle masks at launcher sizes
sheet = Image.new('RGB', (900, 260), (30, 30, 30))
x = 20
icon72 = crop.convert('RGBA')
for px in (192, 144, 96, 64, 48):
    for shape in ('circle', 'square'):
        ic = icon72.resize((px, px), Image.LANCZOS)
        m = Image.new('L', (px * 4, px * 4), 0)
        md = ImageDraw.Draw(m)
        if shape == 'circle':
            md.ellipse([0, 0, px * 4, px * 4], fill=255)
        else:
            md.rounded_rectangle([0, 0, px * 4, px * 4], radius=px, fill=255)
        ic.putalpha(m.resize((px, px), Image.LANCZOS))
        sheet.paste(ic, (x, 20 if shape == 'circle' else 30 + px), ic)
    x += px + 20
os.makedirs(os.path.join(ROOT, 'tools/shots'), exist_ok=True)
sheet.save(os.path.join(ROOT, 'tools/shots/icon-sheet.png'))
print('icon written')
