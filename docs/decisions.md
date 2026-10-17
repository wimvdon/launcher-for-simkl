# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-17: Launcher for Simkl, from the template

The repository was created from the android-app template; this is the first version of the app.

- **What it is:** a home screen for Android TV and Google TV. Pinned apps on top, SIMKL rows
  ("channels") below, titles open in Stremio or Nuvio. Rejected: a regular app with a SIMKL
  channel on the built-in home screen only (Google TV hardly shows app channels, and the owner
  asked for a launcher).
- **Application ID `io.github.impca201.launcherforsimkl`**, matching Tracker for Simkl's
  `io.github.impca201.trackerforsimkl`. It can't change after the first release.
- **minSdk 26 (Android 8.0)**, not the template's 31. Many TVs and boxes run Android 9-11 (the
  Nvidia Shield runs 11), and 8.0 is where home screen channels (TvProvider preview programmes)
  start. No dynamic colour is lost: TVs have no wallpaper colours.
- **TV Material 3 (`androidx.tv:tv-material` 1.1.0), a new dependency,** instead of phone Material 3:
  its cards, list items, buttons and switches show focus for a remote (scale, border, container
  colour), which phone components only hint at. Phone Material 3 is not included at all. The shared
  design kit (`SettingsKit` and friends) is phone-only, so this app doesn't carry it;
  `DESIGN.md` "This app" lists the differences. Dark theme only.
- **Code reused from Tracker for Simkl** by copying, not by a shared library: `JsonExt`, the shape
  of `SimklApi` and `Parsers`, `Images`, `Dates`, `CrashReporter`, `SettingsStore`'s two-file
  backup split, and the same libraries (OkHttp, kotlinx.serialization, Coil, DataStore,
  Robolectric). Rejected: a shared module or library (a second repository to publish and version
  for a few hundred lines).
- **Sign-in with SIMKL's PIN flow** (`/oauth/pin`), made for TVs: a code to enter at simkl.com/pin
  on a phone. Rejected: the browser redirect the Tracker uses (most TVs have no usable browser).
  The launcher only reads SIMKL; marking watched stays with Stremio, Nuvio and the Tracker.
- **Same SIMKL client ID mechanism as the Tracker** (`-PsimklClientId`, CI variable
  `SIMKL_CLIENT_ID`). The PIN flow needs no redirect URI, so the Tracker's client ID can be reused.
  Trending rows (data.simkl.in) work without one.
- **Deep links by IMDb id**, the id both Stremio (Cinemeta) and Nuvio use, started with an explicit
  package because Nuvio also answers `stremio://`. A title listed without an IMDb id is looked up
  once; Stremio falls back to search, Nuvio to its TMDB link.
