# Sozo TV — kod, Android TV va CloudStream auditi

Sana: 2026-09-12. Tekshirilgan commit: `52b4b06`. Loyiha: `/Users/azamov/Sozo-Full/sozo-tv`.

**Eng katta tasdiqlangan muammo — CloudStream qidiruvi noto‘g‘ri API overloadini chaqiradi.** 118 ta o‘rnatilgan manba sinovida 42 ta `NotImplementedError` qayd etildi. 8 manbadagi solishtirishda 6 tasi faqat chaqiruvni `search(query, 1)` ga almashtirish bilan haqiqiy natija qaytardi. Bu o‘zgarish diagnostika ichida sinandi; mahsulot kodi o‘zgartirilmadi.

## Tekshiruv sharoiti va chegarasi

- Alohida **Sozo_TV_Audit** AVD: Android TV API 36, arm64, 1920×1080, 320 dpi, 960×540 dp. `android.hardware.type.television`, `leanback` va `leanback_only` mavjudligi ADB orqali tekshirildi.
- Dastlab oldindan ochiq mobil emulator ko‘rildi; asosiy UI, CloudStream va player tekshiruvi **emulator-5556 Android TV** da bajarildi.
- Yangi guest muhit. Shaxsiy akkauntga kirilmadi. Device auth, AniList/MAL, telefon remote va account sync kodlari ko‘rildi, lekin telefon bilan bog‘lash hamda ikki akkauntli yakuniy sinov bajarilmadi.
- CloudStream qidiruvi 118 manbada, detal 10 manbada, video havola 9 manbada tekshirildi. Natija chiqishi haqiqiy ijroni kafolatlamaydi. Haqiqiy video ijrosi AnimeAv1 → Naruto → Episode 1 bilan sinandi.
- Real televizordagi HDR, HDMI mode switch, turli kodeklar, uzoq seans, past RAM va barcha provayderlarning barcha kontenti bu audit bilan sertifikatlanmaydi.

## Avtomatik tekshiruv

| Tekshiruv | Natija |
|---|---|
| `:app:assembleDebug` | APK yig‘ildi |
| `:app:testDebugUnitTest` | **152 test, 0 failure, 0 error, 0 skipped** |
| `:app:lintDebug` | **80 error, 630 warning, 1 information**; lint task yiqildi |
| `python3 tools/check_strings.py` | 9 locale, 377 asosiy string; mavjud tarjimalardagi formatlar mos |
| TV emulator crash buffer | Tekshirilgan seansda bo‘sh; ushlangan plugin xatolari oddiy logcat’da mavjud |

Lint error tarkibi: 44 `MissingTranslation`, 30 `StringFormatMatches`, 5 `UnsafeOptInUsageError`, 1 `ByteOrderMark`. 80 lint xatosi 80 ta runtime crash degani emas. Masalan `%s` uchun son berilishi formatni buzmasdan ham ishlaydi, ammo lintni to‘xtatadi. `check_strings.py` barcha tarjimalar to‘liqligini yoki Kotlin chaqiruv argumentlarini tekshirmaydi.

Build/lint paytida CloudStream/NiceHttp/OkHttp/Okio kutubxonalarining Kotlin metadata versiyalari bo‘yicha moslik xabarlari ham chiqdi. APK yig‘ilishi va 152 test o‘tishi barcha dinamik pluginlar ABI jihatdan mosligini isbotlamaydi.

## CloudStream natijalari

[118 manbaning to‘liq jadvali](cloudstream-matrix.md) va [xom natijalar](evidence/cloudstream-search.jsonl).

`Naruto` so‘rovi, amaldagi host, concurrency=4, tashqi timeout=20 s:

