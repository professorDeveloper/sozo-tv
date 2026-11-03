#!/bin/bash
# Manual App Store distribution signing. Upload is off unless explicitly enabled.
# SOZO_TV_BUILD_NUMBER empty or "auto": latest App Store Connect build + 1, else
# SOZO_TV_BUILD_NUMBER_FALLBACK (CI passes github.run_number).
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
: "${APPLE_TV_TEAM_ID:?Set APPLE_TV_TEAM_ID}"
: "${APPLE_TV_CERTIFICATE_P12_BASE64:?Set APPLE_TV_CERTIFICATE_P12_BASE64}"
: "${APPLE_TV_CERTIFICATE_PASSWORD:?Set APPLE_TV_CERTIFICATE_PASSWORD}"
: "${APPLE_TV_PROFILE_BASE64:?Set APPLE_TV_PROFILE_BASE64}"
: "${SOZO_TV_VERSION:?Set SOZO_TV_VERSION}"
: "${SOZO_TV_BUILD_NUMBER:=auto}"
export SOZO_TV_BUILD_NUMBER
sozo_upload="${SOZO_TV_UPLOAD_TESTFLIGHT:-false}"
sozo_wait="${SOZO_TV_WAIT_PROCESSING:-false}"
sozo_group="${SOZO_TV_TESTFLIGHT_GROUP:-}"
for flag in "$sozo_upload" "$sozo_wait"; do
  case "$flag" in true|false) ;; *) echo 'Upload and wait flags must be true or false' >&2; exit 1 ;; esac
done
if [ -n "$sozo_group" ]; then sozo_wait=true; fi
if [ "$sozo_wait" = true ] && [ "$sozo_upload" != true ]; then
  echo 'Waiting for processing or adding to a group needs SOZO_TV_UPLOAD_TESTFLIGHT=true' >&2; exit 1
fi
sozo_has_asc=false
if [ -n "${ASC_API_KEY_P8_BASE64:-}" ] && [ -n "${ASC_API_KEY_ID:-}" ] && [ -n "${ASC_API_ISSUER_ID:-}" ]; then
  sozo_has_asc=true
fi
python3 - <<'PY'
import os, re
assert re.fullmatch(r'[A-Z0-9]{10}', os.environ['APPLE_TV_TEAM_ID']), 'Invalid team ID'
assert re.fullmatch(r'\d+\.\d+\.\d+', os.environ['SOZO_TV_VERSION']), 'Use a three-part marketing version'
assert re.fullmatch(r'auto|[1-9]\d{0,3}', os.environ['SOZO_TV_BUILD_NUMBER']), 'Use build number 1–9999 or auto'
PY
if [ "$sozo_upload" = true ]; then
  : "${ASC_API_KEY_P8_BASE64:?Set ASC_API_KEY_P8_BASE64 for TestFlight upload}"
  : "${ASC_API_KEY_ID:?Set ASC_API_KEY_ID for TestFlight upload}"
  : "${ASC_API_ISSUER_ID:?Set ASC_API_ISSUER_ID for TestFlight upload}"
fi
for tool in xcodegen xcodebuild python3 security codesign openssl; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done
sozo_output="${SOZO_TV_RELEASE_DIR:-$PWD/build/release}"
mkdir -p "$sozo_output"
sozo_output="$(cd "$sozo_output" && pwd)"
if [ -e "$sozo_output/SozoTV.xcarchive" ] || [ -e "$sozo_output/export" ]; then
  echo 'Use a fresh release output directory.' >&2; exit 1
fi
sozo_secret_dir="$(mktemp -d "${TMPDIR:-/tmp}/sozo-tvos-signing.XXXXXX")"
sozo_keychain="$sozo_secret_dir/signing.keychain-db"
sozo_profile_path=''
sozo_search_changed=false
sozo_original_keychains=()
while IFS= read -r line; do
  sozo_original_keychains+=("$(printf '%s' "$line" | sed -E 's/^[[:space:]]*"(.*)"[[:space:]]*$/\1/')")
done < <(security list-keychains -d user)
cleanup() {
  if [ "$sozo_search_changed" = true ]; then
    security list-keychains -d user -s "${sozo_original_keychains[@]}" >/dev/null 2>&1 || true
  fi
  security delete-keychain "$sozo_keychain" >/dev/null 2>&1 || true
  if [ -n "$sozo_profile_path" ]; then rm -f "$sozo_profile_path"; fi
  rm -rf "$sozo_secret_dir"
}
trap cleanup EXIT
export SOZO_TV_SECRET_DIR="$sozo_secret_dir"
python3 - <<'PY'
import base64, os, pathlib
root = pathlib.Path(os.environ['SOZO_TV_SECRET_DIR'])
for env, name in [('APPLE_TV_CERTIFICATE_P12_BASE64', 'certificate.p12'),
                  ('APPLE_TV_PROFILE_BASE64', 'profile.mobileprovision')]:
    (root / name).write_bytes(base64.b64decode(''.join(os.environ[env].split()), validate=True))
if os.environ.get('ASC_API_KEY_P8_BASE64'):
    (root / 'AuthKey.p8').write_bytes(base64.b64decode(''.join(os.environ['ASC_API_KEY_P8_BASE64'].split()), validate=True))
