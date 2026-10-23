#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
for tool in ffmpeg xcodegen xcodebuild python3; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done
# Generated solid-color video and silent audio; no third-party media or production account.
ffmpeg -hide_banner -loglevel error -f lavfi -i color=c=0x17476b:s=1280x720:r=15 \
  -f lavfi -i anullsrc=r=48000:cl=stereo -t 60 -c:v libx264 -preset ultrafast \
  -profile:v baseline -pix_fmt yuv420p -c:a aac -movflags +faststart \
  -y /tmp/sozo-tvos-playback-smoke.mp4
mkdir -p /tmp/sozo-player-fixtures
cat > /tmp/sozo-player-fixtures/captions.vtt <<'CAPTIONS'
WEBVTT

00:00:00.000 --> 00:01:00.000
Local server subtitle
CAPTIONS
python3 scripts/playback-fixture-server.py > /tmp/sozo-playback-fixture.log 2>&1 &
sozo_fixture_pid=$!
trap 'kill "$sozo_fixture_pid" 2>/dev/null || true' EXIT INT TERM
sleep 1
if ! kill -0 "$sozo_fixture_pid" 2>/dev/null; then
  cat /tmp/sozo-playback-fixture.log
  exit 1
fi
xcodegen generate
set --
if [ -n "${SOZO_TV_DERIVED_DATA:-}" ]; then set -- "$@" -derivedDataPath "$SOZO_TV_DERIVED_DATA"; fi
if [ -n "${SOZO_TV_RESULT_BUNDLE:-}" ]; then set -- "$@" -resultBundlePath "$SOZO_TV_RESULT_BUNDLE"; fi
xcodebuild -project SozoTV.xcodeproj -scheme SozoTV \
  -destination "${SOZO_TV_DESTINATION:-platform=tvOS Simulator,name=Sozo Apple TV}" \
  "-only-testing:${SOZO_TV_TEST_SUITE:-SozoTVUITests/PlaybackUITests}" \
  -parallel-testing-enabled NO -maximum-concurrent-test-simulator-destinations 1 \
  -collect-test-diagnostics never "$@" test
