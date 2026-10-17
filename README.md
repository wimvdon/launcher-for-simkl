# Launcher for Simkl

## Documentation
| | |
|---|---|
| [Architecture](docs/architecture.md) | System design, components and flow |
| [Structure](docs/structure.md) | Project organization and responsibilities |
| [Installation](docs/installation.md) | Building, signing, testing a pull request and releasing |
| [Technical decisions](docs/decisions.md) | Trade-offs and design justifications |
| [Usage guide](docs/usage.md) | Setting it up as the home screen, channels, pinned apps |
| [API](docs/api.md) | SIMKL endpoints used, PIN sign-in, Stremio and Nuvio deep links |
| [Testing](docs/testing.md) | How to run tests and what they cover |

---

## Description

An unofficial home screen (launcher) for Android TV and Google TV built around [Simkl](https://simkl.com). Not affiliated with or endorsed by Simkl; "Simkl" is Simkl's trademark and only describes the service the app works with.

- **What it does:** a TV home screen with your pinned apps on top and SIMKL rows ("channels") below: Continue watching, Up next, Plan to watch and today's trending TV shows, movies and anime. Selecting a title opens it in **Stremio** or **Nuvio**. All apps are one button away.
- **What problem it solves:** Google TV's home screen is built around Google's own recommendations and ads, and doesn't let you choose another home app. This launcher shows what *you* are watching, and its Home button override makes it the home screen on Google TV too.
- **Real use case:** turn on the TV, the launcher shows the next episode of the show you're watching; press OK and Stremio opens on that episode.

English, Dutch, French, German, Spanish and Italian. Android 8.0+ (TVs, boxes and sticks; it also runs on phones and tablets). See the [privacy policy](PRIVACY.md).

Source available for reference; all rights reserved. See [LICENSE](LICENSE).

## Quick start

Install the latest `launcher-for-simkl-x.y.z.apk` from [Releases](https://github.com/wimvdon/launcher-for-simkl/releases) on the TV (with a downloader app, or `adb install`), open **Launcher for Simkl**, then:

1. **Settings › Sign in**, and enter the code at simkl.com/pin on your phone.
2. **Settings › Home screen**: choose it as the default home app, or on Google TV turn on the **Home button override**.

Details, including setup with a computer for devices that block both: [docs/usage.md](docs/usage.md).

## Technologies used

| Area | Technology |
|---|---|
| Language | Kotlin 2.4, coroutines, `StateFlow` |
| UI | Jetpack Compose with Material 3 for TV (`androidx.tv:tv-material`), Material icons |
| Networking | OkHttp 5 (50 MB HTTP cache), kotlinx.serialization JSON, Coil 3 for images |
| Storage | Jetpack DataStore (settings, token), a JSON snapshot file (library cache) |
| Android TV | TvProvider (home screen channel), an accessibility service (Home button override), RoleManager (default home app) |
| Build | Gradle 9.8 (Kotlin DSL, version catalog), AGP 9.4, R8 |
| CI/CD | GitHub Actions: tests, lint and a debug APK on every pull request; signed releases; Dependabot |
| Tests | JUnit 4, Robolectric (launch test) |

## Architecture (summary)

A single-activity Compose app. `LauncherApp` owns one OkHttp client, the `SimklApi` client (code shared with [Tracker for Simkl](https://github.com/wimvdon/tracker-for-simkl)), the `ContentRepository` (library, playback and trending as a `StateFlow`, synced from `/sync/activities` while the launcher is on screen) and the `AppsRepository` (installed apps). Plain-Kotlin logic builds the rows (`Rows`), the deep links (`DeepLinks`) and the pinned apps (`PinnedApps`). Details: [docs/architecture.md](docs/architecture.md).

## Project structure

```
launcher-for-simkl/
├── .github/            CI and release workflows, PR template, Dependabot
├── CLAUDE.md           Rules for Claude Code sessions
├── scripts/            Release helpers (versioning, keystore secret) with tests
├── app/src/main/kotlin/io/github/impca201/launcherforsimkl/
│   ├── LauncherApp.kt  Application: HTTP client, API, repositories, image loader
│   ├── data/           Repositories, settings, SIMKL client, parsers, models and logic
│   ├── home/           Home button override, default home app
│   ├── tv/             Android TV home screen channels
│   └── ui/             Compose screens, theme
└── app/src/test/       JVM unit tests and the Robolectric launch test
```

Full tree: [docs/structure.md](docs/structure.md).
