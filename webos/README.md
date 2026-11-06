# Sozo for LG webOS TV

A plain web app for LG TVs: browse a source, search, open a title and watch it, using the remote's
arrows, OK and Back or the Magic Remote pointer. It talks to the same backend as the other Sozo TV
apps and only sends GET requests.

> **Not yet run on a real TV.** No LG TV or webOS emulator has run this app. Everything below that says
> "plays" or "passes" means desktop Chrome through Playwright. Native HLS, Chromium 53, the Magic Remote
> and the media pipeline of a real TV are all untested. Please treat the first install as a test.

## Requirements

- **TV:** webOS TV 4.x or newer is the target. webOS 4.x has Chromium 53, the oldest engine the code
  allows for, so the shipped JavaScript is plain ES5 with no build step or framework.
- **Computer:** Node.js 20 or newer, on the same network as the TV.
- **LG account:** a free LG Developer account for the Developer Mode app.

Nothing loads from outside the package except API, image and video requests. hls.js 1.5.20 is vendored
in `vendor/hls.js` with its Apache-2.0 licence.

## Build the package

```sh
cd webos
npm ci --ignore-scripts
npm run package
```

This copies only the shipped files to `dist/app` and the relay service (`service/`) to `dist/service`,
runs `ares-package --check`, and writes `dist/ipk/com.soplay.sozo.webos_1.0.0_all.ipk` (about 246 KB). It
then opens the ipk and fails unless it holds exactly the shipped app and service files, byte for byte.
`ares-package` prints a generic Node.js version notice for any package with a service; that one notice is
allowed, because `service/` is held to ES5 by the tests.

`--no-minify` is deliberate. By default `ares-package` runs terser over every `.js`, including
`hls.min.js`, so the ES5 checks would no longer describe what ships.

The `webOS CI` workflow also builds the ipk and keeps it as the `sozo-webos-ipk` artifact for 14 days.
The `webOS release` workflow, run by hand, publishes it as a `webos-v<version>` GitHub pre-release. The version comes from `appinfo.json`.

## Install with Developer Mode

1. On the TV, install **Developer Mode** from the LG store, sign in with the LG Developer account, turn
   on **Dev Mode Status** and let the TV restart.
2. Open the Developer Mode app again and turn on **Key Server**.
3. On the computer, from `webos/`:

   ```sh
   npx ares-setup-device                      # add: name tv, the TV's IP, port 9922, user prisoner, no password
   npx ares-novacom --device tv --getkey      # asks for the 6-character passphrase shown on the TV
   npx ares-device --system-info --device tv  # checks the connection
   npx ares-install --device tv dist/ipk/com.soplay.sozo.webos_1.0.0_all.ipk
   npx ares-launch --device tv com.soplay.sozo.webos
   ```

To debug, use `npx ares-inspect --device tv --app com.soplay.sozo.webos`. To remove the app, use
`npx ares-install --device tv --remove com.soplay.sozo.webos`.

