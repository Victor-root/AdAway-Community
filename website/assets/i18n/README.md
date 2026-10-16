# Site translations

One file per language, named after the code the picker uses. Each one registers a
single table:

```js
window.AAC_I18N = window.AAC_I18N || {};
window.AAC_I18N['fr'] = { 'nav.why': "Pourquoi", … };
```

English is not here: it lives in `assets/js/main.js` as the base language, because
it is what the markup already says and it is the fallback whenever a key or a
whole file is missing. A file that fails to load leaves the page in English
rather than half-translated.

## Corrections are welcome

These were translated in one pass and no native speaker has reviewed most of
them. If something reads wrong, awkward, or plainly machine-made in your
language, a pull request changing that one string is genuinely useful. Nothing
else has to be touched.

## Adding a language

1. Copy an existing file, rename it to the language code, and translate the
   values. Keep every key: a missing one silently falls back to English.
2. Add an entry to `LANGS` in `assets/js/main.js`, with the language named in
   itself (`Français`, not `French`), and `rtl: true` if it is written right to
   left.
3. If a browser sends a tag that should map to your language but does not match
   the code, extend `normalize()` in the same file.

Keep the language list in step with the application's own, which lives in
`app/src/main/res/xml/locales_config.xml`.
