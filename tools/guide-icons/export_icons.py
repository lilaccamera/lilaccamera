#!/usr/bin/env python3
"""Writes Lilac's own icons from AppIcons.kt as SVG files for the user guide.

AppIcons.kt also holds PhotonCamera's icons (mostly filled Material symbols). Every icon whose
name already exists in upstream PhotonCamera's AppIcons.kt is skipped, so the guide only shows
icons Lilac's screens draw.

Usage: python3 tools/guide-icons/export_icons.py   (from the repo root; needs git history)
Output: guide/icons/<Name>.svg (the folder is rewritten), black on transparent; the site's CSS
inverts them in dark mode. Rerun it after changing AppIcons.kt.
"""
import pathlib, re, subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
REL = "app/src/main/java/com/hinnka/mycamera/ui/icons/AppIcons.kt"
UPSTREAM = "2d9e0812"  # the last upstream PhotonCamera commit merged into this fork
OUT = ROOT / "guide/icons"

src = (ROOT / REL).read_text()
upstream_src = subprocess.run(["git", "-C", str(ROOT), "show", f"{UPSTREAM}:{REL}"],
                              check=True, capture_output=True, text=True).stdout
upstream = set(re.findall(r'"AppIcons\.(\w+)"', upstream_src))

# Named path constants such as BoltPath or Slash, possibly built from several string literals.
consts = {m.group(1): "".join(re.findall(r'"([^"]*)"', m.group(2)))
          for m in re.finditer(r'private const val (\w+) =\s*((?:"[^"]*"\s*\+?\s*)+)', src)}

def path_expr(expr):
    """Joins string literals and named constants of a Kotlin `"..." + Name` expression."""
    return "".join(consts.get(tok, "") if not tok.startswith('"') else tok[1:-1]
                   for tok in re.findall(r'"[^"]*"|\b[A-Z]\w*\b', expr))

STROKE = 'fill="none" stroke="#000" stroke-linecap="round" stroke-linejoin="round"'
icons = {}
# Line icons: lineIcon("AppIcons.Name", <expr>) on a 24-unit grid with a 2-unit round stroke.
for m in re.finditer(r'lineIcon\(\s*"AppIcons\.(\w+)",\s*(.*?)\)\s*(?:\.also|\}|\n\s*val|\n\s*//)', src, re.S):
    d = path_expr(m.group(2))
    if d:
        icons[m.group(1)] = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="{d}" '
                             f'{STROKE} stroke-width="2"/></svg>')
# Icons built by hand with ImageVector.Builder: one <path> per addPath, stroked or filled.
for m in re.finditer(r'name = "AppIcons\.(\w+)",(.*?)\.build\(\)', src, re.S):
    name, body = m.groups()
    if name in icons:
        continue
    w = re.search(r'viewportWidth = ([\d.]+)f', body); h = re.search(r'viewportHeight = ([\d.]+)f', body)
    ty = re.search(r'translationY = (-?[\d.]+)f', body)
    paths = []
    for p in re.finditer(r'addPath\((.*?)\n\s*\)', body, re.S):
        d = re.search(r'addPathNodes\("([^"]+)"\)', p.group(1))
        if not d:
            continue
        if "stroke = " in p.group(1):
            sw = re.search(r'strokeLineWidth = ([\d.]+)f(?:\s*\*\s*([\d.]+)f\s*/\s*([\d.]+)f)?', p.group(1))
            width = 1.0
            if sw:
                width = float(sw.group(1)) * (float(sw.group(2)) / float(sw.group(3)) if sw.group(2) else 1)
            paths.append(f'<path d="{d.group(1)}" {STROKE} stroke-width="{width:g}"/>')
        else:
            paths.append(f'<path d="{d.group(1)}"/>')
    if w and h and paths:
        g = f' transform="translate(0 {ty.group(1)})"' if ty else ""
        icons[name] = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w.group(1)} {h.group(1)}">'
                       f'<g{g}>' + "".join(paths) + "</g></svg>")

lilac = {n: svg for n, svg in icons.items() if n not in upstream}
OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("*.svg"):
    old.unlink()
for name, svg in lilac.items():
    ET.fromstring(svg)  # self-check: every file is well-formed XML
    (OUT / f"{name}.svg").write_text(svg + "\n")
for name in ("Sliders", "Film", "MoonStars", "User", "MovieCamera", "TurningArrows", "ThermometerReading"):
    assert name in lilac, f"{name} not exported"
assert "Layers" not in lilac and "CameraAlt" not in lilac, "an upstream icon was exported"
print(f"{len(lilac)} Lilac icons written to {OUT.relative_to(ROOT)} ({len(icons) - len(lilac)} upstream skipped)")