These steps come from [LG's Developer Mode guide](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app).
They have not been run against this app.

### The Developer Mode session expires

Developer Mode runs for a limited time, shown in the app's **Remain Session** field. The webOS Homebrew
project [gives the limit as 1000 hours](https://www.webosbrew.org/devmode/). Press **EXTEND** while the TV
is online, before it runs out: LG says an expired session cannot be extended.

Once it has expired, the next reboot turns Developer Mode off and **removes the installed apps**, this
one included. The same happens after ten reboots without a network. Saved positions and My List live
on the TV, so expect to lose them too. The fix is to sign in again and reinstall.

## Rooted TVs

Rooting is not needed and carries its own risk. On a TV that is already rooted with
[Homebrew Channel](https://github.com/webosbrew/webos-homebrew-channel), the session limit does not apply
([comparison](https://www.webosbrew.org/rooting/)). Copy the ipk to the TV and install it with
[webOS Dev Manager](https://www.webosbrew.org/devmode/) or with the command from Homebrew Channel's README:

```sh
luna-send-pub -i 'luna://com.webos.appInstallService/dev/install' '{"id":"com.ares.defaultName","ipkUrl":"/tmp/com.soplay.sozo.webos_1.0.0_all.ipk","subscribe":true}'
```

This has not been tried with Sozo, and Sozo is not in the Homebrew Channel repository.

## Sources

The backend lists 42 ordinary sources. The app allows nine (`js/lib/providers.js`), and shows one only if
the live API still offers it. This is a snapshot from November 2026, and the catalogue changes often.

Results are from one live run in desktop Chrome, with one title per source:

| Source | Type | Language | Result in desktop Chrome |
| --- | --- | --- | --- |
| uzbeklar | Films and series | Uzbek | Played, 720p mp4 |
| idub | Films and series | Uzbek | Played, 720p mp4 |
| animevost | Anime | Russian | Played, 720p mp4 |
| supercartoons | Cartoons | English | Played, 720x525 mp4 |
| anilibria | Anime | Russian | Played, 1080p HLS through hls.js |
| worldlivetv | Live | Many | Played, 1080p HLS through hls.js |
| livetvindia | Live | Hindi | Played, 1080p HLS through hls.js |
| vidapi | Films and series | English | **Did not play.** Its segment host sends no CORS header, so hls.js cannot read it. The chooser appears. |
| sportstv | Live | Many | **Did not play**, cause not checked. The chooser appears. It also has a plain-HTTP host and one HEVC channel. |

A TV takes a different path for HLS: it tries the platform's native player first, which needs no CORS, and
uses hls.js only as a fallback where the source allows it. vidapi's segments are named `.html` and sent as
`text/html`. On an LG webOS 10.3 TV the native player rejects them at once (`MEDIA_ELEMENT_ERROR: Format
error`), while hls.js plays the same stream at 1080p; its segment host now sends `Access-Control-Allow-Origin: *`.
So on webOS vidapi goes through the relay below, with hls.js as the fallback. sportstv plays natively on the
same TV at 1080p without the relay or hls.js (Setanta Sports 1 Eurasia via `stream.mcquack.net` and Futbol via
`185.121.1.163:8083`, both plain HTTP).

### Native HLS relay (webOS only)

The ipk carries a small JS service, `com.soplay.sozo.webos.service` (`service/`). At startup the app
subscribes to it through `PalmServiceBridge`; the service listens on `127.0.0.1` on a free port and answers
with that port and a random per-run token. For a provider marked `relabel: true` (vidapi), the native
player is given `http://127.0.0.1:<port>/<token>/p?u=<stream URL>`. The service fetches the same bytes,
names them by content (`#EXTM3U` as a playlist, `0x47` as MPEG-TS, and so on) instead of trusting
`text/html`, and rewrites every playlist URI to come back through itself. Nothing is decoded or changed.

On an LG webOS 10.3 TV a vidapi film started in 1.7 s at 1920x800 this way, and hls.js was never loaded;
plain hls.js starts on the lowest level. If the service is missing or down, the URL is left as it is,
the native player fails, and the ladder falls back to hls.js. Off webOS nothing calls the service.

### Why the other 33 are left out

| Reason | Sources |
| --- | --- |
| 18+ titles in their results | thenkiri, internetarchive, peertube, wikimedia, musictv |
| Need a Referer, User-Agent or header set a TV `<video>` cannot send | anikai, anikoto, hianimes, anikage, rareanimes, primesrc, tioanime, animeav1 |
| Need a signer, local proxy or device resolver | asilmedia, uzmovi, animeheaven, vsmov |
| Iframes with WebView sniffing only | multimovies |
| Run on the device, or blocked for the backend | kisskh, coflix, animehay, animedia, kkphim, phimnhua |
| mkv only or mislabelled, often with EAC3 audio | hdhub4u, fourkhdhub, uhdmovies, cinestream |
| `magnet:` links | nyaa, torastream |
| Video with no audio | filmzie |
| Unreliable: timeouts or most titles failing | yummyanime, animexin |

Adult providers are never listed or opened, and the `X-Sozo-Adult` header is never sent.

## Limits

- **18+ filtering is not a guarantee.** The app excludes sources, turns off search and related titles on
  vidapi, and filters text on every card and detail page. An adult film with an innocent title can still
  slip through, and a real fix needs a filter on the server by certification. The text filter also
  removes some harmless titles.
- **Old certificates.** Several sources and image hosts use Let's Encrypt chains. A webOS 4 TV may not
  trust that root, and the app cannot fix that.
- **Network.** Sources were measured from one connection in Tashkent, and may behave differently elsewhere.
- **Playback.** No DASH, DRM, torrents, mkv, downloads or dub switching. With native HLS the TV picks the
  variant, so the only quality choice is the source list.
- **Accounts.** No login, sync or profiles. Resume and My List are stored only on the TV.
- **Images.** Some posters do not load, such as animevost's, whose host has an expired certificate. Each
  card keeps a titled placeholder.
- **Languages.** English, Uzbek and Russian.

## Tests

```sh
npm ci --ignore-scripts
npx playwright-core install chromium chromium-headless-shell   # browsers for the three browser suites

npm test                # unit, ES5 parse, API and CSS lint, package checks
npm run test:browser    # app and player in headless Chromium, stubbed API
npm run test:e2e        # journeys, screens and stress, stubbed API
npm run test:e2e:live   # real journeys against production, GET only
```

CI runs `npm test` and `npm run package`. The live suite needs a browser with H.264 and AAC. It depends on
real streams and is not fully stable: on the final code one of four journey runs failed, when a chosen
anilibria quality fell back to the other one and the journey could not continue. Run it gently, as it uses
the production API.

The ES5 and CSS checks are static, so they catch syntax and known missing APIs but do not prove the app runs
on Chromium 53. Nothing here has run on Chromium 53.

## Before calling this verified on a TV

- Install on the oldest TV available and check Home, Search, Detail and the exit dialog.
- Play one mp4 source and one HLS source, then seek, change source and resume.
- Try vidapi and sportstv with native HLS on webOS older than 10.3.
- Use the Magic Remote pointer, the arrows, OK and Back, including held keys.
- Check that the screensaver stays off during playback and how `ended` behaves.
