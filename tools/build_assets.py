#!/usr/bin/env python3
"""Turn web/index.html into the offline copy bundled in the APK (local fonts and libraries)."""
import pathlib, re
root = pathlib.Path(__file__).resolve().parent.parent
s = (root / "web/index.html").read_text(encoding="utf-8")
s, n1 = re.subn(r'<link rel="preconnect"[^>]*>\s*<link href="https://fonts\.googleapis\.com[^>]*>', '<link rel="stylesheet" href="fonts/fonts.css">', s)
s, n2 = re.subn(r'https://cdnjs\.cloudflare\.com/ajax/libs/jspdf/[^"]+', 'lib/jspdf.umd.min.js', s)
s, n3 = re.subn(r'https://cdnjs\.cloudflare\.com/ajax/libs/xlsx/[^"]+', 'lib/xlsx.full.min.js', s)
assert (n1, n2, n3) == (1, 1, 1), (n1, n2, n3)
assert "https://" not in s.split("<style>")[0], "remote resource left in <head>"
(root / "app/src/main/assets/index.html").write_text(s, encoding="utf-8")
print("assets/index.html written")
