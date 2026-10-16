# Screenshots

Each capture comes as a **pair**: the same screen, once with the app in light
theme and once in dark theme. The site shows the one matching its own theme and
swaps instantly when the theme changes.

Until a file exists, the site shows a neutral placeholder in its place instead
of a broken image, so nothing looks wrong while the set is incomplete.

| File | Screen |
|:-----|:-------|
| `home-light.png` / `home-dark.png` | Mobile home screen, blocking enabled, with the counters visible |
| `lists-light.png` / `lists-dark.png` | Hosts sources, with a few sources listed |
| `dnslog-light.png` / `dnslog-dark.png` | DNS log with a few recorded requests |
| `tv-light.png` / `tv-dark.png` | Android TV home screen |

## Format

These are **device mockups**: the capture already sits inside a rendered phone
or TV frame, on a transparent background. The page therefore draws no frame of
its own, and the image sets its own height, so a re-export at another size or
aspect ratio still lays out correctly.

Two things to keep when replacing them:

- **Transparency.** The rounded corners rely on the alpha channel. Flattening it
  onto a background leaves visible square corners, on the light theme in
  particular.
- **Both themes from the same state.** Change only the theme between the two
  captures of a pair, so the swap does not jump.

## Weight

Phone frames render at roughly 270 px wide on a desktop and 390 px on a phone,
so around 800 px of source is already more than enough. The committed files are
resized to that and reduced to a 256 colour palette, which suits flat app UI
with no visible loss and takes the whole set from 4.5 MB to 0.4 MB. Worth
redoing after replacing them:

```bash
python3 - <<'PY'
from PIL import Image
import glob
for f in glob.glob('*.png'):
    im = Image.open(f)                      # keep RGBA, never convert('RGB')
    w = 1400 if f.startswith('tv') else 800
    if im.width > w:
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    im.quantize(colors=256, method=Image.FASTOCTREE).save(f, 'PNG', optimize=True)
PY
```
