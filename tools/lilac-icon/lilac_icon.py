"""Lilac Camera icon: one geometry, emitted as Android VectorDrawables and SVG previews.

Usage: python3 lilac_icon.py <android_res_dir> <svg_out_dir>
Canvas is the 108-unit adaptive icon canvas; launchers show the middle 72 units.
"""
import math, os, re, sys

X = Y = 54.0
R = 20.0                    # lens outer radius
GR = R * 0.75               # glass radius
FS = GR * 0.8 / 26.5        # floret petal scale (petal reach 26.5 units)
K = GR / 22.0               # glint scale relative to the design sketches
BODY = (X - 24.6, Y - 18.9, 49.2, 37.8, 2.0)   # x, y, w, h, corner radius (1.3 : 1)
DOT = (34.4, 41.0, 3.0)
PETAL = 'M0,-4 C-7,-6 -10,-15 -7,-20.5 C-5,-23.5 -2,-25 0,-26.5 C2,-25 5,-23.5 7,-20.5 C10,-15 7,-6 0,-4Z'


def f(v):
    return f'{v:.3f}'.rstrip('0').rstrip('.')


def affine(pts, tx, ty, deg, s, pre_ty=0.0):
    """translate(tx,ty) rotate(deg) translate(0,pre_ty) scale(s), applied to points."""
    c, si = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    out = []
    for x, y in pts:
        x, y = x * s, y * s + pre_ty
        out.append((tx + x * c - y * si, ty + x * si + y * c))
    return out


def petal(tx, ty, deg, s, pre_ty=0.0):
    toks = re.findall(r'[MCZ]|-?\d+(?:\.\d+)?', PETAL)
    d, i = [], 0
    while i < len(toks):
        t = toks[i]
        if t == 'Z':
            d.append('Z'); i += 1; continue
        n = 1 if t == 'M' else 3
        pts = [(float(toks[i + 1 + 2 * k]), float(toks[i + 2 + 2 * k])) for k in range(n)]
        pts = affine(pts, tx, ty, deg, s, pre_ty)
        d.append(t + ' '.join(f'{f(a)},{f(b)}' for a, b in pts))
        i += 1 + 2 * n
    xs = [float(v) for v in re.findall(r'-?\d+(?:\.\d+)?', ''.join(d))]
    return ' '.join(d), (min(xs[0::2]), min(xs[1::2]), max(xs[0::2]), max(xs[1::2]))


def circle(cx, cy, r, ccw=False):
    sw = 0 if ccw else 1
    return (f'M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 1,{sw} {f(cx + r)},{f(cy)} '
            f'A{f(r)},{f(r)} 0 1,{sw} {f(cx - r)},{f(cy)} Z')


def rrect(x, y, w, h, r):
    return (f'M{f(x + r)},{f(y)} H{f(x + w - r)} A{f(r)},{f(r)} 0 0,1 {f(x + w)},{f(y + r)} '
            f'V{f(y + h - r)} A{f(r)},{f(r)} 0 0,1 {f(x + w - r)},{f(y + h)} H{f(x + r)} '
            f'A{f(r)},{f(r)} 0 0,1 {f(x)},{f(y + h - r)} V{f(y + r)} A{f(r)},{f(r)} 0 0,1 {f(x + r)},{f(y)} Z')


# Fills: '#RRGGBB' with alpha, or ('lin', x1, y1, x2, y2, stops) / ('rad', cx, cy, r, stops); stops = [(off, '#RRGGBB', alpha)]
def lin_v(y1, y2, a, b, oa=1.0, ob=1.0):
    return ('lin', X, y1, X, y2, [(0, a, oa), (1, b, ob)])


def rad_box(cx, cy, r, a, b):  # matches the sketches' radial: centre at 40 %/35 % of the box, radius 75 % of its width
    return ('rad', cx - r + 0.8 * r, cy - r + 0.7 * r, 1.5 * r, [(0, a, 1), (1, b, 1)])


