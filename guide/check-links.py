#!/usr/bin/env python3
"""Fail if a page of the built site (default: site/) links to a missing page or anchor.

The strict build checks Markdown links, but not links inside raw HTML blocks.
"""
import os, re, sys

root = sys.argv[1] if len(sys.argv) > 1 else "site"
bad = []
for d, _, files in os.walk(root):
    for f in files:
        if not f.endswith(".html") or f == "404.html":  # 404.html uses absolute links on purpose
            continue
        page = os.path.join(d, f)
        for link in re.findall(r'(?:href|src)="([^"]+)"', open(page).read()):
            if link.startswith(("http:", "https:", "#", "mailto:", "data:")):
                continue
            path, _, frag = link.partition("#")
            target = os.path.normpath(os.path.join(d, path.split("?")[0]))
            if os.path.isdir(target):
                target = os.path.join(target, "index.html")
            if not os.path.exists(target):
                bad.append(f"{page}: {link}")
            elif frag and target.endswith(".html") and f'id="{frag}"' not in open(target).read():
                bad.append(f"{page}: {link} (no such anchor)")
print("\n".join(bad) or "links ok")
sys.exit(1 if bad else 0)
