#!/usr/bin/env bash
# Sirve los mockups de diseño en http://localhost:8765/demo.html (tele + celular conectados).
cd "$(dirname "$0")/.." && exec python3 -m http.server 8765 --bind 127.0.0.1 --directory design/mockup
