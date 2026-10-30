#!/bin/bash
# Same entry point on a local Mac, hosted Actions, or a dedicated build machine.
set -euo pipefail
cd "$(dirname "$0")/.."
for tool in xcodebuild xcrun swiftc xcodegen ffmpeg python3; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done
sozo_output="${SOZO_TV_OUTPUT_DIR:-$PWD/build/ci}"
mkdir -p "$sozo_output"
sozo_output="$(cd "$sozo_output" && pwd)"
if [ -e "$sozo_output/UI.xcresult" ]; then
  echo 'Use a fresh output directory; UI.xcresult already exists.' >&2; exit 1
fi
sozo_simulator=''
cleanup() {
  if [ -n "$sozo_simulator" ]; then
    xcrun simctl shutdown "$sozo_simulator" >/dev/null 2>&1 || true
    xcrun simctl delete "$sozo_simulator" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
xcodebuild -version | tee "$sozo_output/xcode.log"
python3 Tests/ReleasePipeline/check.py
scripts/check-profiles.sh 2>&1 | tee "$sozo_output/foundation.log"
python3 scripts/select-simulator.py > "$sozo_output/simulator-types.txt"
sozo_runtime="$(sed -n '1p' "$sozo_output/simulator-types.txt")"
sozo_device_type="$(sed -n '2p' "$sozo_output/simulator-types.txt")"
sozo_simulator="$(xcrun simctl create "Sozo CI $(date +%s)" "$sozo_device_type" "$sozo_runtime")"
printf '%s\n' "$sozo_simulator" > "$sozo_output/simulator-id.txt"
xcrun simctl boot "$sozo_simulator"
xcrun simctl bootstatus "$sozo_simulator" -b
SOZO_TV_TEST_SUITE=SozoTVUITests \
SOZO_TV_DESTINATION="platform=tvOS Simulator,id=$sozo_simulator" \
SOZO_TV_DERIVED_DATA="$sozo_output/DerivedData" \
SOZO_TV_RESULT_BUNDLE="$sozo_output/UI.xcresult" \
  scripts/check-playback.sh 2>&1 | tee "$sozo_output/ui.log"
xcrun xcresulttool get test-results summary --path "$sozo_output/UI.xcresult" --compact > "$sozo_output/ui-summary.json"
python3 - "$sozo_output/ui-summary.json" <<'PY'
import json, pathlib, re, sys
summary = json.load(open(sys.argv[1]))
expected = sum(len(re.findall(r'func test\w+\(', p.read_text())) for p in pathlib.Path('Tests/UI').glob('*.swift'))
assert summary['failedTests'] == 0, summary
assert summary['skippedTests'] == 0, 'Skipped tests cannot pass the CI gate'
assert summary['passedTests'] >= expected, f'Only {summary["passedTests"]}/{expected} UI tests executed'
print(f'All {summary["passedTests"]} UI tests passed without skips')
PY
xcodebuild -project SozoTV.xcodeproj -scheme SozoTV -configuration Release \
  -destination 'generic/platform=tvOS Simulator' -derivedDataPath "$sozo_output/ReleaseData" \
  build 2>&1 | tee "$sozo_output/release.log"
sozo_app="$sozo_output/ReleaseData/Build/Products/Release-appletvsimulator/SozoTV.app"
python3 scripts/verify-release.py "$sozo_app"
ditto -c -k --sequesterRsrc --keepParent "$sozo_app" "$sozo_output/SozoTV-simulator.zip"