PY
export ASC_API_KEY_PATH="$sozo_secret_dir/AuthKey.p8"
if [ "$SOZO_TV_BUILD_NUMBER" = auto ]; then
  sozo_resolved=''
  if [ "$sozo_has_asc" = true ]; then
    sozo_resolved="$(python3 scripts/asc.py next-build)" || sozo_resolved=''
  fi
  if [ -z "$sozo_resolved" ]; then
    : "${SOZO_TV_BUILD_NUMBER_FALLBACK:?Could not read the latest build from App Store Connect; set SOZO_TV_BUILD_NUMBER}"
    echo "App Store Connect query unavailable; falling back to build number $SOZO_TV_BUILD_NUMBER_FALLBACK" >&2
    sozo_resolved="$SOZO_TV_BUILD_NUMBER_FALLBACK"
  fi
  SOZO_TV_BUILD_NUMBER="$sozo_resolved"
  if ! printf '%s' "$SOZO_TV_BUILD_NUMBER" | grep -Eq '^[1-9][0-9]{0,3}$'; then
    echo "Resolved build number $SOZO_TV_BUILD_NUMBER is outside 1–9999" >&2; exit 1
  fi
fi
echo "Build number: $SOZO_TV_BUILD_NUMBER"
printf '%s\n' "$SOZO_TV_BUILD_NUMBER" > "$sozo_output/build-number.txt"
if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "build_number=$SOZO_TV_BUILD_NUMBER" >> "$GITHUB_OUTPUT"; fi
security cms -D -i "$sozo_secret_dir/profile.mobileprovision" > "$sozo_secret_dir/profile.plist"
python3 scripts/prepare-release.py "$sozo_secret_dir/profile.plist" "$sozo_secret_dir/ExportOptions.plist" > "$sozo_secret_dir/profile-uuid.txt"
sozo_profile_uuid="$(cat "$sozo_secret_dir/profile-uuid.txt")"
sozo_profiles_dir="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
mkdir -p "$sozo_profiles_dir"
if [ -e "$sozo_profiles_dir/$sozo_profile_uuid.mobileprovision" ]; then
  echo 'Profile UUID already exists locally. Use a dedicated release runner.' >&2; exit 1
fi
sozo_profile_path="$sozo_profiles_dir/$sozo_profile_uuid.mobileprovision"
cp "$sozo_secret_dir/profile.mobileprovision" "$sozo_profile_path"
sozo_keychain_password="$(openssl rand -hex 32)"
security create-keychain -p "$sozo_keychain_password" "$sozo_keychain"
security set-keychain-settings -lut 3600 "$sozo_keychain"
security unlock-keychain -p "$sozo_keychain_password" "$sozo_keychain"
security import "$sozo_secret_dir/certificate.p12" -k "$sozo_keychain" \
  -P "$APPLE_TV_CERTIFICATE_PASSWORD" -T /usr/bin/codesign -T /usr/bin/security >/dev/null
security set-key-partition-list -S apple-tool:,apple:,codesign: -s \
  -k "$sozo_keychain_password" "$sozo_keychain" >/dev/null
sozo_search_changed=true
security list-keychains -d user -s "$sozo_keychain" "${sozo_original_keychains[@]}"
xcodegen generate
xcodebuild -project SozoTV.xcodeproj -scheme SozoTV -configuration Release \
  -destination 'generic/platform=tvOS' -archivePath "$sozo_output/SozoTV.xcarchive" \
  DEVELOPMENT_TEAM="$APPLE_TV_TEAM_ID" CODE_SIGN_STYLE=Manual \
  CODE_SIGN_IDENTITY='Apple Distribution' PROVISIONING_PROFILE_SPECIFIER="$sozo_profile_uuid" \
  MARKETING_VERSION="$SOZO_TV_VERSION" CURRENT_PROJECT_VERSION="$SOZO_TV_BUILD_NUMBER" \
  archive 2>&1 | tee "$sozo_output/archive.log"
python3 scripts/verify-release.py "$sozo_output/SozoTV.xcarchive/Products/Applications/SozoTV.app"
sozo_auth=()
if [ "$sozo_upload" = true ]; then
  sozo_auth=(-authenticationKeyPath "$sozo_secret_dir/AuthKey.p8" \
    -authenticationKeyID "$ASC_API_KEY_ID" -authenticationKeyIssuerID "$ASC_API_ISSUER_ID")
fi
xcodebuild -exportArchive -archivePath "$sozo_output/SozoTV.xcarchive" \
  -exportOptionsPlist "$sozo_secret_dir/ExportOptions.plist" \
  -exportPath "$sozo_output/export" "${sozo_auth[@]}" 2>&1 | tee "$sozo_output/export.log"
if [ "$sozo_upload" = true ] && [ -n "${GITHUB_OUTPUT:-}" ]; then echo 'uploaded=true' >> "$GITHUB_OUTPUT"; fi
echo "Release complete: $SOZO_TV_VERSION ($SOZO_TV_BUILD_NUMBER), upload=$sozo_upload"
if [ "$sozo_wait" = true ]; then
  sozo_build_id="$(python3 scripts/asc.py wait "$SOZO_TV_VERSION" "$SOZO_TV_BUILD_NUMBER" "${SOZO_TV_WAIT_MINUTES:-45}")"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then echo 'processed=true' >> "$GITHUB_OUTPUT"; fi
  if [ -n "$sozo_group" ]; then
    python3 scripts/asc.py add-to-group "$sozo_build_id" "$sozo_group"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then echo 'grouped=true' >> "$GITHUB_OUTPUT"; fi
  fi
fi
