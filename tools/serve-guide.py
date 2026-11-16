#!/usr/bin/env python3
"""Preview the built guide (site/) on this machine's Tailscale address, or the address given.

    python3 tools/serve-guide.py            # http://<tailscale ip>:8766/
    python3 tools/serve-guide.py 127.0.0.1  # this machine only

Responses are never cached, so a page loaded while `zensical build` rewrites site/
cannot leave a half-written file in a viewer's cache.
"""
import functools, http.server, pathlib, subprocess, sys


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


host = sys.argv[1] if len(sys.argv) > 1 else subprocess.check_output(["tailscale", "ip", "-4"], text=True).split()[0]
site = pathlib.Path(__file__).resolve().parent.parent / "site"
handler = functools.partial(NoCache, directory=str(site))
print(f"Serving {site} at http://{host}:8766/", flush=True)
http.server.ThreadingHTTPServer((host, 8766), handler).serve_forever()