def realistic():
    x, y, w, h, rr = BODY
    shapes = []
    for grow, a in ((1.8, 0.06), (1.2, 0.08), (0.6, 0.12)):  # soft body shadow; vectors have no blur
        shapes.append((rrect(x - grow, y + 1.6 - grow, w + 2 * grow, h + 2 * grow, rr + grow), '#140A28', a))
    shapes.append((rrect(x, y, w, h, rr), lin_v(y, y + h, '#FBFAFD', '#D3CCDD'), 1))
    shapes.append((circle(*DOT), rad_box(*DOT, '#B48BF5', '#6A40C6'), 1))
    shapes.append((circle(X, Y, R), lin_v(Y - R, Y + R, '#FFFFFF', '#DFDAE6'), 1))
    shapes.append((circle(X, Y, R * 0.89), lin_v(Y - R * 0.89, Y + R * 0.89, '#6C6875', '#2F2C36'), 1))
    shapes.append((circle(X, Y, GR), rad_box(X, Y, GR, '#221A30', '#08070B'), 1))
    shapes.append(('stroke', circle(X, Y, GR * 0.62), '#2C2934', 0.6))
    flo = ('lin', X - 12, Y - 12, X + 12, Y + 12, [(0, '#B59BEA', 0.42), (1, '#5E44A8', 0.42)])
    for a in (45, 135, 225, 315):
        shapes.append((petal(X, Y, a, FS)[0], flo, 1))
    gd, (x0, y0, x1, y1) = petal(X - 8 * K, Y - 8 * K, -45, 0.55 * K, 15.25 * 0.55 * K)
    shapes.append((gd, ('lin', x0, y0, x1, y1, [(0, '#E2C6FF', 1), (1, '#7550D4', 1)]), 1))
    shapes.append((circle(X + 9 * K, Y + 9 * K, 1.8 * K), '#FFFFFF', 0.8))
    return shapes


def flat(light=False):
    c = dict(body='#C9B6EE', ring='#FFFFFF', glass='#2E2150', flo=('#634F9C', '#433373'), glint='#D8C5F3') if light else \
        dict(body='#D8C5F3', ring='#F6F2FC', glass='#1E1530', flo=('#5C4894', '#38295F'), glint='#C9B6EE')
    x, y, w, h, rr = BODY
    shapes = [(rrect(x, y, w, h, rr), c['body'], 1), (circle(*DOT), '#7A5DB6', 1),
              (circle(X, Y, R), c['ring'], 1), (circle(X, Y, R * 0.89), '#6E5A99', 1), (circle(X, Y, GR), c['glass'], 1)]
    flo = ('lin', X - 12, Y - 12, X + 12, Y + 12, [(0, c['flo'][0], 1), (1, c['flo'][1], 1)])
    for a in (45, 135, 225, 315):
        shapes.append((petal(X, Y, a, FS)[0], flo, 1))
    shapes.append((petal(X - 8 * K, Y - 8 * K, -45, 0.55 * K, 15.25 * 0.55 * K)[0], c['glint'], 1))
    return shapes


def themed(fg='#FFFFFF'):
    x, y, w, h, rr = BODY
    # Non-zero winding: body and lens wind +1 each where they overlap, so the glass is cut twice; the dot sits on the body only.
    sil = ' '.join([rrect(x, y, w, h, rr), circle(X, Y, R), circle(X, Y, GR, True), circle(X, Y, GR, True),
                    circle(*DOT, ccw=True)])
    shapes = [(sil, lin_v(Y - 21, Y + 21, fg, fg, 1, 0.62), 1)]
    for a in (45, 135, 225):
        shapes.append((petal(X, Y, a, FS)[0], fg, 0.38))
    tip, base = 12 * math.sqrt(0.5), 1.8 * math.sqrt(0.5)
    shapes.append((petal(X, Y, 315, FS)[0], ('lin', X - tip, Y - tip, X - base, Y - base, [(0, fg, 1), (1, fg, 0.38)]), 1))
    return shapes


def argb(col, a):
    return '#' + f'{round(a * 255):02X}' + col[1:].upper()


