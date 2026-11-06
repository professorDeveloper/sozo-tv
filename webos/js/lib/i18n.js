(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var STRINGS = {
    en: {
      'app.loading': 'Loading...',
      'tab.home': 'Home', 'tab.search': 'Search', 'tab.categories': 'Categories', 'tab.mylist': 'My List', 'tab.settings': 'Settings',
      'common.retry': 'Try again', 'common.back': 'Back', 'common.cancel': 'Cancel', 'common.close': 'Close', 'common.changeSource': 'Change source',
      'exit.title': 'Exit Sozo?', 'exit.message': 'You can come back any time.', 'exit.confirm': 'Exit', 'exit.stay': 'Stay',
      'offline.banner': 'No internet connection',
      'home.continue': 'Continue watching', 'home.empty': 'This source has nothing to show right now.', 'row.viewAll': 'View all',
      'source.title': 'Choose a source', 'source.hint': 'Sources come from different sites. Only those that can play on this TV are listed.',
      'source.current': 'Selected', 'source.ok': 'Working', 'source.down': 'May be unavailable', 'source.unknown': 'Status unknown', 'source.none': 'No sources are available right now.',
      'kind.vod': 'Movies and series', 'kind.live': 'Live TV',
      'lang.uz': 'Uzbek', 'lang.ru': 'Russian', 'lang.en': 'English', 'lang.hi': 'Hindi', 'lang.all': 'Many languages',
      'search.placeholder': 'Type to search', 'search.prompt': 'Results appear here as you type.', 'search.short': 'Type at least 2 characters.',
      'search.none': 'Nothing found for this search.', 'search.off': 'Search is not available for this source.', 'search.results': 'Results', 'search.in': 'Searching in {source}',
      'key.space': 'Space', 'key.backspace': 'Delete', 'key.mode.latin': 'ABC', 'key.mode.cyrillic': 'АБВ',
      'cat.categories': 'Categories', 'cat.genres': 'Genres', 'cat.latest': 'Latest', 'cat.none': 'This source has no categories.', 'cat.off': 'Browsing by category is not available for this source.',
      'list.empty': 'Nothing to show here.', 'list.moreError': 'Could not load more.',
      'detail.play': 'Play', 'detail.resume': 'Resume', 'detail.resumeAt': 'Resume from {time}', 'detail.playEpisode': 'Play episode {n}', 'detail.resumeEpisode': 'Resume episode {n}',
      'detail.watchLive': 'Watch live', 'detail.add': 'Add to My List', 'detail.remove': 'In My List', 'detail.episodes': 'Episodes', 'detail.season': 'Season {n}', 'detail.episodeN': 'Episode {n}',
      'detail.related': 'You may also like', 'detail.cast': 'Cast', 'detail.director': 'Director', 'detail.noEpisodes': 'No episodes are available yet.',
      'detail.episodesError': 'Could not load the episodes.', 'detail.untitled': 'Untitled',
      'mylist.title': 'My List', 'mylist.empty': 'Your list is empty. Open a title and choose Add to My List.',
      'settings.language': 'Language', 'settings.langAuto': 'Same as TV', 'settings.source': 'Source', 'settings.data': 'Saved on this TV',
      'settings.clearHistory': 'Clear watch history', 'settings.clearList': 'Clear My List', 'settings.confirmHistory': 'Clear your watch history?', 'settings.confirmList': 'Clear My List?',
      'settings.confirmHint': 'This cannot be undone.', 'settings.clear': 'Clear', 'settings.cleared': 'Done', 'settings.about': 'About', 'settings.version': 'Version {v}',
      'settings.safety': '18+ sources are not offered and matching titles are filtered out.',
      'player.opening': 'Opening...', 'player.switching': 'Trying another link...', 'player.play': 'Play', 'player.pause': 'Pause', 'player.next': 'Next episode', 'player.nextIn': 'Next episode in {s} s', 'player.playNow': 'Play now',
      'player.quality': 'Quality and source', 'player.source': 'Source', 'player.sourceN': 'Source {n}', 'player.levels': 'Quality', 'player.auto': 'Auto', 'player.subtitles': 'Subtitles', 'player.subOff': 'Off', 'player.subError': 'These subtitles could not be loaded.', 'player.subReset': 'Reset', 'player.subOffset': 'Timing: {s}', 'player.audio': 'Audio', 'player.audioN': 'Track {n}', 'player.live': 'LIVE',
      'player.noSource.t': 'This title cannot play on this TV', 'player.noSource.m': 'Its video links do not work with this TV. Try another source.',
      'player.failed.t': 'This video did not play', 'player.failed.m': 'Pick another link, or change source. Some links are blocked or dead, and the TV cannot tell which.',
      'player.leaveLive.t': 'Leave this channel?', 'player.leaveLive.m': 'Live TV carries on without you.', 'player.leave': 'Leave', 'player.stay': 'Keep watching',
      'err.offline.t': 'No internet connection', 'err.offline.m': 'Check the TV network settings, then try again.',
      'err.timeout.t': 'This is taking too long', 'err.timeout.m': 'The source did not answer in time. Try again.',
      'err.network.t': 'Could not connect', 'err.network.m': 'The connection failed. Check the network and try again.',
      'err.badData.t': 'Something went wrong', 'err.badData.m': 'The source sent an unexpected answer. Try again in a moment.',
      'err.region.t': 'Not available in this region', 'err.region.m': 'This source cannot be reached from here. Try another source.',
      'err.device.t': 'Not available on this TV', 'err.device.m': 'This source does not work on this TV. Try another source.',
      'err.gone.t': 'No longer available', 'err.gone.m': 'This source or title has been removed.',
      'err.rate.t': 'Too many requests', 'err.rate.m': 'Please wait a few seconds, then try again.',
      'err.rateWait.m': 'Please wait {s} seconds, then try again.',
      'err.notFound.t': 'Not found', 'err.notFound.m': 'This title could not be found. It may have been removed.',
      'err.request.t': 'Something went wrong', 'err.request.m': 'The source could not handle this request.',
      'err.unavailable.t': 'Temporarily unavailable', 'err.unavailable.m': 'The source is not responding right now. Try again in a moment.',
      'err.notAvailable.t': 'Not available', 'err.notAvailable.m': 'This title is not available.',
      'err.generic.t': 'Something went wrong', 'err.generic.m': 'Please try again.'
    },
    uz: {
      'app.loading': 'Yuklanmoqda...',
      'tab.home': 'Bosh sahifa', 'tab.search': 'Qidiruv', 'tab.categories': 'Kategoriyalar', 'tab.mylist': "Ro'yxatim", 'tab.settings': 'Sozlamalar',
      'common.retry': 'Qayta urinish', 'common.back': 'Orqaga', 'common.cancel': 'Bekor qilish', 'common.close': 'Yopish', 'common.changeSource': "Manbani o'zgartirish",
      'exit.title': 'Sozodan chiqasizmi?', 'exit.message': 'Istalgan payt qaytishingiz mumkin.', 'exit.confirm': 'Chiqish', 'exit.stay': 'Qolish',
      'offline.banner': "Internet aloqasi yo'q",
      'home.continue': "Ko'rishni davom ettirish", 'home.empty': "Bu manbada hozircha ko'rsatadigan narsa yo'q.", 'row.viewAll': 'Barchasi',
      'source.title': 'Manbani tanlang', 'source.hint': "Manbalar turli saytlardan olinadi. Faqat shu televizorda ishlay oladiganlari ko'rsatilgan.",
      'source.current': 'Tanlangan', 'source.ok': 'Ishlayapti', 'source.down': 'Ishlamasligi mumkin', 'source.unknown': "Holati noma'lum", 'source.none': "Hozircha mavjud manba yo'q.",
      'kind.vod': 'Filmlar va seriallar', 'kind.live': 'Jonli TV',
      'lang.uz': "O'zbekcha", 'lang.ru': 'Ruscha', 'lang.en': 'Inglizcha', 'lang.hi': 'Hindcha', 'lang.all': "Ko'p tilli",
      'search.placeholder': 'Qidirish uchun yozing', 'search.prompt': 'Natijalar yozish davomida shu yerda chiqadi.', 'search.short': 'Kamida 2 ta belgi yozing.',
      'search.none': 'Bu qidiruv bo\'yicha hech narsa topilmadi.', 'search.off': 'Bu manbada qidiruv mavjud emas.', 'search.results': 'Natijalar', 'search.in': '{source} ichida qidirilmoqda',
      'key.space': "Bo'sh joy", 'key.backspace': "O'chirish", 'key.mode.latin': 'ABC', 'key.mode.cyrillic': 'АБВ',
      'cat.categories': 'Kategoriyalar', 'cat.genres': 'Janrlar', 'cat.latest': 'Yangilari', 'cat.none': "Bu manbada kategoriyalar yo'q.", 'cat.off': "Bu manbada kategoriya bo'yicha ko'rish mavjud emas.",
      'list.empty': "Bu yerda hech narsa yo'q.", 'list.moreError': "Davomini yuklab bo'lmadi.",
      'detail.play': "Ko'rish", 'detail.resume': 'Davom ettirish', 'detail.resumeAt': '{time} dan davom ettirish', 'detail.playEpisode': "{n}-qismni ko'rish", 'detail.resumeEpisode': '{n}-qismni davom ettirish',
      'detail.watchLive': "Jonli ko'rish", 'detail.add': "Ro'yxatimga qo'shish", 'detail.remove': "Ro'yxatimda", 'detail.episodes': 'Qismlar', 'detail.season': '{n}-mavsum', 'detail.episodeN': '{n}-qism',
      'detail.related': "Sizga yoqishi mumkin", 'detail.cast': 'Aktyorlar', 'detail.director': 'Rejissyor', 'detail.noEpisodes': "Hozircha qismlar yo'q.",
      'detail.episodesError': "Qismlarni yuklab bo'lmadi.", 'detail.untitled': 'Nomsiz',
      'mylist.title': "Ro'yxatim", 'mylist.empty': "Ro'yxatingiz bo'sh. Filmni oching va Ro'yxatimga qo'shishni tanlang.",
      'settings.language': 'Til', 'settings.langAuto': 'Televizor tili', 'settings.source': 'Manba', 'settings.data': 'Shu televizorda saqlangan',
      'settings.clearHistory': "Ko'rish tarixini tozalash", 'settings.clearList': "Ro'yxatimni tozalash", 'settings.confirmHistory': "Ko'rish tarixi tozalansinmi?", 'settings.confirmList': "Ro'yxatim tozalansinmi?",
      'settings.confirmHint': "Buni ortga qaytarib bo'lmaydi.", 'settings.clear': 'Tozalash', 'settings.cleared': 'Tayyor', 'settings.about': 'Ilova haqida', 'settings.version': 'Versiya {v}',
      'settings.safety': '18+ manbalar taklif qilinmaydi, mos nomlar esa filtrlanadi.',
      'player.opening': 'Ochilmoqda...', 'player.switching': 'Boshqa havola sinab ko‘rilmoqda...', 'player.play': 'Ijro etish', 'player.pause': 'Pauza', 'player.next': 'Keyingi qism', 'player.nextIn': 'Keyingi qism {s} soniyadan keyin', 'player.playNow': 'Hozir ochish',
      'player.quality': 'Sifat va manba', 'player.source': 'Manba', 'player.sourceN': 'Manba {n}', 'player.levels': 'Sifat', 'player.auto': 'Avtomatik', 'player.subtitles': 'Subtitrlar', 'player.subOff': "O'chiq", 'player.subError': "Bu subtitrlarni yuklab bo'lmadi.", 'player.subReset': 'Tiklash', 'player.subOffset': 'Vaqt siljishi: {s}', 'player.audio': 'Ovoz', 'player.audioN': '{n}-yo‘lak', 'player.live': 'JONLI',
      'player.noSource.t': "Bu filmni shu televizorda ko'rib bo'lmaydi", 'player.noSource.m': 'Uning video havolalari bu televizorda ishlamaydi. Boshqa manbani sinab ko‘ring.',
      'player.failed.t': 'Bu video ochilmadi', 'player.failed.m': "Boshqa havolani yoki manbani tanlang. Ayrim havolalar bloklangan yoki ishlamaydi, televizor buni ajrata olmaydi.",
      'player.leaveLive.t': 'Kanaldan chiqasizmi?', 'player.leaveLive.m': 'Jonli efir sizsiz davom etadi.', 'player.leave': 'Chiqish', 'player.stay': "Ko'rishda davom etish",
      'err.offline.t': "Internet aloqasi yo'q", 'err.offline.m': 'Televizor tarmoq sozlamalarini tekshirib, qayta urining.',
      'err.timeout.t': "Juda uzoq davom etyapti", 'err.timeout.m': 'Manba vaqtida javob bermadi. Qayta urining.',
      'err.network.t': "Ulanib bo'lmadi", 'err.network.m': 'Aloqa uzildi. Tarmoqni tekshirib, qayta urining.',
      'err.badData.t': 'Xatolik yuz berdi', 'err.badData.m': 'Manba kutilmagan javob yubordi. Birozdan keyin qayta urining.',
      'err.region.t': 'Bu hududda mavjud emas', 'err.region.m': "Bu manbaga bu yerdan ulanib bo'lmaydi. Boshqa manbani sinab ko'ring.",
      'err.device.t': 'Bu televizorda mavjud emas', 'err.device.m': "Bu manba shu televizorda ishlamaydi. Boshqa manbani sinab ko'ring.",
      'err.gone.t': 'Endi mavjud emas', 'err.gone.m': "Bu manba yoki film olib tashlangan.",
      'err.rate.t': "So'rovlar juda ko'p", 'err.rate.m': 'Bir necha soniya kuting, keyin qayta urining.',
      'err.rateWait.m': '{s} soniya kuting, keyin qayta urining.',
      'err.notFound.t': 'Topilmadi', 'err.notFound.m': 'Bu film topilmadi. U olib tashlangan bo\'lishi mumkin.',
      'err.request.t': 'Xatolik yuz berdi', 'err.request.m': "Manba bu so'rovni bajara olmadi.",
      'err.unavailable.t': 'Vaqtincha mavjud emas', 'err.unavailable.m': 'Manba hozir javob bermayapti. Birozdan keyin qayta urining.',
      'err.notAvailable.t': 'Mavjud emas', 'err.notAvailable.m': 'Bu film mavjud emas.',
      'err.generic.t': 'Xatolik yuz berdi', 'err.generic.m': 'Qayta urining.'
    },
    ru: {
      'app.loading': 'Загрузка...',
      'tab.home': 'Главная', 'tab.search': 'Поиск', 'tab.categories': 'Категории', 'tab.mylist': 'Мой список', 'tab.settings': 'Настройки',
      'common.retry': 'Повторить', 'common.back': 'Назад', 'common.cancel': 'Отмена', 'common.close': 'Закрыть', 'common.changeSource': 'Сменить источник',
      'exit.title': 'Выйти из Sozo?', 'exit.message': 'Вернуться можно в любой момент.', 'exit.confirm': 'Выйти', 'exit.stay': 'Остаться',
      'offline.banner': 'Нет подключения к интернету',
      'home.continue': 'Продолжить просмотр', 'home.empty': 'В этом источнике сейчас ничего нет.', 'row.viewAll': 'Смотреть все',
      'source.title': 'Выберите источник', 'source.hint': 'Источники берутся с разных сайтов. Показаны только те, что могут работать на этом телевизоре.',
      'source.current': 'Выбран', 'source.ok': 'Работает', 'source.down': 'Может быть недоступен', 'source.unknown': 'Статус неизвестен', 'source.none': 'Сейчас нет доступных источников.',
      'kind.vod': 'Фильмы и сериалы', 'kind.live': 'Прямой эфир',
      'lang.uz': 'Узбекский', 'lang.ru': 'Русский', 'lang.en': 'Английский', 'lang.hi': 'Хинди', 'lang.all': 'Много языков',
      'search.placeholder': 'Введите запрос', 'search.prompt': 'Результаты появятся здесь по мере ввода.', 'search.short': 'Введите не менее 2 символов.',
      'search.none': 'По этому запросу ничего не найдено.', 'search.off': 'В этом источнике нет поиска.', 'search.results': 'Результаты', 'search.in': 'Поиск в {source}',
      'key.space': 'Пробел', 'key.backspace': 'Удалить', 'key.mode.latin': 'ABC', 'key.mode.cyrillic': 'АБВ',
      'cat.categories': 'Категории', 'cat.genres': 'Жанры', 'cat.latest': 'Новинки', 'cat.none': 'В этом источнике нет категорий.', 'cat.off': 'Просмотр по категориям в этом источнике недоступен.',
      'list.empty': 'Здесь пока ничего нет.', 'list.moreError': 'Не удалось загрузить ещё.',
      'detail.play': 'Смотреть', 'detail.resume': 'Продолжить', 'detail.resumeAt': 'Продолжить с {time}', 'detail.playEpisode': 'Смотреть серию {n}', 'detail.resumeEpisode': 'Продолжить серию {n}',
      'detail.watchLive': 'Смотреть эфир', 'detail.add': 'В мой список', 'detail.remove': 'В списке', 'detail.episodes': 'Серии', 'detail.season': 'Сезон {n}', 'detail.episodeN': 'Серия {n}',
      'detail.related': 'Вам может понравиться', 'detail.cast': 'В ролях', 'detail.director': 'Режиссёр', 'detail.noEpisodes': 'Серий пока нет.',
      'detail.episodesError': 'Не удалось загрузить серии.', 'detail.untitled': 'Без названия',
      'mylist.title': 'Мой список', 'mylist.empty': 'Список пуст. Откройте фильм и выберите «В мой список».',
      'settings.language': 'Язык', 'settings.langAuto': 'Как на телевизоре', 'settings.source': 'Источник', 'settings.data': 'Сохранено на этом телевизоре',
      'settings.clearHistory': 'Очистить историю просмотра', 'settings.clearList': 'Очистить мой список', 'settings.confirmHistory': 'Очистить историю просмотра?', 'settings.confirmList': 'Очистить мой список?',
      'settings.confirmHint': 'Это действие нельзя отменить.', 'settings.clear': 'Очистить', 'settings.cleared': 'Готово', 'settings.about': 'О приложении', 'settings.version': 'Версия {v}',
      'settings.safety': 'Источники 18+ не предлагаются, подходящие названия отфильтровываются.',
      'player.opening': 'Открываем...', 'player.switching': 'Пробуем другую ссылку...', 'player.play': 'Смотреть', 'player.pause': 'Пауза', 'player.next': 'Следующая серия', 'player.nextIn': 'Следующая серия через {s} с', 'player.playNow': 'Включить сейчас',
      'player.quality': 'Качество и источник', 'player.source': 'Источник', 'player.sourceN': 'Источник {n}', 'player.levels': 'Качество', 'player.auto': 'Авто', 'player.subtitles': 'Субтитры', 'player.subOff': 'Выкл.', 'player.subError': 'Не удалось загрузить эти субтитры.', 'player.subReset': 'Сброс', 'player.subOffset': 'Сдвиг: {s}', 'player.audio': 'Звук', 'player.audioN': 'Дорожка {n}', 'player.live': 'ЭФИР',
      'player.noSource.t': 'Этот фильм нельзя посмотреть на этом телевизоре', 'player.noSource.m': 'Его видеоссылки не работают на этом телевизоре. Попробуйте другой источник.',
      'player.failed.t': 'Это видео не запустилось', 'player.failed.m': 'Выберите другую ссылку или источник. Часть ссылок заблокирована или не работает, и телевизор не может отличить одно от другого.',
      'player.leaveLive.t': 'Выйти с канала?', 'player.leaveLive.m': 'Прямой эфир продолжится без вас.', 'player.leave': 'Выйти', 'player.stay': 'Смотреть дальше',
      'err.offline.t': 'Нет подключения к интернету', 'err.offline.m': 'Проверьте настройки сети телевизора и повторите.',
      'err.timeout.t': 'Это занимает слишком долго', 'err.timeout.m': 'Источник не ответил вовремя. Повторите попытку.',
      'err.network.t': 'Не удалось подключиться', 'err.network.m': 'Соединение прервалось. Проверьте сеть и повторите.',
      'err.badData.t': 'Что-то пошло не так', 'err.badData.m': 'Источник прислал неожиданный ответ. Повторите чуть позже.',
      'err.region.t': 'Недоступно в этом регионе', 'err.region.m': 'Этот источник отсюда недоступен. Попробуйте другой.',
      'err.device.t': 'Недоступно на этом телевизоре', 'err.device.m': 'Этот источник не работает на этом телевизоре. Попробуйте другой.',
      'err.gone.t': 'Больше недоступно', 'err.gone.m': 'Этот источник или фильм удалён.',
      'err.rate.t': 'Слишком много запросов', 'err.rate.m': 'Подождите несколько секунд и повторите.',
      'err.rateWait.m': 'Подождите {s} сек. и повторите.',
      'err.notFound.t': 'Не найдено', 'err.notFound.m': 'Этот фильм не найден. Возможно, он удалён.',
      'err.request.t': 'Что-то пошло не так', 'err.request.m': 'Источник не смог обработать этот запрос.',
      'err.unavailable.t': 'Временно недоступно', 'err.unavailable.m': 'Источник сейчас не отвечает. Повторите чуть позже.',
      'err.notAvailable.t': 'Недоступно', 'err.notAvailable.m': 'Этот фильм недоступен.',
      'err.generic.t': 'Что-то пошло не так', 'err.generic.m': 'Повторите попытку.'
    }
  };

  var NATIVE_NAMES = { en: 'English', uz: "O'zbekcha", ru: 'Русский' };
  var active = 'en';

  function detect(tag) {
    var t = String(tag || '').toLowerCase();
    if (t.indexOf('uz') === 0) return 'uz';
    if (t.indexOf('ru') === 0) return 'ru';
    return 'en';
  }

  function resolve(override, navigatorLanguage) {
    return STRINGS[override] ? override : detect(navigatorLanguage);
  }

  function t(key, vars) {
    var table = STRINGS[active] || STRINGS.en;
    var s = Object.prototype.hasOwnProperty.call(table, key) ? table[key] : STRINGS.en[key];
    if (s === undefined) return key;
    if (vars) {
      s = s.replace(/\{(\w+)\}/g, function (m, name) {
        return vars[name] === undefined ? m : String(vars[name]);
      });
    }
    return s;
  }

  var api = {
    STRINGS: STRINGS,
    LANGS: ['en', 'uz', 'ru'],
    NATIVE_NAMES: NATIVE_NAMES,
    detect: detect,
    resolve: resolve,
    set: function (lang) { active = STRINGS[lang] ? lang : 'en'; },
    get: function () { return active; },
    t: t
  };
  Sozo.I18n = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
