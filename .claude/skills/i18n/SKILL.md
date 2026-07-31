---
name: i18n
description: Keep Android string resources correct and in sync across locales — extract hardcoded strings, add and audit translations, handle plurals and formatting. Use for "add a translation", "check the locales", "extract these strings", "is the Spanish up to date".
---

# String resources and localization

The default locale (`res/values/strings.xml`) is the source of truth. Every other `res/values-<locale>/strings.xml` mirrors its keys. A key present in one and missing in another means those users see the wrong language mid-screen.

## Audit

Compare key sets across locales:

```sh
grep -oP '(?<=<string name=")[^"]+' app/src/main/res/values/strings.xml | sort > /tmp/base.txt
grep -oP '(?<=<string name=")[^"]+' app/src/main/res/values-es/strings.xml | sort > /tmp/es.txt
comm -23 /tmp/base.txt /tmp/es.txt   # in default, missing from es
comm -13 /tmp/base.txt /tmp/es.txt   # in es, orphaned
```

Do the same for `<plurals>` and `<string-array>`. Android Lint's `MissingTranslation` and `ExtraTranslation` checks cover this too — run `./gradlew lintDebug` and do not suppress those rules.

## Find hardcoded strings

Any user-visible literal in Kotlin or XML is a localization bug and an accessibility bug (screen readers read what is there, in whatever language it happens to be):

```sh
grep -rn 'android:text="[^@]' app/src/main/res/layout/
grep -rnE 'Text\(\s*"' app/src/main/java/
```

Extract to `strings.xml` with a descriptive key, then reference via `stringResource(R.string.key)` in Compose or `getString()` elsewhere.

## Naming keys

`<screen_or_component>_<element>_<variant>`: `settings_voice_speed_label`, `editor_delete_content_desc`. Name by *purpose*, never by content — a key called `ok_button` survives a copy change; a key called `press_ok_to_continue` does not.

Content descriptions get their own keys, suffixed `_content_desc`. They are user-visible text and must be translated like everything else.

## Getting it right

- **Placeholders**: use positional arguments (`%1$s`, `%2$d`) not bare `%s`, because word order changes between languages and a translator must be able to reorder them.
- **Plurals**: use `<plurals>`, never `if (n == 1)` in code. Languages have between one and six plural categories; the two-branch conditional is only ever correct for English-like languages.
- **Never concatenate** translated fragments to build a sentence. Grammar does not survive it. One string, one complete sentence, with placeholders.
- **Escaping**: apostrophes must be `\'` or the resource fails to compile — the most common Spanish/French/Italian breakage.
- **`translatable="false"`** on strings that must not be translated (package names, URLs, format patterns), so they stop appearing in translation queues.
- **Length**: translations commonly run 30% longer than English. Check the layout still fits — see the `compose-ui` skill on font scaling; the same overflow problems apply.

## Adding a locale

1. Create `res/values-<locale>/strings.xml` with every key from the default.
2. If the app declares IME subtypes or locale lists, add the locale there too (`res/xml/method.xml`, `locales_config.xml`).
3. If the app has an in-app language picker, add it to that list.
4. Run the app with the locale forced (`adb shell am start ... ` after setting the device language, or a `@Preview(locale = "es")`) and look at every screen for overflow.

## When you cannot translate

Add the key with the default-language text and an XML comment marking it untranslated, rather than omitting it. A missing key can crash or fall back invisibly; a marked one is a visible, greppable TODO.