- **35** manba natija qaytardi.
- **80** manba bo‘sh natija qaytardi.
- **3** manba timeout/null bilan tugadi.
- Bo‘sh javoblar ichida **42 `NotImplementedError`**, shuningdek tarmoq, login, playlist va ABI xatolari bor. Qolgan bo‘sh javoblarni “manba buzilgan” deb belgilash mumkin emas: manbaning kontent turi yoki so‘rovga mos kontent yo‘qligi ham sabab bo‘ladi.

### P1 — qidiruvning noto‘g‘ri overloadi

Joy: `app/src/main/java/com/saikou/sozo_tv/engine/cloudstream/PluginHost.kt:417` va tavsiyalar fallback’i `:497`.

Host `api.search(query)` chaqiradi. AllWish@18 haqiqiy DEX metod imzosi esa `(String, int, Continuation)`: ya’ni `search(query, page)`. Lokal CloudStream v4.7.0 sources’da sahifali overload eski overloadga fallback qiladi, eski overloadning bazaviy tanasi esa `throw NotImplementedError()`.

| Manba | Amaldagi chaqiruv | `search("Naruto", 1)` |
|---|---:|---:|
| AllWish | 0 / NotImplementedError | 26 |
| Anikage | 0 / NotImplementedError | 25 |
| KickassAnime | 0 / NotImplementedError | 29 |
| MovieBox | 0 / NotImplementedError | 14 |
| StreamPlay | 0 / NotImplementedError | 20 |
| SuperStream | 0 / NotImplementedError | 20 |
| AniDB | 0 / NotImplementedError | 0; qo‘shimcha tekshiruv kerak |
| Anichi | 0 / NotImplementedError | `StringUtils.encodeUrl` NoSuchMethodError |

Dalil: [bir xil pluginlar bilan taqqoslash](evidence/cloudstream-compare.jsonl). Tavsiya: sahifali API’ni asosiy kirish nuqtasi qilish, `items`/`hasNext` ni saqlash; tavsiyalar fallback’ini ham shu yo‘lga o‘tkazish. Faqat birinchi chaqiruvni almashtirish barcha provayder muammolarini hal qilmaydi.

### P1 — mustaqil ABI va yetishmayotgan klasslar

| Manba | Tasdiqlangan log | Ta’sir |
|---|---|---|
| CineStream@487 | `AccountManager.Companion.getSimklApi()` yo‘q | Plugin yuklanmaydi |
| AnimePahe@39 | `CommonActivity.getActivity()` virtual kutilgan, static topilgan | Qidiruv / Cloudflare oqimi ishlamaydi |
| Anichi@26 | `StringUtils.encodeUrl(String)` yo‘q | To‘g‘ri qidiruv API’sida ham ishlamaydi |
| StreamPlay-Anime | `com.fleeksoft.ksoup.Ksoup` yo‘q | Havola chiqarishning bir qismi uziladi; sinovda 4 manba qolgandi |

Kod: `app/build.gradle.kts` (CloudStream v4.7.0 va Kotlin pinlari), `app/src/main/java/com/lagradost/cloudstream3/CommonActivity.kt:33`, `app/src/main/java/com/lagradost/cloudstream3/syncproviders/SyncAPI.kt:56`.

AnimePahe uchun sabab ayniqsa aniq: shim izohida instance getter kerakligi yozilgan, ammo `activity` ga `@JvmStatic` qo‘yilgan. Moslikni taxminiy bo‘sh stub bilan emas, plugin kutayotgan haqiqiy JVM imzosi va real yo‘l bilan tekshirish kerak.

### P2 — xatolar “natija topilmadi” ko‘rinishiga aylantiriladi

`PluginHost.kt:417–425` `Throwable` ni ushlab `emptyList()` beradi. `loadJson` va `loadLinksJson` ham o‘xshash tarzda xatoni yutadi. `ExtensionEngine.searchOne()` dagi `ERROR` holati ushbu xatolarni ko‘rmaydi: host ularni oldindan bo‘sh muvaffaqiyatga aylantirgan.