def vd(shapes, comment, crop=None):
    x0, y0, w, h = crop or (0, 0, 108, 108)
    out = [f'<!-- {comment} Generated by the Lilac icon script (see vault note "Lilac Camera Icon Design"). -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           f'    android:width="{f(w)}dp"', f'    android:height="{f(h)}dp"',
           f'    android:viewportWidth="{f(w)}"', f'    android:viewportHeight="{f(h)}">']
    if crop:
        out.append(f'<group android:translateX="{f(-x0)}" android:translateY="{f(-y0)}">')
    for s in shapes:
        if s[0] == 'stroke':
            _, d, col, wdt = s
            out.append(f'  <path android:pathData="{d}" android:strokeColor="{col}" android:strokeWidth="{wdt}"/>')
            continue
        d, fill, alpha = s
        if isinstance(fill, str):
            out.append(f'  <path android:pathData="{d}" android:fillColor="{argb(fill, alpha)}"/>')
            continue
        if fill[0] == 'lin':
            _, x1, y1, x2, y2, stops = fill
            g = f'android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" android:endX="{f(x2)}" android:endY="{f(y2)}"'
        else:
            _, cx, cy, r, stops = fill
            g = f'android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" android:gradientRadius="{f(r)}"'
        items = ''.join(f'<item android:offset="{o}" android:color="{argb(c, a)}"/>' for o, c, a in stops)
        out.append(f'  <path android:pathData="{d}">\n    <aapt:attr name="android:fillColor">'
                   f'<gradient {g}>{items}</gradient></aapt:attr>\n  </path>')
    if crop:
        out.append('</group>')
    out.append('</vector>')
    return '\n'.join(out) + '\n'


def svg(shapes, bg):
    defs, body = [], []
    for i, s in enumerate(shapes):
        if s[0] == 'stroke':
            _, d, col, wdt = s
            body.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{wdt}"/>')
            continue
        d, fill, alpha = s
        if isinstance(fill, str):
            body.append(f'<path d="{d}" fill="{fill}" fill-opacity="{alpha}"/>')
            continue
        stops = ''.join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in fill[-1])
        if fill[0] == 'lin':
            _, x1, y1, x2, y2, _s = fill
            defs.append(f'<linearGradient id="g{i}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">{stops}</linearGradient>')
        else:
            _, cx, cy, r, _s = fill
            defs.append(f'<radialGradient id="g{i}" gradientUnits="userSpaceOnUse" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}">{stops}</radialGradient>')
        body.append(f'<path d="{d}" fill="url(#g{i})"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="288" height="288">'
            f'<defs>{"".join(defs)}</defs><rect width="108" height="108" fill="{bg}"/>{"".join(body)}</svg>\n')


if __name__ == '__main__':
    res, svg_dir = sys.argv[1], sys.argv[2]
    assert abs(BODY[2] / BODY[3] - 1.3) < 0.01, 'body must stay 1.3 : 1'
    corner = math.hypot(BODY[2] / 2, BODY[3] / 2)
    assert corner < 33, f'body corner {corner:.1f} leaves the 66-unit safe zone'
    assert DOT[0] + DOT[2] < X - math.sqrt(R ** 2 - (DOT[1] - Y) ** 2), 'flash dot overlaps the lens'
    with open(os.path.join(res, 'drawable/ic_launcher_foreground.xml'), 'w') as fh:
        fh.write(vd(realistic(), 'Lilac Camera icon, realistic style.'))
    with open(os.path.join(res, 'drawable/ic_launcher_monochrome.xml'), 'w') as fh:
        fh.write(vd(themed(), 'Themed icon layer: launchers keep only the alpha, so depth comes from transparency.'))
    # Frame editor "Lilac Camera" logo (key "photon"): the one-colour mark, cropped to the camera.
    mark = (BODY[0], Y - R, BODY[2], 2 * R)
    for name, col in (('ic_photon.xml', '#000000'), ('ic_photon_light.xml', '#FFFFFF')):
        with open(os.path.join(res, 'drawable', name), 'w') as fh:
            fh.write(vd(themed(col), 'Lilac Camera frame logo.', mark))
    # Launcher background: light lilac in light theme, deep purple in dark theme.
    for folder, col in (('values', '#F6F2FC'), ('values-night', '#3B2A66')):
        with open(os.path.join(res, folder, 'ic_launcher_background.xml'), 'w') as fh:
            fh.write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{col}</color>\n</resources>\n')
    print(f'frame logo aspect {mark[2] / mark[3]:.3f} : 1')
    for name, shapes, bg in [('Realistic', realistic(), '#3B2A66'), ('Realistic Light', realistic(), '#F6F2FC'),
                             ('Flat', flat(), '#3B2A66'), ('Flat Light', flat(True), '#F6F2FC'),
                             ('Themed', themed('#21005D'), '#EADDFF')]:
        with open(os.path.join(svg_dir, f'Lilac Icon {name}.svg'), 'w') as fh:
            fh.write(svg(shapes, bg))
    print(f'ok: body corner at {corner:.1f} of 36 visible, files written')