- **Changing the home app where Android won't let you:** an accessibility service that filters the
  Home key and replaces the stock home activity when it comes to the front, as Projectivy Launcher
  and Button Mapper do. It is off until the user turns it on, reads no window content and only
  reacts to the stock home *activity* (so Google TV's settings panel keeps working). Long-pressing
  Home opens the TV's settings, which the override would otherwise take away. For TVs that block
  even that, the app and the usage guide give ADB commands (allow restricted settings, enable the
  service, optionally disable Google TV's home). Rejected: requiring root, and disabling the stock
  launcher by default (it removes Google TV's settings panel and profiles).
- **Channels:** the launcher's own rows are configurable channels; "Up next" is also published as
  a preview channel on the built-in home screen (TvProvider, local data only). Other apps' channels
  are read only when Android grants `ACCESS_ALL_EPG_DATA`, which it reserves for system apps, so a
  normal installation can't show them. Rejected: scraping them some other way (there is none) and
  Watch Next entries (Stremio and Nuvio publish their own; duplicates).
- **No screen transition animation library:** screens fade in (150ms). `AnimatedContent` makes
  focus requests fail until it finishes, which leaves a remote without focus.
- **Compose's `BringIntoViewSpec` was tried** to keep the focused row and tile at a fixed position,
  and dropped: at the ends of a list it kept asking for a scroll the list couldn't make, which
  looped. The focused row scrolls to the top with `animateScrollToItem` instead, which stops at the
  ends.

## 2026-10-17: Help text only where the screen doesn't speak for itself

The owner found the apps over-explanatory. A scan of every hint, summary and help paragraph removed text that restated a label, described standard controls (drag handles, tapping a row) or explained the app's design, and shortened the rest to what the screen can't show: consequences, where data goes, unfamiliar terms. `DESIGN.md` ("Wording") now says so, and the Dynamic color supporting text is one shared phrase without the app's colour name. Rejected: removing all help text (consequences and privacy notes are information, not decoration) and tooltips or info buttons instead (hidden text people rarely open, and more to translate).

## 2026-10-16: One empty state for every app, setup as a full-screen state

A second Material 3 audit found empty screens drawn three different ways: an icon in a tinted circle with a title (Tracker for Simkl), a title and text whose icon didn't show (RSS Reader), and a grey icon with one line of text (Noter for Notion). Apps that can't work until something is set up showed it differently too: Time Tracker for Notion put a small banner above a disabled timer, Noter filled the screen, Snoozer did both. Material 3 asks an empty state to say what it means and offer the next useful step, so the shared kit gained `ui/theme/EmptyState.kt` and `DESIGN.md` a section "Empty and setup states": one centred layout for empty, no-result, failed and setup-needed screens, with setup-needed replacing the screen's content. The same audit added two smaller rules: button labels name the action in as few words as possible ("Gezien", not "Markeren als gezien"), and rows that scroll sideways keep one height. Rejected: illustrations per screen (more to draw and translate for each app, and Material 3 warns against decoration that hides the next step) and keeping the banner for setup (easy to miss, and it leaves a screen full of controls that do nothing).

## 2026-10-16: Settings are one list; top-level navigation is a docked bar

An audit of the apps against Material 3 found two structural problems. Settings were a stack of collapsed cards, so even a single switch took two taps to find and every section was boxed in a card, which Material 3 lists as an anti-pattern. Settings are now one list grouped by section headers, with `ListItem` rows (switches apply at once) and only setup instructions folded away (`SettingsKit.kt`: `SettingsSection`, `SwitchSetting`, `SettingItem`, `ExpandableSetting`, `SettingsBlock`). Some apps put their top-level sections in a floating pill bar; Material 3 Expressive reserves floating toolbars for actions on the current screen, and keeps the navigation bar docked for destinations, so the shared rules now require the docked `NavigationBar`. `DESIGN.md` gained a "Navigation and structure" section. Rejected: a settings home page that links to sub-pages (more taps for apps with a handful of settings) and keeping the collapsible cards with everything expanded (still a card per section).

## 2026-10-15: Android's backup copies settings only, from an allow list

Users expect their settings back after a reset or on a new phone, and Android does that for free with its own backup (Google account) and phone-to-phone transfer. The template now lists exactly one file, the DataStore `backed_up_settings`, in `data_extraction_rules.xml`; everything else stays on the phone. An allow list means a file added later (a sign-in token, device state) is never backed up by accident, and `BackupRulesTest` fails if the rules drift. Rules for what goes where are in `docs/architecture.md`, "Backup". Rejected: backing up everything with exclusions (one forgotten exclusion leaks a token into the backup), switching backup off (users lose their settings) and our own backup to Google Drive (needs network access, sign-in and more code for what Android already does).

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-15: Code is "all rights reserved" by default

Without a `LICENSE` file the legal default is already all rights reserved, but that isn't visible and is easy to misread for a public repository. The template now ships an explicit all-rights-reserved `LICENSE`, so every app starts strict and the owner opts in to sharing. Switching to a permissive licence (MIT or Apache-2.0) is a one-file swap, described in the README. Rejected: a commented-out alternative inside `LICENSE` (GitHub and licence scanners read the whole file, so it would be misdetected) and starting with MIT (opening up later is easy, taking it back for code already shared is not).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-13: Template baseline

- **Language/UI:** Kotlin + Jetpack Compose + Material 3, single `app` module. This is Google's
  recommended, mainstream stack with the most documentation and examples.
- **Tool versions (latest stable on this date):** Gradle 9.8.1, Android Gradle Plugin 9.4.1
  (uses its built-in Kotlin support, so no separate `kotlin-android` plugin), Kotlin 2.4.21
  (Compose compiler plugin), Compose BOM 2026.09.00. Dependabot keeps these current.
- **minSdk 31 (Android 12).** The owner installs apps on their own, recent phone, so older
  Android versions aren't worth the extra code and testing. Android 12 brings Material You dynamic
  colour (no fallback code path), the modern splash screen and per-app language settings. It still
  covers the large majority of active devices. Lower it only if someone with an older phone needs
  the app.
- **compileSdk / targetSdk 37 (Android 17)**, the latest stable API level. The minor SDK release
  37.2 exists but isn't needed by anything yet.
- **Java 17 bytecode, JDK 21 to build.** JDK 21 is what Claude Code cloud sessions provide, so local
  checks match CI exactly.
- **Version numbers:** release `X.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, passed in by the release
  workflow. All other builds use `git describe --tags --long` (e.g. `1.2.0-3-gabc1234`), falling back to
  `0.0.0-dev`, so a dev build can never be mistaken for a release. Version logic lives in
  `scripts/release_version.py` (unit tested) and `app/build.gradle.kts`.
- **Debug builds use `applicationIdSuffix = ".debug"`** so a pull request's APK installs next to the
  released app instead of conflicting with it.
- **Releases are GitHub Releases with a signed APK**, published by `gh release create` (preinstalled
  on runners) instead of a third-party action, which means one fewer dependency with write access.
  The R8 mapping file is attached to each release.
- **Signing secrets** are base64 and decoded by a tolerant, tested script (`scripts/decode_keystore.py`)
  that never prints the secret. The key password is checked with a small Java program
  (`scripts/VerifySigningKey.java`), because `keytool` ignores the key password of PKCS12 keystores and
  the build would otherwise fail later with an unclear error.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT licensed). The default
  "enhanced" provider is closed source under separate terms of use.
- **CI artifacts:** the debug APK is uploaded unzipped (`archive: false`) so it can be installed on a
  phone straight from the download.
- **Languages:** English (default) + Dutch, as a working example of translations. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.