Natija: yangi API talabi, login talabi va mos kontent yo‘qligi foydalanuvchiga bir xil ko‘rinadi. Cancellation ham umumiy `Throwable` ichida ushlanadi. Tavsiya: typed failure yoki exception propagation; cancellation’ni qayta tashlash; UI’da no-match, unavailable, needs-setup va incompatible holatlarini ajratish.

### P2 — CloudStream plugin sozlamalariga kirish yo‘q

`SourceScreen.kt:392–400` faqat Aniyomi manbalarining sozlamasini ochadi; CloudStream uchun darhol `false`. `Plugin.openSettings` maydoni bor, ammo UI undan foydalanmaydi.

Logda Kartoons akkauntga kirishni so‘radi; Jellyfin `null/U...`, StremioC `/manif...` singari sozlanmagan URL bilan yiqildi. Bu manbalarni oddiy “ishlamaydi” deb chiqarish o‘rniga sozlash yo‘lini taqdim etish kerak. BDIX manbalarida esa public hamda private hostlarga connection timeout qayd etildi; faqat TV kodi bilan ularning tarmoq cheklovini hal qilib bo‘lmaydi.

### Detal va havolalar sinovi

Bu jadvalda dastlabki qidiruvdagi to‘siq diagnostikada sahifali API bilan chetlab o‘tildi. Mahsulot hostiga patch qo‘yilmadi.

| Manba | Detal | Video havola |
|---|---|---|
| AnimeAv1 | Naruto, 220 epizod | 15 source, 0 subtitle |
| Anikage | Naruto, 220 epizod | 9 source, 5 subtitle |
| KickassAnime | Naruto, 220 epizod | 2 source, 5 subtitle |
| MovieBox | Naruto, 240 epizod | 6 source, 34 subtitle |
| AniKoto | Road of Naruto, 1 epizod | 2 source |
| StreamPlay-Anime | Naruto, 220 epizod | 4 source; Ksoup xatosi ham bor |
| SuperStream | Naruto, 220 epizod | 0 source |
| Animecloud | Naruto, 226 epizod | 0 source |
| AllWish | Tanlangan film, 0 epizod | Sinab bo‘lmadi: epizod yo‘q |
| StreamPlay | Naruto, 220 epizod | Tashqi 30 s timeout **66.862 s** da qaytdi |

StreamPlay host logida timeoutdan keyin 29 source va 81 subtitle yig‘ilgani ko‘rinadi, ammo bekor bo‘lgan so‘rov ularni chaqiruvchiga yetkazmadi. Bu raqamlarni muvaffaqiyatli ijro deb hisoblamadim. Havola pipeline’i cancellation/timeoutga kech javob bermoqda.

Dalil: [detal/havola natijalari](evidence/cloudstream-details.jsonl), [tegishli log](evidence/cloudstream-detail-log.txt).

## Ilovaning qolgan qismi

### P1 — “All sources” faqat dastlabki 12 manbada qidiradi

`ExtensionEngine.kt:323–328`, `MAX_GLOBAL_PROVIDERS = 12` (`:432`). Ro‘yxat SERVER → ANIYOMI → CLOUDSTREAM tartibida birlashtirilib, faol manba boshga chiqariladi va `take(12)` qilinadi. Yetarli server manbalari bo‘lsa, faol bo‘lmagan CloudStream hamda Aniyomi manbalari umuman so‘rov olmaydi.

Tavsiya: foydalanuvchi tanlagan haqiqiy qamrovni qidirish; yukni provider sonini yashirin kesish bilan emas, concurrency va bosqichma-bosqich natija bilan cheklash. Yoki cheklangan qamrovni UI’da aniq nomlash.

### P1 — epizod tarixi serial sifatida saqlanmaydi

`SeriesPlayerScreen.kt:1888–1910` yangi `WatchHistoryEntity` yaratishda `isSeries` va `isAnime` bermaydi. Entity defaultlari: `isSeries=false`, `isAnime=true` (`data/local/entity/AnimeBookmark.kt:70–71`).

