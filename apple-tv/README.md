# Sozo TV for Apple TV

Native tvOS client built with SwiftUI and AVKit. It deliberately consumes only the Sozo REST
content contract; Android-only CloudStream/Aniyomi binaries and downloaded executable code are
not loaded on Apple TV.

Generate/open the project:

```sh
xcodegen generate
open SozoTV.xcodeproj
```

Set a Development Team for device builds. Simulator builds use ad-hoc signing, including the application identifier needed by Keychain.
Do not set `CODE_SIGNING_ALLOWED=NO`: that launches but breaks real credential storage.

## Profiles and Liquid Glass

The native profile hub includes household profile creation/editing/deletion, bundled avatars,
PIN entry with a remote-friendly keypad, kids restrictions, account name editing, library,
watch history, recent activity, notifications, friends, linked-device management, source
selection, support tickets and appearance/playback settings. Requests carry the selected
profile; credentials use Keychain with refresh-token rotation. Profile/account changes
invalidate in-flight requests. Source and playback preferences are isolated by account and
profile. Playback records actual progress and can resume supported history entries.

Liquid Glass uses the native SwiftUI API on tvOS 26 and later. Older versions and Reduce
Transparency use solid surfaces; Reduce Motion disables focus scaling. Focus decoration
uses a short transition without continuously animating full-screen blur.

Apple TV uses server-provided sources and AVKit. Android CloudStream/Aniyomi extensions,
Android offline downloads and mpv shaders are not portable to this client. AniList/MAL
linking remains on mobile; this screen explains that rather than copying mobile credentials.
A quality limit selects available resolution; it does not upscale an SD source.

## Verification

Install FFmpeg and xcodegen, then run the full suite on an installed tvOS simulator. The script generates isolated media and starts a loopback range server for the detail-to-player integration:

```sh
SOZO_TV_TEST_SUITE=SozoTVUITests \
SOZO_TV_DESTINATION='platform=tvOS Simulator,name=Sozo Apple TV' \
  scripts/check-playback.sh
```

Profile UI tests launch with `--profile-ui-fixture`; catalog tests use `--browse-ui-fixture`. This DEBUG-only path uses an in-memory account
and URLProtocol responses; it cannot send requests to production. The tests drive the Siri
Remote focus system. Release builds do not include the fixture implementation.

Run `scripts/check-profiles.sh` for the Foundation checks covering session transport,
profile settings, history payloads, provider envelopes, playable-source/quality policy and request cancellation. Their sources live in `Tests/`. A physical Apple TV/TestFlight build requires a developer signing team and separate
on-device verification; a simulator pass is not device certification.

### Remote PIN controls

The numeric keypad keeps a fixed 3-column layout, starts on 1 and restores focus after
invalid input. Erase and cancel remain reachable even with an empty PIN; submitting fewer
than four digits is a no-op. Existing 4–8 digit PINs remain supported. Play/Pause submits
without moving to the confirmation button; Back/Menu cancels and returns focus to the
profile that opened the dialog. Creating and confirming a new four-digit PIN uses the same
keypad rather than the system text keyboard. Controls stay in place while checking the PIN.
The confirmation button is centered below zero, keeping the vertical focus path unambiguous.

Secure sign-in regression tests use the real simulator Keychain in an isolated service.
They cover save/read/rotation/delete and the complete pairing-to-profiles transition, including
relaunch persistence. Never substitute the in-memory profile fixture for these tests.
Native interface copy is English; controls use neutral white/silver focus styling.

## Catalog and native playback

Home includes source selection, featured titles and supported Continue Watching entries.
Search debounces remote text entry, discards stale requests and paginates without duplicate
cards. Detail pages paginate episodes, preserve fractional episode labels and filter out
iframe pages, DASH manifests and other formats AVKit cannot open. Direct movie source
headers survive the episode endpoint. Remembered source choices and maximum quality are
scoped to the active profile; HLS adapts within the configured cap.

Artwork is downsampled off the main thread with bounded memory/disk caches and stable
placeholder geometry. Native glass containers group nearby controls; content panels remain
readable solid surfaces. Siri Remote tests cover navigation between the header/grid, search
pagination, profile management, PINs, notifications, closed support conversations and settings
isolation. Avatars load their bundled PNGs as original images. App Icon and Top Shelf use
native layered/static branded assets.

For the actual AVKit integration checks, install FFmpeg and run:

```sh
SOZO_TV_DESTINATION='platform=tvOS Simulator,name=Sozo Apple TV' scripts/check-playback.sh
```

This generates a local 720p H.264/AAC clip and verifies resume, automatic-start preferences
and remote Play/Pause using the production player wrapper. The isolated playback probe is
DEBUG-only and cannot contact production. Integration tests explicitly skip if the generated
clip is absent. A generated clip verifies the player, not availability or codec compatibility
of every external provider. The External subtitles menu loads WebVTT/SRT tracks with the source's
allowed playback headers. Captions follow seeking and can be switched off; embedded
subtitle/audio menus remain available. Unsupported or unavailable subtitle files show a retryable
error without stopping the video.

## Source catalog and featured banners

The Home source control and profile Sources destination open the same full-screen catalog.
Search, category and language filters run locally. Duplicate/empty source IDs and phone-only
providers are excluded from selectable cards. Refresh reloads metadata. Selecting a source
checks its Home endpoint before saving the profile preference; an error keeps the previous
choice and explains how to retry. A successful catalog check is not a guarantee that every
external video is playable. Source changes are synchronized again when Home appears. New profiles prefer VidAPI when available; an explicitly saved source keeps priority.

