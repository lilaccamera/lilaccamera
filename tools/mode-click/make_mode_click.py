"""Mode dial click: a short mechanical click cut from the app's own shutter sound.

Usage (macOS, from the repo root): python3 -I tools/mode-click/make_mode_click.py
Decodes app/src/main/assets/shutter.mp3 with afconvert, cuts the small click at 350 ms
(one of the two light clicks before the main clack), fades both ends, normalises it and
writes app/src/main/res/raw/mode_click.wav as 16-bit mono.
"""
import os, struct, subprocess, sys, tempfile, wave

START_MS, LENGTH_MS = 350, 24
FADE_IN_MS, FADE_OUT_MS = 1.5, 6   # short enough to keep the click's attack at about 352 ms
PEAK = 0.98

root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
src = os.path.join(root, 'app/src/main/assets/shutter.mp3')
out = os.path.join(root, 'app/src/main/res/raw/mode_click.wav')

with tempfile.TemporaryDirectory() as tmp:
    pcm = os.path.join(tmp, 'shutter.wav')
    subprocess.run(['afconvert', '-f', 'WAVE', '-d', 'LEI16', src, pcm], check=True)
    with wave.open(pcm) as w:
        rate, channels, frames = w.getframerate(), w.getnchannels(), w.getnframes()
        data = struct.unpack('<%dh' % (frames * channels), w.readframes(frames))

a, n = START_MS * rate // 1000, LENGTH_MS * rate // 1000
fade_in, fade_out = int(FADE_IN_MS * rate / 1000), int(FADE_OUT_MS * rate / 1000)
clip = []
for i in range(n):
    s = sum(data[(a + i) * channels + c] for c in range(channels)) / channels
    if i < fade_in:
        s *= i / fade_in
    if i > n - fade_out:
        s *= (n - i) / fade_out
    clip.append(s)
peak = max(abs(s) for s in clip)
assert peak > 1000, 'no click found at %d ms: check the cut point' % START_MS
clip = [round(s * PEAK * 32767 / peak) for s in clip]

os.makedirs(os.path.dirname(out), exist_ok=True)
with wave.open(out, 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(rate)
    w.writeframes(struct.pack('<%dh' % len(clip), *clip))
print('%s: %d samples at %d Hz' % (os.path.relpath(out, root), len(clip), rate), file=sys.stderr)