**Emulator bazasida tasdiq:** Naruto Episode 1, `providerId=cs:AnimeAv1`, `epIndex=0`, `lastPosition=197391`, `totalDuration=1450083`, ammo `isSeries=0`. [Baza tekshiruvi](evidence/history-check.json).

`WatchHistorySyncRepository.syncKey()` serial bo‘lmasa epizod qismini qo‘shmaydi; serverga ham `isSerial=false` ketadi. Natijada turli epizodlar bir kalitga tushishi mumkin. Anime bo‘lmagan kontent esa default `isAnime=true` tufayli tarix filtrida noto‘g‘ri bo‘limga tushadi. Tavsiya: haqiqiy detail metadata’sini player argumentlariga va history entity’ga olib o‘tish, eski yozuvlar uchun migratsiya/tuzatish yo‘lini belgilash.

### P2 — qidiruv natijalari eski so‘rov bilan almashishi mumkin

`SearchViewModel.kt:28–58` va `searchMovie()` har chaqiruvda yangi job ochadi, avvalgisini bekor qilmaydi yoki query generation bilan tekshirmaydi. B so‘rovi A’dan oldin tugasa, kechikkan A yangi natijalarni bosadi. Xato yo‘lida `_loading=false` ham qo‘yilmagan. `SearchScreen` faqat debounce job’ini bekor qiladi; ViewModel’dagi tarmoq ishini emas. Kodda tasdiqlangan; sun’iy kechiktirilgan tarmoq bilan alohida regressiya sinovi hali yo‘q.

### P2 — epizod ekranidagi manba nomi noto‘g‘ri

`EpisodeScreen.kt:104–105` kontentning encoded provider’i o‘rniga global active provider nomini chiqaradi. AnimeAv1’dan ochilgan Naruto ekranida “Current Selected Source: VidAPI” ko‘rindi. Video yo‘li to‘g‘ri provider bilan ishladi, ammo foydalanuvchi noto‘g‘ri manbani ayblashi mumkin. [Ekran](evidence/13-episodes.png).

### P2 — bookmark va akkaunt ro‘yxatlari ikki alohida tizim

UI’dagi bookmark `MovieBookmarkRepositoryImpl` orqali faqat Room’ga yoziladi. `UserListsRepository` va API client mavjud, lekin presentation qatlamidagi yagona qo‘llanishi `SettingsViewModel.exitUser()` da `clear()`; `WATCH_LATER`/`WATCHED` UI oqimiga ulanmagan. Telefon bilan Watch Later/Watched almashinuvi mavjud deb hisoblab bo‘lmaydi.

Shuningdek sign-out mahalliy bookmark repository’ni tozalamaydi. Agar bookmarklar akkauntga tegishli bo‘lishi talab qilinsa, keyingi akkaunt avvalgi lokal bookmarklarni ko‘radi. Bu talab aniqlashtirilishi kerak; guest/device-local bo‘lishi ham ongli mahsulot qarori bo‘lishi mumkin.

### P2 — QuickJS 16 KB page alignment’ga tayyor emas

Yangi APK ichidagi `lib/arm64-v8a/libquickjs.so` ELF `PT_LOAD.p_align` qiymatlari **4096**. Qolgan tekshirilgan arm64 native kutubxonalari 16384. Dastlabki 16 KB mobil emulator ham compatibility ogohlantirishi ko‘rsatdi. TV API 36 sinovida bu crash bo‘lmadi; 16 KB qurilmalarga mos build uchun QuickJS native artefaktini yangilash/qayta yig‘ish zarur.

### Qo‘shimcha kuzatuvlar

