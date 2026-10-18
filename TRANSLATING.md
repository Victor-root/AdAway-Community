# Translating to your language

Translations are made by volunteers, and a pull request is the way to send one. A translation is a few text files, so it is a good first contribution, and a fix of a single wrong string is as welcome as a whole new language.

## Where the translations are

English is the source of every text. Each language has its own copy, named after its language code (`fr` for French, `pt-rBR` for Brazilian Portuguese in the Android folders):

| What | Where | Source in English |
|:--|:--|:--|
| The texts of the app | `app/src/main/res/values-<code>/strings*.xml` | `app/src/main/res/values/strings*.xml` |
| The help pages | `app/src/main/res/raw-<code>/help_faq.html` and `help_problems.html` | `app/src/main/res/raw/` |
| The store descriptions | `metadata/<code>/` (`title.txt`, `short_description.txt`, `full_description.txt`) | `metadata/en-US/` |
| The website | `website/assets/i18n/<code>.js` | the `EN` table of `website/assets/js/main.js` |

A text that is missing in a language shows in English, so a translation can be sent in parts.

## Fixing a translation

1. Fork the repository and find the text in the file of your language.
2. Fix it and keep what is not text as it is: the placeholders (`%1$s`, `%d`), the HTML tags of the help pages, and the escaped characters (`\'`, `\n`).
3. Open a pull request. One pull request per language makes it easier to review.

## Adding a language

1. Copy the files of the English source into the folders of your language, named with the language code, and translate the values. Keep the language code to the basic one (`sr`) rather than a regional one (`sr-rRS`), unless the regions really differ, as for Portuguese.
2. The app finds its languages from the `values-<code>` folders: nothing else has to be declared for it.
3. For the website, copy an existing file of `website/assets/i18n/`, translate the values and add the language to the `LANGS` list of `website/assets/js/main.js`, named in itself (`Français`, not `French`).
4. Open a pull request.

## Good to know

- The first translations of the app come from the 6.x versions. The texts added since, and most of the website, were translated by machine and have not been read by a native speaker, so a review is always useful.
- Do not translate the name of the app, the names of the lists, or the technical names (`VPN`, `DNS`, `hosts`).
- Keep a text about as long as the English one: the buttons and the TV screens have little room.
