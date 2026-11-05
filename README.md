# Sozo TV
<img width="2542" height="1154" alt="image" src="https://github.com/user-attachments/assets/57f0e94b-fcde-4394-9f32-0014e5e60528" />
<br> </br>
<p align="center">
   <a href="https://discord.gg/n22URhYvMR"><img src="https://img.shields.io/badge/Discord-7289DA?style=for-the-badge&logo=discord&logoColor=white"></a>
   <a href="https://telegram.me/sozoapp" ><img src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white"></a> 
   <a href="https://t.me/sozoapp"><img src="https://img.shields.io/badge/Download-Latest%20Release-brightgreen?style=for-the-badge&logo=download&logoColor=white"></a>
</p>

Sozo TV is the big-screen companion to the Sozo phone app: browse the catalogue, pick a
source and watch on a television with a remote. This repository holds three apps that talk to
the same Sozo backend (`apisozo`):

| App | Path | Stack |
| --- | --- | --- |
| Android TV | `app/` | Kotlin, Leanback launcher, Media3 ExoPlayer, min SDK 24. Also runs CloudStream (`.cs3`) and Aniyomi (`.apk`) extensions and an embedded torrent server. |
| Apple TV | `apple-tv/` | Native SwiftUI + AVKit, generated with XcodeGen. Uses only server-provided sources; see [`apple-tv/README.md`](apple-tv/README.md) for what it leaves out on purpose. |
| LG webOS TV | `webos/` | Plain ES5 web app with no build step, for webOS TV 4.x and newer. **Not yet run on a real TV**; see [`webos/README.md`](webos/README.md). |

The Android TV and Apple TV apps have household profiles, continue watching, search and localized UI.
The webOS app has no login or profiles, and keeps continue watching on the TV.

## Download

Release APKs are not kept in this repository. Get the latest Android TV build from the
**official Telegram channel** ([t.me/sozoapp](https://t.me/sozoapp)).

## Building

**Android TV** needs JDK 17 and the Android SDK:

```sh
./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # unit tests
python3 tools/check_strings.py        # translated strings vs. English
```

Release signing reads `key.properties` and a keystore, which are never committed. The two
vendored AARs in `app/libs/` are described in [`app/libs/README.md`](app/libs/README.md).

**Apple TV** needs Xcode and `xcodegen`:

```sh
cd apple-tv && xcodegen generate && open SozoTV.xcodeproj
```

Full verification, including simulator UI tests, is `apple-tv/scripts/ci.sh`; details are in
[`apple-tv/README.md`](apple-tv/README.md).

**LG webOS TV** needs Node.js 20 or newer:

```sh
cd webos && npm ci --ignore-scripts && npm test && npm run package   # ipk in dist/ipk/
```

Installing on a TV is described in [`webos/README.md`](webos/README.md).

## CI and releases

| Workflow | Runs on | Does |
| --- | --- | --- |
| `ci.yml` | PRs, pushes to `master` | String check, unit tests, debug build of the Android TV app |
| `release.yml` | Manual, or a `v*` tag | Bumps the version, builds and verifies the signed APK, sends it to Telegram |
| `tvos-ci.yml` | PRs, pushes to `master`, manual | Apple TV build and simulator tests |
| `tvos-release.yml` | Manual | Signs an Apple TV archive and optionally uploads it to TestFlight |
| `webos.yml` | PRs and pushes to `master` that touch `webos/` | webOS unit and ES5 checks, builds the ipk and keeps it as an artifact |
| `webos-release.yml` | By hand | Tests, builds the ipk and publishes it as a `webos-v<version>` pre-release |

The required secrets are listed at the top of each workflow and in `apple-tv/README.md`.

### Official Discord Server
 
<p align="center">
 <a href="https://discord.gg/n22URhYvMR">
  <img src="https://invidget.switchblade.xyz/n22URhYvMR">
 </a>

# Photos

<img width="4619" alt="Kitsune (3)" src="https://github.com/user-attachments/assets/30a8dade-66ac-4448-a411-fda1771dc804">
<br>
<br>

<img width="1576" height="958" alt="image" src="https://github.com/user-attachments/assets/561b8e29-cf0e-431c-932b-6818fac7b592" />
<br>
<br>

<img width="1694" height="1086" alt="image" src="https://github.com/user-attachments/assets/3a7eb659-f5eb-4e7d-98c4-babfd34eb1fe" />
<br>
<br>

<img width="2880" height="377" alt="image" src="https://github.com/user-attachments/assets/c531f8c9-9806-4d69-8d0b-a57c8cc7e1b8" />
<br>
<br>

## License

[MIT](https://choosealicense.com/licenses/mit/)
