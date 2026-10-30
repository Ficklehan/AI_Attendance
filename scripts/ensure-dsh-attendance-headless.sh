#!/usr/bin/env bash
# attendance profile 必须能跑 headless one-shot，Java 才送得进图。
set -euo pipefail
PROFILE="${DSH_HOME:-$HOME/.dsh}/profiles/attendance"
PKG="$PROFILE/package.json"
if [ ! -f "$PKG" ]; then
  echo "missing $PKG — run: npx -y @deepseek-ai/dsh@alpha plugin --profile attendance add dsh-ocr-local" >&2
  exit 1
fi
python3 - "$PKG" <<'PY'
import json, sys
path = sys.argv[1]
with open(path) as f:
    data = json.load(f)
bundles = data.setdefault("dsh", {}).setdefault("profile", {}).setdefault("bundles", [])
need = "@deepseek-ai/dsh-headless"
if need not in bundles:
    if "@deepseek-ai/dsh-base" in bundles:
        i = bundles.index("@deepseek-ai/dsh-base") + 1
        bundles.insert(i, need)
    else:
        bundles.insert(0, need)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("added", need, "to", path)
else:
    print(need, "already in", path)
PY
TILER_DIR="$PROFILE/node_modules/dsh-v4flash-tiler"
if [ ! -d "$TILER_DIR" ]; then
  if npx -y @deepseek-ai/dsh@alpha plugin --profile attendance add dsh-v4flash-tiler; then
    echo "added dsh-v4flash-tiler"
  else
    echo "WARN: dsh-v4flash-tiler skipped (skill still calls tiler_route when present)" >&2
  fi
else
  echo "dsh-v4flash-tiler already installed"
fi
