#!/bin/bash
# Fails when a guide page contains planning shorthand a reader of the guide
# cannot follow: wiki links ([[...]]), IDs such as T12, T4b or L5, or the
# words listed in the second pattern.
# Usage: guide/check-text.sh [dir]   (default: guide)
dir="${1:-guide}"
found=0
grep -rnE --include='*.md' '\[\[|\bT[0-9]+[a-z]?\b|\bL[0-9]{1,2}\b' "$dir" && found=1
grep -rniE --include='*.md' 'vault|obsidian|claude|work plan|owner' "$dir" && found=1
exit $found
