#!/bin/bash
# Pin Xcode on a hosted runner. "26.1" (or "26.1.1") must exist exactly; a bare
# major such as "26" takes the newest installed 26.x, so an image update can
# never silently move the build to the next major.
set -euo pipefail
sozo_wanted="${1:?Usage: select-xcode.sh <version|major>}"
if ! printf '%s' "$sozo_wanted" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$'; then
  echo "Invalid Xcode version: $sozo_wanted" >&2; exit 1
fi
sozo_root="${SOZO_TV_XCODE_ROOT:-/Applications}"
sozo_available="$(find "$sozo_root" -maxdepth 1 -name 'Xcode_*.app' | sed -E 's|.*/Xcode_(.*)\.app$|\1|' | grep -E '^[0-9.]+$' | sort -t. -k1,1n -k2,2n -k3,3n || true)"
if [[ "$sozo_wanted" == *.* ]]; then
  sozo_pick="$(printf '%s\n' "$sozo_available" | grep -Fx "$sozo_wanted" || true)"
else
  sozo_pick="$(printf '%s\n' "$sozo_available" | grep -E "^${sozo_wanted}(\.|$)" | tail -1 || true)"
fi
if [ -z "$sozo_pick" ]; then
  echo "::error title=Xcode $sozo_wanted not installed::Available on this runner: $(printf '%s' "$sozo_available" | tr '\n' ' ')" >&2
  exit 1
fi
sozo_dir="$sozo_root/Xcode_${sozo_pick}.app/Contents/Developer"
echo "DEVELOPER_DIR=$sozo_dir" >> "${GITHUB_ENV:-/dev/null}"
DEVELOPER_DIR="$sozo_dir" xcodebuild -version