Featured banners rotate every eight seconds while Home is active. Rotation pauses under
remote focus, in the background, and when Reduce Motion is enabled. Navigating away cancels
the rotation task. A changed provider/catalog resets the banner position.

### Seasons and shelf navigation
Episodes are grouped by explicit season metadata or the provider's season reference/label; specials and unclassified episodes remain accessible. Grouping preserves original media references and history numbering. Each Home shelf remembers its focused column while navigating vertically. Artwork loads within a bounded viewport margin.

Select reveals AVKit's timeline, audio and embedded subtitle controls. The Playback options menu adds ten-second seeking, returns to the source/quality picker, and offers the next episode for serials. History on a later episode page loads that page and selects its season before resuming. Appearance toggles use dark text on the native white focus surface.

### Recovery and profile isolation
Library and activity reset when the selected account/profile changes. A failed account-name save retries the typed draft; notification pagination retries the same failed page. Foundation and remote-driven UI regressions cover these paths.

### Additional recovery and presentation checks
Source changes preserve the current playback position rather than the initial resume point.
Explicit pagination metadata keeps overlapping search/episode pages traversable; Next episode
can continue across an overlapping page. Support and Friends retries repeat the failed read
operation and preserve drafts. Missing saved sources fall back to a currently available TV source.
Initial avatars select a readable foreground on light or dark profile colors. Poster state is
retained on shelf return and keyed by URL/thumbnail size. Editor native rows keep readable text
under white focus; avatar shelves reserve space for focus effects.

External subtitle failures restore the previous embedded subtitle and disabled captions stop
their observer. HTML error pages cannot masquerade as subtitle files. Startup playback honors
background interruption and teardown, including asynchronous resume seek completion.

## Apple TV CI and distribution

`.github/workflows/tvos-ci.yml` runs separately from Android on `macos-26`.
It executes the Foundation checks and every remote-driven UI test, rejects skipped/missing
tests, builds an ad-hoc signed simulator Release and checks that DEBUG fixtures are absent.
Logs, `.xcresult` screenshots/results and the simulator `.app` ZIP are retained for 14 days.
The script creates and deletes its own Apple TV simulator; it does not erase your simulator.

The same pipeline works without GitHub, including while an account is suspended:

```sh
# Run from the repository root; Xcode, xcodegen and FFmpeg must be installed.
SOZO_TV_OUTPUT_DIR=/tmp/sozo-tvos-ci-new-run apple-tv/scripts/ci.sh
```

Use a new output directory for each run. A simulator ZIP is for macOS Simulator,
not an installable package for physical Apple TV.

`.github/workflows/tvos-release.yml` is manual. It first passes the complete CI gate,
then signs a device archive and exports an App Store IPA. `upload_testflight` defaults
to false; enabling it uploads the validated archive for App Store Connect processing.
It never submits for App Review or automatically distributes to external testers.
Marketing version and build number are supplied explicitly; use a higher build number
than the previous upload. It does not commit version changes or create Android tags.

Create the `apple-tv` GitHub environment and set these secrets:

| Secret | Contents |
| --- | --- |
| `APPLE_TV_TEAM_ID` | Your Apple Developer team ID |
| `APPLE_TV_CERTIFICATE_P12_BASE64` | Base64 Apple Distribution certificate **with its private key** |
| `APPLE_TV_CERTIFICATE_PASSWORD` | Password protecting that P12 |
| `APPLE_TV_PROFILE_BASE64` | Base64 **tvOS App Store** provisioning profile for `com.saikou.sozotv` |
| `ASC_API_KEY_P8_BASE64` | Base64 App Store Connect team API private key; required for upload |
| `ASC_API_KEY_ID` | App Store Connect key ID; required for upload |
| `ASC_API_ISSUER_ID` | App Store Connect issuer ID; required for upload |

The app must already exist in App Store Connect under that bundle ID and team. The
profile must authorize the supplied distribution certificate. The pipeline rejects a
wrong team, bundle, platform, expired profile and development/ad-hoc/enterprise profiles.
Signing runs in a temporary keychain, restores the original search list and removes its
private material/profile on exit. Do not put these secrets in the repository.

On a dedicated local Mac, set the same environment variables plus `SOZO_TV_VERSION`,
`SOZO_TV_BUILD_NUMBER` and optionally `SOZO_TV_UPLOAD_TESTFLIGHT=true`, then run
`apple-tv/scripts/release.sh` **after** `apple-tv/scripts/ci.sh` passes. This is the
fallback when GitHub Actions is unavailable. Signed device archive/export/upload needs
those credentials; a successful simulator build does not prove distribution signing.

`PrivacyInfo.xcprivacy` declares the actual required-reason API uses: app-only defaults
and elapsed-time calculations. These declarations follow [Apple's required-reason API
documentation](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api).
Complete the App Store privacy answers, privacy policy, screenshots and content-rights
information for the production backend before publishing. Device playback, older tvOS
fallbacks, every live provider, HDR/DRM and real remote behavior still need physical testing.

Oversized posters are rejected during streaming and their tasks are canceled, rather than
loading the entire response first. Valid poster disk caching remains enabled. Profile selection
resolves the latest profile record, preventing stale cards from bypassing a newly enabled PIN
or restoring adult permissions after a Kids change.
