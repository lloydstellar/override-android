"""Builds the Play Store graphics from real phone screenshots, in the look of
the game: the dark green glass of the terminal, Orbitron headings, an amber
stroke.

  tools/shots/raw/*.png             screenshots from the phone (1080x2400)
  tools/store/screen-*.png          9:16 phone screenshots with a caption, for Play
  tools/store/feature-1024x500.png  the feature graphic

The raw screenshots come from a debug build with the demo boards (see
Demo.kt). Rendered with headless Chromium so the fonts are the app's own.
Run: python3 tools/make_store_graphics.py
"""
import os
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'tools/shots/raw')
STORE = os.path.join(ROOT, 'tools/store')
FONTS = os.path.join(ROOT, 'app/src/main/res/font')

# order is the order in the store; the first two are what most people see
SCREENS = [
    ('01-game.png', 'Slide, fuse and build a 2048-bit pulse'),
    ('02-nodes.png', 'Purge rogue code with an EMP Cell'),
    ('03-override.png', 'Overload the core of NEXUS-9'),
    ('04-sectors.png', 'Twelve sectors, each with a twist'),
    ('05-wideband.png', 'Wider matrices, dead pixels, move limits'),
    ('07-archive.png', 'Decrypt the files of the resistance'),
    ('06-home.png', 'Free. No ads, no account, no internet'),
]

BASE_CSS = f"""
@font-face {{ font-family: Orbitron; src: url(file://{FONTS}/orbitron.ttf); font-weight: 400 900; }}
@font-face {{ font-family: Mono; src: url(file://{FONTS}/share_tech_mono.ttf); }}
* {{ margin: 0; padding: 0; box-sizing: border-box; }}
html, body {{ width: 100%; height: 100%; overflow: hidden; }}
body {{
  background:
    radial-gradient(900px 640px at 80% -10%, rgba(57, 255, 106, 0.12), transparent 62%),
    radial-gradient(760px 540px at -12% 22%, rgba(255, 176, 0, 0.07), transparent 60%),
    repeating-linear-gradient(0deg, rgba(0, 0, 0, 0.22) 0 1px, transparent 1px 3px),
    #020a05;
  color: #d8ffe4;
}}
h1 {{
  font-family: Orbitron; font-weight: 800; letter-spacing: 1px;
  background: linear-gradient(180deg, #ffffff, #d8ffe4 55%, #39ff6a);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  filter: drop-shadow(0 0 10px rgba(57, 255, 106, 0.35));
}}
.stroke {{ height: 4px; background: linear-gradient(90deg, #FFB000, transparent); }}
.mono {{ font-family: Mono; color: #1fa64a; letter-spacing: 3px; }}
"""


def render(html, width, height, out):
    with tempfile.NamedTemporaryFile('w', suffix='.html', delete=False) as f:
        f.write(html)
        page = f.name
    try:
        subprocess.run([
            'chromium', '--headless=new', '--disable-gpu', '--hide-scrollbars',
            '--allow-file-access-from-files', '--force-device-scale-factor=1',
            f'--window-size={width},{height}', f'--screenshot={out}', '--virtual-time-budget=4000',
            f'file://{page}',
        ], check=True, capture_output=True)
    finally:
        os.unlink(page)
    print('wrote', os.path.relpath(out, ROOT))


def screen(name, caption, index):
    shot = os.path.join(RAW, name)
    # the app hides the status bar; skip the 140 px of the camera cut-out at the top
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{BASE_CSS}
    .cap {{ position: absolute; left: 80px; right: 80px; top: 100px; }}
    .cap h1 {{ font-size: 62px; line-height: 1.18; }}
    .cap .stroke {{ width: 180px; margin-top: 28px; }}
    .phone {{
      position: absolute; left: 50%; bottom: 0; width: 700px; height: 1440px; transform: translateX(-50%);
      overflow: hidden; border: 2px solid rgba(57, 255, 106, 0.45); border-bottom: none;
      clip-path: polygon(34px 0, 100% 0, 100% 100%, 0 100%, 0 34px);
      box-shadow: 0 0 60px rgba(57, 255, 106, 0.12);
    }}
    .phone img {{ display: block; width: 700px; margin-top: -91px; }}
    </style></head><body>
      <div class="cap"><h1>{caption}</h1><div class="stroke"></div></div>
      <div class="phone"><img src="file://{shot}"></div>
    </body></html>"""
    render(html, 1080, 1920, os.path.join(STORE, f'screen-{index:02d}.png'))


def feature():
    shot = os.path.join(RAW, '01-game.png')
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{BASE_CSS}
    .text {{ position: absolute; left: 64px; top: 112px; width: 500px; }}
    .kicker {{ font-size: 22px; letter-spacing: 8px; }}
    h1 {{ font-size: 76px; line-height: 1.0; margin-top: 10px; }}
    .stroke {{ width: 150px; margin: 22px 0 22px; }}
    .line {{ font-family: Mono; font-size: 27px; color: #d8ffe4; letter-spacing: 0.5px; line-height: 1.3; }}
    .board {{ position: absolute; right: 44px; top: 70px; width: 360px; height: 360px; overflow: hidden;
              transform: perspective(900px) rotateY(-14deg) rotateX(4deg);
              border: 2px solid rgba(57, 255, 106, 0.5); box-shadow: 0 0 50px rgba(57, 255, 106, 0.18); }}
    .board img {{ width: 393px; margin-left: -11px; margin-top: -213px; }}
    </style></head><body>
      <div class="board"><img src="file://{shot}"></div>
      <div class="text">
        <div class="mono kicker">LLOYD STELLAR</div>
        <h1>2048<br>OVERRIDE</h1>
        <div class="stroke"></div>
        <div class="line">A 2048 puzzle against<br>a rogue AI</div>
      </div>
    </body></html>"""
    render(html, 1024, 500, os.path.join(STORE, 'feature-1024x500.png'))


def main():
    os.makedirs(STORE, exist_ok=True)
    for i, (name, caption) in enumerate(SCREENS, start=1):
        screen(name, caption, i)
    feature()


if __name__ == '__main__':
    main()
