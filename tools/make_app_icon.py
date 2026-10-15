"""Draws the 2048 Override app icon: the green of the 2 tile in the
game, full bleed, with a light printed circuit on it, and a big 2048 in
Orbitron, the font of the numbers on the tiles.

Writes the adaptive icon layers (background, foreground, monochrome), the
splash icon, the 512 px Play Store icon and a test sheet at real sizes.
Run with a Python that has Pillow:  python tools/make_app_icon.py
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
STORE = os.path.join(ROOT, 'tools/store')
FONT = os.path.join(ROOT, 'app/src/main/res/font/orbitron.ttf')

# palette of the 2 Hz Sub-Pulse tile (Lore.kt)
LIGHT = (23, 112, 58)
DARK = (13, 77, 38)
TRACE = (201, 255, 216)
INK = (246, 255, 249)
SHADOW = (2, 24, 10)

SS = 16                      # supersampling: 1 dp = 16 px while drawing
D = 108 * SS                 # the adaptive icon canvas, 108 dp
C = D / 2


def dp(v):
    return v * SS


# the printed circuit, in dp on the 108 dp canvas; a launcher shows the middle
# 72 dp (18 to 90), so the traces run in from beyond the edge
TRACES = [
    [(0, 31), (29, 31), (37, 23), (47, 23)],
    [(61, 0), (61, 25), (68, 32), (81, 32)],
    [(108, 22), (90, 22), (85, 17)],
    [(0, 77), (25, 77), (32, 70), (43, 70)],
    [(55, 108), (55, 84), (62, 77), (75, 77)],
    [(108, 85), (85, 85), (79, 91), (79, 108)],
    [(37, 108), (37, 88)],
    [(23, 0), (23, 16), (28, 21)],
]
VIAS = [(73, 23), (91, 70), (18, 88), (49, 86)]


def background():
    img = Image.new('RGBA', (D, D))
    d = ImageDraw.Draw(img)
    # the tile gradient: light at the top left, dark at the bottom right
    for i in range(0, 2 * D, SS // 2):
        t = min(1.0, i / (2 * D))
        col = tuple(int(LIGHT[k] + (DARK[k] - LIGHT[k]) * t) for k in range(3))
        d.line([(i, 0), (0, i)], fill=col + (255,), width=SS)
    # the circuit
    layer = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    a = 70
    for pts in TRACES:
        ld.line([(dp(x), dp(y)) for x, y in pts], fill=TRACE + (a,), width=int(dp(1.5)), joint='curve')
        for x, y in (pts[0], pts[-1]):
            ld.ellipse([dp(x) - dp(0.75), dp(y) - dp(0.75), dp(x) + dp(0.75), dp(y) + dp(0.75)], fill=TRACE + (a,))
        # a pad at the end that lies inside the icon
        x, y = pts[-1]
        if 0 < x < 108 and 0 < y < 108:
            ld.ellipse([dp(x) - dp(2.4), dp(y) - dp(2.4), dp(x) + dp(2.4), dp(y) + dp(2.4)], outline=TRACE + (a,), width=int(dp(1.3)))
        x, y = pts[0]
        if 0 < x < 108 and 0 < y < 108:
            ld.ellipse([dp(x) - dp(2.4), dp(y) - dp(2.4), dp(x) + dp(2.4), dp(y) + dp(2.4)], outline=TRACE + (a,), width=int(dp(1.3)))
    for x, y in VIAS:
        ld.ellipse([dp(x) - dp(1.6), dp(y) - dp(1.6), dp(x) + dp(1.6), dp(y) + dp(1.6)], outline=TRACE + (a,), width=int(dp(1.1)))
    return Image.alpha_composite(img, layer)


def number_font(width):
    """Orbitron Black, sized so '2048' is [width] wide"""
    size = dp(40)
    while True:
        f = ImageFont.truetype(FONT, int(size))
        try:
            f.set_variation_by_axes([900])
        except Exception:
            pass
        if f.getlength('2048') <= width:
            return f
        size *= 0.98


WIDTH = 63                   # dp: the corners of the number stay inside the 66 dp safe circle


def text_mask():
    m = Image.new('L', (D, D), 0)
    ImageDraw.Draw(m).text((C, C), '2048', font=number_font(dp(WIDTH)), fill=255, anchor='mm')
    return m


def foreground():
    m = text_mask()
    img = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    # a soft dark shadow under the number, like the numbers on the tiles
    sh = m.filter(ImageFilter.GaussianBlur(dp(1.6)))
    sh = Image.eval(sh, lambda v: int(v * 0.55))
    shadow = Image.new('RGBA', (D, D), SHADOW + (0,))
    shadow.putalpha(sh)
    img.alpha_composite(shadow, (0, int(dp(1.0))))
    ink = Image.new('RGBA', (D, D), INK + (0,))
    ink.putalpha(m)
    return Image.alpha_composite(img, ink)


def monochrome():
    img = Image.new('RGBA', (D, D), (0, 0, 0, 0))
    img.paste(Image.new('RGBA', (D, D), (255, 255, 255, 255)), (0, 0), text_mask())
    return img


def down(img, px):
    return img.resize((px, px), Image.LANCZOS)


os.makedirs(RES, exist_ok=True)
os.makedirs(STORE, exist_ok=True)
fg, bg, mono = foreground(), background(), monochrome()
down(bg, 432).save(os.path.join(RES, 'ic_launcher_background.png'))
down(fg, 432).save(os.path.join(RES, 'ic_launcher_foreground.png'))
down(mono, 432).save(os.path.join(RES, 'ic_launcher_monochrome.png'))

# what a launcher shows is the middle 72 dp
full = Image.alpha_composite(bg, fg)
crop = full.crop((int(dp(18)), int(dp(18)), int(dp(90)), int(dp(90))))

# splash: a 288 dp canvas whose icon has to fit a 192 dp circle; show the whole icon in that circle
SPLASH = 1152
inner = SPLASH * 192 // 288
disc = crop.resize((inner, inner), Image.LANCZOS)
circle = Image.new('L', (inner * 4, inner * 4), 0)
ImageDraw.Draw(circle).ellipse([0, 0, inner * 4, inner * 4], fill=255)
disc.putalpha(circle.resize((inner, inner), Image.LANCZOS))
splash = Image.new('RGBA', (SPLASH, SPLASH), (0, 0, 0, 0))
splash.alpha_composite(disc, ((SPLASH - inner) // 2, (SPLASH - inner) // 2))
splash.save(os.path.join(RES, 'splash_icon.png'))

# Play Store: full bleed, Play rounds the corners itself
crop.resize((512, 512), Image.LANCZOS).convert('RGB').save(os.path.join(STORE, 'icon-512.png'))

# test sheet: circle and squircle masks at launcher sizes, on a dark and a light wallpaper
sheet = Image.new("RGB", (900, 880), (30, 30, 30))
ImageDraw.Draw(sheet).rectangle([0, 440, 900, 880], fill=(226, 230, 228))
icon72 = crop.convert('RGBA')
for top in (0, 440):
    x = 20
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
            sheet.paste(ic, (x, top + (20 if shape == 'circle' else 30 + px)), ic)
        x += px + 20
os.makedirs(os.path.join(ROOT, 'tools/shots'), exist_ok=True)
sheet.save(os.path.join(ROOT, 'tools/shots/icon-sheet.png'))
print('icon written')
