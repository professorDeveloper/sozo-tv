#!/bin/bash
# Report which release secrets and inputs are missing, by name only, before any
# macOS minutes are spent. Values are never printed.
set -uo pipefail
sozo_upload="${SOZO_TV_UPLOAD_TESTFLIGHT:-false}"
sozo_group="${SOZO_TV_TESTFLIGHT_GROUP:-}"
sozo_build="${SOZO_TV_BUILD_NUMBER:-}"
sozo_failed=0
sozo_lines=()
row() { sozo_lines+=("| $1 | \`$2\` | $3 |"); }
need() {
  if [ -n "${!1:-}" ]; then row ok "$1" "$2"; else row MISSING "$1" "$2"; sozo_failed=1; fi
}
want() {
  if [ -n "${!1:-}" ]; then row ok "$1" "$2"; else row optional "$1" "$2"; fi
}

need APPLE_TV_TEAM_ID 'Apple Developer Team ID (10 characters)'
need APPLE_TV_CERTIFICATE_P12_BASE64 'Apple Distribution certificate + private key, .p12, base64'
need APPLE_TV_CERTIFICATE_PASSWORD 'Password of that .p12'
need APPLE_TV_PROFILE_BASE64 'tvOS App Store provisioning profile for com.saikou.sozotv, base64'
for name in ASC_API_KEY_P8_BASE64 ASC_API_KEY_ID ASC_API_ISSUER_ID; do
  case "$name" in
    ASC_API_KEY_P8_BASE64) sozo_what='App Store Connect API key .p8, base64' ;;
    ASC_API_KEY_ID) sozo_what='App Store Connect API Key ID' ;;
    *) sozo_what='App Store Connect Issuer ID' ;;
  esac
  if [ "$sozo_upload" = true ]; then
    need "$name" "$sozo_what (upload)"
  else
    want "$name" "$sozo_what (automatic build number; upload)"
  fi
done
want TELEGRAM_BOT_TOKEN 'Telegram notification (repository secret)'
want TELEGRAM_CHAT_IDS 'Comma-separated Telegram chat ids (repository secret)'

if ! printf '%s' "${SOZO_TV_VERSION:-}" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  row INVALID version 'Input: three-part marketing version, e.g. 1.0.0'; sozo_failed=1
fi
if [ -n "$sozo_build" ] && [ "$sozo_build" != auto ] && ! printf '%s' "$sozo_build" | grep -Eq '^[1-9][0-9]{0,3}$'; then
  row INVALID build_number 'Input: empty/auto, or 1–9999'; sozo_failed=1
fi
if [ -n "$sozo_group" ] && [ "$sozo_upload" != true ]; then
  row INVALID upload_testflight 'Input: a TestFlight group needs upload_testflight'; sozo_failed=1
fi
if [ "${SOZO_TV_WAIT_PROCESSING:-false}" = true ] && [ "$sozo_upload" != true ]; then
  row INVALID upload_testflight 'Input: waiting for processing needs upload_testflight'; sozo_failed=1
fi

{
  echo '### Apple TV release preflight'
  echo
  echo '| State | Name | What it is |'
  echo '| --- | --- | --- |'
  printf '%s\n' "${sozo_lines[@]}"
  echo
  if [ "$sozo_failed" = 1 ]; then
    echo 'Add every MISSING secret to the apple-tv environment (Settings, Environments), fix any INVALID input and dispatch again. See apple-tv/README.md, "First TestFlight upload".'
  fi
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
if [ "$sozo_failed" = 1 ]; then
  echo '::error title=Apple TV release preflight::Required secrets or inputs are missing; see the checklist above.'
fi
exit "$sozo_failed"
