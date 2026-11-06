# Vendored AARs

Both are real dependencies (`implementation(files(...))` in `app/build.gradle.kts`) and are
byte-identical to the phone app's `android/app/libs`. Each is the published library with its
native code rebuilt 16 KB page-aligned, because Android 15+ devices with 16 KB pages refuse to
load the upstream 4 KB-aligned `.so` files. `configurations.all` excludes the published
artifacts so they cannot come back transitively.

| File | Origin | SHA-256 |
| --- | --- | --- |
| `torrentserver-7861970-16k.aar` | [recloudstream/torrentserver](https://github.com/recloudstream/torrentserver) at `7861970` (TorrServer via torrentserver-aniyomi). Published `classes.jar` unchanged; `libgojni.so` for all four ABIs rebuilt with Go 1.23.5, gomobile `cd096645`, NDK r28. Used by `engine/torrent/TorrentEngine.kt`. | `cfaa598c97dae34d45849951759682b1f015b58c128e9c5db9871509297e5e40` |
| `quickjs-android-0.9.2-16k.aar` | `app.cash.quickjs:quickjs-android:0.9.2` (cashapp/zipline `a738129cc4`). Published `classes.jar` unchanged; `libquickjs.so` rebuilt with NDK r28 and `-Wl,-z,max-page-size=16384`. | `fdade24d05289ef32058324627b95a89c34504f76b6f24bf5da14da014fb1155` |

Check with `shasum -a 256 app/libs/*.aar`. Replace both here and in the phone app together.
