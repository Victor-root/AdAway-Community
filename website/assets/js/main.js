/* ============================================================
   AdAway Community - site behaviour
   Theme (system by default), language (browser by default),
   hero feed, nav.
   ============================================================ */
(function () {
  'use strict';

  /* ---------------------------------------------------------
     1. Base language
        English is inlined rather than shipped as a file: it is what the
        markup already says, it is the fallback for every other language,
        and having it here means a visitor whose language file fails to
        load still gets a readable page instead of a broken one.
     --------------------------------------------------------- */
  var EN = {
    'meta.desc': 'An unofficial community fork of AdAway: a system-wide, open source ad blocker for Android, with VPN-mode stability fixes and Android TV support.',

    'a11y.skip': 'Skip to content',
    'a11y.nav': 'Main',
    'a11y.lang': 'Language',
    'a11y.menu': 'Menu',
    'a11y.themeDark': 'Switch to dark theme',
    'a11y.themeLight': 'Switch to light theme',

    'nav.why': 'Why',
    'nav.how': 'How it works',
    'nav.features': 'Features',
    'nav.shots': 'Screenshots',
    'nav.get': 'Download',

    'hero.tag': 'Unofficial community fork',
    'hero.h1a': 'Ads stop',
    'hero.h1b': 'before they load.',
    'hero.sub': 'AdAway blocks ads and trackers across every app on your Android device, at the DNS level. No root required, no account, no traffic sent anywhere. This community fork keeps it stable on recent Android versions and adds Android TV support.',
    'hero.cta1': 'Download the APK',
    'hero.cta2': 'View source',
    'hero.cta3': 'Get it on Omnify',
    'hero.req': 'Android 8 Oreo or later · GPLv3 · No trackers, no ads, no telemetry by default',
    'hero.feed': 'DNS requests',
    'hero.feedBlocked': 'blocked',

    'why.kicker': 'The fork',
    'why.h2': 'Why this exists',
    'why.lead': 'AdAway is a great project, but its updates had slowed down and some useful fixes were left waiting. This fork keeps them alive in a build you can actually install.',
    'why.migTag': 'Version 7',
    'why.migT': 'Rebuilt from scratch',
    'why.migB1': 'AdAway relied on very old code and tools. Patching them any longer was not an option if the app was to stay stable, fast, secure and easy to maintain, so everything was rebuilt on modern foundations. It was a big job, but an unavoidable one.',
    'why.migB2': 'For you, nothing changes: same screens, same settings, and your lists are kept. Behind the scenes, everything is new.',
    'why.note': 'This is not a hostile fork: it is not affiliated with, endorsed by, or signed by the official AdAway maintainers.',

    'how.kicker': 'How it works',
    'how.h2': 'Blocking at the source',
    'how.lead': 'Ads and trackers must look up a domain name before they can load. AdAway answers those lookups with nothing, so the request never leaves your device, in every app.',
    'how.vpnTag': 'No root needed',
    'how.vpnT': 'VPN mode',
    'how.vpnB': 'A local VPN on your device inspects DNS requests only. No account, and nothing is sent to a remote server.',
    'how.rootTag': 'Root required',
    'how.rootT': 'Root mode',
    'how.rootB': 'On a rooted device, the blocklist is written straight into the system hosts file. The VPN slot stays free.',

    'feat.kicker': 'Features',
    'feat.h2': 'What you get',
    'feat.1t': 'Stable VPN mode',
    'feat.1b': 'No restart loops, and the app, the notification and the Quick Settings tile always agree. The VPN comes back on its own if your phone kills it.',
    'feat.2t': 'Android TV',
    'feat.2b': 'A full interface built for a remote, on TVs and TV boxes.',
    'feat.3t': 'Live DNS log',
    'feat.3b': 'See what each app asks for, then block, allow or redirect it in one tap.',
    'feat.4t': 'Your own lists',
    'feat.4b': 'Your own blocked, allowed and redirected hosts, and per-app VPN exclusions.',

    'shots.kicker': 'Screenshots',
    'shots.h2': 'See it running',
    'shots.1': 'Home screen: block on or off in one tap, with live counters.',
    'shots.2': 'Hosts sources: the blocklists feeding the app, refreshed on their own.',
    'shots.3': 'DNS log: what your apps actually resolve, in real time.',
    'shots.4': 'The Android TV home screen, built for a remote.',


    'get.kicker': 'Install',
    'get.h2': 'Get it',
    'get.1t': 'Download the APK',
    'get.1b': 'Grab the latest release and install it. Android asks once to allow installs from your browser.',
    'get.1c': 'Download',
    'get.2t': 'Track it with Omnify',
    'get.2b': 'Follow new releases and get updates alongside your other apps.',
    'get.2c': 'Get Omnify',
    'get.note': 'Updates keep your settings and data. Our signing key differs from the official AdAway one, so uninstall the official app first.',

    'foot.tagline': 'Unofficial community fork of AdAway',
    'foot.project': 'Project',
    'foot.releases': 'Releases',
    'foot.issues': 'Report an issue',
    'foot.contribute': 'Contribute',
    'foot.translate': 'Translate',
    'foot.upstream': 'Upstream',
    'foot.official': 'Official AdAway',
    'foot.license': 'Licensed under the GPLv3+, like the original AdAway. Created and maintained by the AdAway project contributors; this fork is not affiliated with or endorsed by them.'
  };

  /* ---------------------------------------------------------
     2. Languages
        The same list the application itself ships, each named in its own
        language. Everything except English lives in assets/i18n/<code>.js
        and is fetched only when it is actually needed.
     --------------------------------------------------------- */
  var LANGS = [
    { c: 'af', n: 'Afrikaans' },
    { c: 'ar', n: 'العربية', rtl: true },
    { c: 'ast', n: 'Asturianu' },
    { c: 'az', n: 'Azərbaycanca' },
    { c: 'be', n: 'Беларуская' },
    { c: 'bg', n: 'Български' },
    { c: 'bn', n: 'বাংলা' },
    { c: 'ca', n: 'Català' },
    { c: 'cs', n: 'Čeština' },
    { c: 'da', n: 'Dansk' },
    { c: 'de', n: 'Deutsch' },
    { c: 'el', n: 'Ελληνικά' },
    { c: 'en', n: 'English' },
    { c: 'eo', n: 'Esperanto' },
    { c: 'es', n: 'Español' },
    { c: 'es-MX', n: 'Español (Latinoamérica)' },
    { c: 'et', n: 'Eesti' },
    { c: 'eu', n: 'Euskara' },
    { c: 'fa', n: 'فارسی', rtl: true },
    { c: 'fi', n: 'Suomi' },
    { c: 'fil', n: 'Filipino' },
    { c: 'fr', n: 'Français' },
    { c: 'gl', n: 'Galego' },
    { c: 'he', n: 'עברית', rtl: true },
    { c: 'hi', n: 'हिन्दी' },
    { c: 'hr', n: 'Hrvatski' },
    { c: 'hu', n: 'Magyar' },
    { c: 'id', n: 'Bahasa Indonesia' },
    { c: 'is', n: 'Íslenska' },
    { c: 'it', n: 'Italiano' },
    { c: 'ja', n: '日本語' },
    { c: 'km', n: 'ភាសាខ្មែរ' },
    { c: 'ko', n: '한국어' },
    { c: 'ku', n: 'Kurdî' },
    { c: 'lt', n: 'Lietuvių' },
    { c: 'ml', n: 'മലയാളം' },
    { c: 'ms', n: 'Bahasa Melayu' },
    { c: 'my', n: 'မြန်မာ' },
    { c: 'ne', n: 'नेपाली' },
    { c: 'nl', n: 'Nederlands' },
    { c: 'no', n: 'Norsk bokmål' },
    { c: 'pa', n: 'ਪੰਜਾਬੀ' },
    { c: 'pl', n: 'Polski' },
    { c: 'ps', n: 'پښتو', rtl: true },
    { c: 'pt', n: 'Português' },
    { c: 'pt-BR', n: 'Português (Brasil)' },
    { c: 'ro', n: 'Română' },
    { c: 'ru', n: 'Русский' },
    { c: 'si', n: 'සිංහල' },
    { c: 'sk', n: 'Slovenčina' },
    { c: 'sl', n: 'Slovenščina' },
    { c: 'sq', n: 'Shqip' },
    { c: 'sr', n: 'Српски' },
    { c: 'sv', n: 'Svenska' },
    { c: 'ta', n: 'தமிழ்' },
    { c: 'th', n: 'ไทย' },
    { c: 'tr', n: 'Türkçe' },
    { c: 'uk', n: 'Українська' },
    { c: 'ur', n: 'اردو', rtl: true },
    { c: 'uz', n: 'Oʻzbekcha' },
    { c: 'vi', n: 'Tiếng Việt' },
    { c: 'zh', n: '简体中文' },
    { c: 'zh-TW', n: '繁體中文' }
  ];

  var BY_CODE = {};
  for (var li = 0; li < LANGS.length; li++) BY_CODE[LANGS[li].c] = LANGS[li];

  var LS_LANG = 'aac.lang';
  var LS_THEME = 'aac.theme';

  function store(key, value) {
    try { localStorage.setItem(key, value); } catch (e) { /* private mode */ }
  }
  function read(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  /* ---------------------------------------------------------
     3. Theme: follows the system unless the visitor chose one
     --------------------------------------------------------- */
  var mq = window.matchMedia('(prefers-color-scheme: dark)');
  var themeBtn = document.getElementById('themeBtn');
  var dict = EN;

  function systemTheme() {
    return mq.matches ? 'dark' : 'light';
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    if (themeBtn) {
      themeBtn.setAttribute('aria-label', theme === 'dark' ? t('a11y.themeLight') : t('a11y.themeDark'));
    }
  }

  applyTheme(read(LS_THEME) || systemTheme());

  // Keep following the system while no explicit choice has been made.
  var onSystemChange = function () {
    if (!read(LS_THEME)) applyTheme(systemTheme());
  };
  if (mq.addEventListener) mq.addEventListener('change', onSystemChange);
  else if (mq.addListener) mq.addListener(onSystemChange);

  if (themeBtn) {
    themeBtn.addEventListener('click', function () {
      var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      store(LS_THEME, next);
      applyTheme(next);
    });
  }

  /* ---------------------------------------------------------
     4. Language
        Detected from the browser, overridable from the picker, remembered
        afterwards. Anything unknown, and any file that fails to load, ends
        up on English rather than on a half-translated page.
     --------------------------------------------------------- */

  // Spanish-speaking regions that read better with the Latin American wording.
  var LATAM = {
    mx: 1, ar: 1, bo: 1, cl: 1, co: 1, cr: 1, cu: 1, do: 1, ec: 1, gt: 1, hn: 1,
    ni: 1, pa: 1, pe: 1, pr: 1, py: 1, sv: 1, us: 1, uy: 1, ve: 1, '419': 1
  };

  /**
   * Reduce a browser language tag to one of the codes above. Region is only
   * looked at where it changes the text (script, spelling or vocabulary);
   * everywhere else en-GB, en-AU and en all mean the same file.
   */
  function normalize(tag) {
    var parts = String(tag || '').toLowerCase().replace(/_/g, '-').split('-');
    var base = parts[0];
    var rest = parts.slice(1);
    function has(value) { return rest.indexOf(value) >= 0; }

    // Codes some browsers still send from the superseded ISO lists.
    if (base === 'iw') base = 'he';
    if (base === 'in') base = 'id';

    if (base === 'zh') return (has('hant') || has('tw') || has('hk') || has('mo')) ? 'zh-TW' : 'zh';
    if (base === 'pt') return has('br') ? 'pt-BR' : 'pt';
    if (base === 'es') return (rest.length && LATAM[rest[rest.length - 1]]) ? 'es-MX' : 'es';
    if (base === 'nb' || base === 'nn' || base === 'no') return 'no';
    if (base === 'tl' || base === 'fil') return 'fil';
    return base;
  }

  function detectLang() {
    var saved = read(LS_LANG);
    if (saved && BY_CODE[saved]) return saved;
    var list = navigator.languages && navigator.languages.length
      ? navigator.languages
      : [navigator.language || 'en'];
    for (var i = 0; i < list.length; i++) {
      var code = normalize(list[i]);
      if (BY_CODE[code]) return code;
    }
    return 'en';
  }

  function t(key) {
    return dict[key] || EN[key] || '';
  }

  var loaded = { en: EN };
  var pending = {};

  /**
   * Fetch a language file by adding a script tag, not with fetch(): a script
   * tag also works when the page is opened straight from disk, which is how
   * the site is previewed before it is published.
   */
  function loadLang(code, done) {
    if (loaded[code]) { done(loaded[code]); return; }
    if (pending[code]) { pending[code].push(done); return; }
    pending[code] = [done];

    var finish = function (result) {
      var waiting = pending[code] || [];
      delete pending[code];
      for (var i = 0; i < waiting.length; i++) waiting[i](result);
    };

    var script = document.createElement('script');
    script.src = 'assets/i18n/' + code + '.js';
    script.async = true;
    script.onload = function () {
      var table = (window.AAC_I18N || {})[code] || null;
      if (table) loaded[code] = table;
      finish(table);
    };
    script.onerror = function () { finish(null); };
    document.head.appendChild(script);
  }

  var descTag = document.querySelector('meta[name="description"]');
  var langBtn = document.getElementById('langBtn');
  var langPop = document.getElementById('langPop');
  var langName = document.getElementById('langName');
  var langCode = document.getElementById('langCode');
  var afterLang = [];

  function paint(code) {
    var entry = BY_CODE[code] || BY_CODE.en;
    var root = document.documentElement;
    root.setAttribute('lang', code);
    root.setAttribute('dir', entry.rtl ? 'rtl' : 'ltr');

    var nodes = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].textContent = t(nodes[i].getAttribute('data-i18n'));
    }
    var labelled = document.querySelectorAll('[data-i18n-aria]');
    for (var j = 0; j < labelled.length; j++) {
      labelled[j].setAttribute('aria-label', t(labelled[j].getAttribute('data-i18n-aria')));
    }
    if (descTag) descTag.setAttribute('content', t('meta.desc'));

    if (langName) langName.textContent = entry.n;
    if (langCode) langCode.textContent = entry.c.toUpperCase();
    var options = langPop ? langPop.querySelectorAll('.lang-opt') : [];
    for (var k = 0; k < options.length; k++) {
      options[k].setAttribute('aria-selected', options[k].getAttribute('data-code') === code ? 'true' : 'false');
    }

    // The theme button's label is translated too, so refresh it in place.
    applyTheme(root.getAttribute('data-theme') || systemTheme());

    for (var c = 0; c < afterLang.length; c++) afterLang[c]();
  }

  function setLang(code) {
    loadLang(code, function (table) {
      // A language whose file is missing or broken falls back to English
      // rather than leaving the page in a half-translated state.
      dict = table || EN;
      paint(table ? code : 'en');
    });
  }

  if (langPop) {
    for (var p = 0; p < LANGS.length; p++) {
      var option = document.createElement('button');
      option.type = 'button';
      option.className = 'lang-opt';
      option.setAttribute('role', 'option');
      option.setAttribute('data-code', LANGS[p].c);
      option.setAttribute('aria-selected', 'false');
      option.appendChild(document.createTextNode(LANGS[p].n));
      var badge = document.createElement('small');
      badge.textContent = LANGS[p].c;
      option.appendChild(badge);
      langPop.appendChild(option);
    }
  }

  function closeLangPop() {
    if (!langPop || langPop.hidden) return;
    langPop.hidden = true;
    if (langBtn) langBtn.setAttribute('aria-expanded', 'false');
  }

  if (langBtn && langPop) {
    langBtn.addEventListener('click', function (event) {
      event.stopPropagation();
      var open = langPop.hidden;
      langPop.hidden = !open;
      langBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
      if (open) {
        var selected = langPop.querySelector('[aria-selected="true"]');
        if (selected && selected.scrollIntoView) selected.scrollIntoView({ block: 'center' });
      }
    });

    langPop.addEventListener('click', function (event) {
      // Clicks inside the list must not reach the close-on-outside-click
      // handler below; picking an entry closes it explicitly instead.
      event.stopPropagation();
      var option = event.target.closest ? event.target.closest('.lang-opt') : null;
      if (!option) return;
      var code = option.getAttribute('data-code');
      store(LS_LANG, code);
      setLang(code);
      closeLangPop();
      langBtn.focus();
    });

    document.addEventListener('click', closeLangPop);
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') closeLangPop();
    });
  }

  setLang(detectLang());

  /* ---------------------------------------------------------
     5. Navigation
     --------------------------------------------------------- */
  var nav = document.getElementById('nav');
  var menuBtn = document.getElementById('menuBtn');
  var navLinks = document.querySelector('.nav-links');

  if (nav) {
    var onScroll = function () {
      nav.classList.toggle('stuck', window.scrollY > 8);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  if (menuBtn && navLinks) {
    menuBtn.addEventListener('click', function () {
      var open = navLinks.classList.toggle('open');
      menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    navLinks.addEventListener('click', function (event) {
      if (event.target.tagName === 'A') {
        navLinks.classList.remove('open');
        menuBtn.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* ---------------------------------------------------------
     6. Screenshot placeholders
        Keeps the layout honest before the real captures land.
     --------------------------------------------------------- */
  function markMissing(img) {
    var name = (img.getAttribute('src') || '').split('/').pop();
    var box = document.createElement('div');
    box.className = 'shot-missing ' + (img.className || '');
    box.textContent = name;
    if (img.parentNode) img.parentNode.replaceChild(box, img);
  }

  var shots = document.querySelectorAll('.phone img, .tvframe img');
  for (var s = 0; s < shots.length; s++) {
    (function (img) {
      img.addEventListener('error', function () { markMissing(img); });
      // The image may already have failed before this script ran, in which
      // case the error event is long gone; catch that state directly.
      if (img.complete && img.naturalWidth === 0) markMissing(img);
    })(shots[s]);
  }

  /* ---------------------------------------------------------
     7. Direct APK link
        The release asset is named after its version
        (AdAway.Community.v6.6.0-c.apk), so no fixed URL can point at
        it. The buttons therefore ship pointing at the latest release
        page, which always works and needs no script, and are upgraded
        here to the actual .apk once the API answers. Any failure
        (offline, rate limit, blocked request) simply leaves the
        working fallback in place.
        The answer is cached so a visitor costs one request per day.
     --------------------------------------------------------- */
  var apkLinks = document.querySelectorAll('[data-apk]');

  if (apkLinks.length && window.fetch) {
    var API = 'https://api.github.com/repos/Victor-root/AdAway-Community/releases/latest';
    var CACHE_KEY = 'aac.apk';
    var CACHE_MS = 24 * 60 * 60 * 1000;

    var setApkHref = function (url) {
      for (var i = 0; i < apkLinks.length; i++) apkLinks[i].setAttribute('href', url);
    };

    var cached = null;
    try { cached = JSON.parse(read(CACHE_KEY) || 'null'); } catch (e) { cached = null; }

    if (cached && cached.url && (Date.now() - cached.at) < CACHE_MS) {
      setApkHref(cached.url);
    } else {
      fetch(API, { headers: { Accept: 'application/vnd.github+json' } })
        .then(function (response) {
          if (!response.ok) throw new Error('HTTP ' + response.status);
          return response.json();
        })
        .then(function (release) {
          var assets = release.assets || [];
          for (var i = 0; i < assets.length; i++) {
            var name = (assets[i].name || '').toLowerCase();
            if (name.slice(-4) === '.apk' && assets[i].browser_download_url) {
              setApkHref(assets[i].browser_download_url);
              store(CACHE_KEY, JSON.stringify({ url: assets[i].browser_download_url, at: Date.now() }));
              return;
            }
          }
        })
        .catch(function () { /* keep the releases-page fallback */ });
    }
  }

  /* ---------------------------------------------------------
     8. Hero feed
     --------------------------------------------------------- */
  var feedList = document.getElementById('feedList');
  var feedCount = document.getElementById('feedCount');

  if (feedList) {
    var ENTRIES = [
      ['ads.doubleclick.net', true],
      ['graph.facebook.com', true],
      ['api.github.com', false],
      ['googleads.g.doubleclick.net', true],
      ['analytics.tiktok.com', true],
      ['cdn.jsdelivr.net', false],
      ['app-measurement.com', true],
      ['sdk.iad-01.braze.com', true],
      ['upload.wikimedia.org', false],
      ['track.adform.net', true],
      ['pagead2.googlesyndication.com', true],
      ['api.spotify.com', false],
      ['telemetry.mozilla.org', true],
      ['events.redditmedia.com', true],
      ['static.cloudflareinsights.com', true]
    ];

    var MAX_ROWS = 6;
    var index = 0;
    var blocked = 0;
    var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function label(isBlocked) {
      // "OK" reads the same everywhere, so only the blocked side is translated.
      return isBlocked ? t('hero.feedBlocked') : 'OK';
    }

    function push() {
      var entry = ENTRIES[index % ENTRIES.length];
      index++;

      var li = document.createElement('li');
      if (entry[1]) {
        li.className = 'b';
        blocked++;
        if (feedCount) feedCount.textContent = blocked;
      }

      var domain = document.createElement('span');
      domain.className = 'd';
      domain.textContent = entry[0];

      var tag = document.createElement('span');
      tag.className = 'f-tag ' + (entry[1] ? 'f-b' : 'f-o');
      tag.textContent = label(entry[1]);
      // The domain is data, never text to translate: keep it left-to-right
      // even on a right-to-left page, where it would otherwise be reordered.
      domain.setAttribute('dir', 'ltr');

      li.appendChild(domain);
      li.appendChild(tag);
      feedList.insertBefore(li, feedList.firstChild);

      while (feedList.children.length > MAX_ROWS) {
        feedList.removeChild(feedList.lastChild);
      }
    }

    for (var q = 0; q < MAX_ROWS; q++) push();

    // Rows already on screen keep their old wording when the language changes,
    // so relabel them instead of waiting for them to scroll out.
    afterLang.push(function () {
      var tags = feedList.querySelectorAll('.f-tag');
      for (var i = 0; i < tags.length; i++) {
        tags[i].textContent = label(tags[i].className.indexOf('f-b') >= 0);
      }
    });

    if (!reduced) {
      var timer = null;
      var start = function () { if (!timer) timer = setInterval(push, 1900); };
      var stop = function () { clearInterval(timer); timer = null; };

      document.addEventListener('visibilitychange', function () {
        if (document.hidden) stop(); else start();
      });

      if ('IntersectionObserver' in window) {
        new IntersectionObserver(function (entries) {
          entries[0].isIntersecting ? start() : stop();
        }, { threshold: 0.1 }).observe(feedList);
      } else {
        start();
      }
    }
  }
})();