- `DetailViewModel.loadTrailer()` hali stub; trailer metadata’ni olmaydi. Hozir UI yo‘q trailer boshqaruvlarini yashiradi, shuning uchun asosiy Watch oqimini to‘xtatmaydi.
- Login/Sozo API klientida platform TLS va tokenlarning encrypted saqlanishi mavjud. Image pipeline’dagi eski trust-all olib tashlangan. Biroq uchinchi tomon stream/CloudStream trafikida trust-all hali qasddan yoqilgan; auth klientidan ajratilgan bo‘lsa-da, tarmoq yo‘lida stream ma’lumotlarini almashtirish xavfi qoladi.
- README amaldagi mahsulotni to‘liq tasvirlamaydi: u hali “Anilist only” deb yozilgan, kodda esa server, CloudStream, Aniyomi, device auth, remote va live TV bor.

## Android TV orqali ko‘rilgan oqimlar

| Oqim | Kuzatuv |
|---|---|
| Cold start / update | Update oynasi chiqdi; Later orqali Home ochildi. Release notes bo‘sh edi; update o‘rnatish bosilmadi |
| Home va pult rail’i | Banner, genres, popular movies, focus va chap menyuga o‘tish ishladi |
| TV Garden | Kategoriya va kanallar ro‘yxati yuklandi; live stream ijrosi bu seansda tasdiqlanmadi |
| Sources | Sozo/Aniyomi/CloudStream tablari, provider ro‘yxati va yuklanish xatosi ko‘rindi |
| Search | Custom TV klaviaturasi, scope tugmasi; provider API qismi yuqoridagi harness bilan tekshirildi |
| Profile | Guest, settings/connection bo‘limlari ko‘rildi; signed-in oqimi bajarilmadi |
| Detail | Naruto tavsifi, 220 epizod, Watch Now ochildi |
| Episodes | 1–100, 101–200, 201–220 tablari va Episode 1 ochildi |
| Player | Birinchi AnimeAv1 HLS hostida HTTP 403; “Try another server” → Voe’dan keyin haqiqiy video ijrosi |
| Playback controls | Media session PLAYING, vaqt oldinga yurdi; PAUSED holati, seek, server va subtitr dialogi tekshirildi |
| History | Playerdan chiqishda progress yozildi; yuqoridagi `isSeries=0` xatosi bazada tasdiqlandi |

[Haqiqiy video kadri](evidence/18-playing-controls.png), [pauza boshqaruvlari](evidence/19-paused.png), [subtitr oynasi](evidence/20-subtitles.png).

## Tuzatish tartibi

1. CloudStream search overloadini va error/cancellation propagation’ni tuzatish; eski va yangi search implementatsiyasi bilan regressiya test.
2. AnimePahe `CommonActivity` ABI, Anichi `StringUtils`, CineStream Simkl va Ksoup mosligini real pluginlar bilan tekshirish. Dependency upgrade’ni alohida build o‘zgarishi sifatida qilish.
3. All-sources qamrovi va CloudStream settings/login yo‘lini tuzatish.
4. History metadata hamda ikki qurilmali sync sinovi; qidiruv race/loading holatlari.
5. Lintni yashirmasdan xatolarini tuzatish; QuickJS 16 KB; tarjima va provider label tuzatishlari.

## Qayta bajarish va saqlangan materiallar

Asosiy buyruq: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`.

Diagnostika original `ExtensionEngine` va haqiqiy `.cs3` fayllarni TV ilovasi ichida ishlatdi. Tekshiruv activity’si manbasi [AuditActivity.kt.txt](evidence/AuditActivity.kt.txt), faqat-debug manifest [AndroidManifest.xml.txt](evidence/AndroidManifest.xml.txt). Ular qayta sinash uchun saqlandi, `app/src/debug` dan olib tashlandi. Mahsulot kodiga patch kiritilmadi.

Hisobotdagi runtime natijalar ushbu sana, emulator, tarmoq va plugin versiyalariga tegishli. Keyingi provider yangilanishi natijalarni o‘zgartirishi mumkin.
