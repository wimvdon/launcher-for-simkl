# Project structure

```
launcher-for-simkl/
├── .editorconfig                     Editor/ktlint rules
├── .gitattributes                    LF everywhere; *.bat → CRLF; keystores/images binary
├── .github/
│   ├── dependabot.yml                Weekly Gradle and GitHub Actions update PRs
│   ├── pull_request_template.md      Plain-language PR description + checklist
│   └── workflows/
│       ├── ci.yml                    Script tests, unit + launch tests, lint, builds, debug APK
│       └── release.yml               Release rules → verify signing key → build APK → publish GitHub Release
├── CLAUDE.md                         Rules for Claude Code sessions
├── DESIGN.md                         Design language shared with the sibling apps (+ "This app")
├── LICENSE                           Copyright notice: all rights reserved
├── PRIVACY.md                        Privacy policy
├── README.md
├── docs/                             This documentation
├── scripts/                          Release helpers, shared with the android-app template
├── build.gradle.kts                  Root build: plugin declarations
├── settings.gradle.kts               Repositories, single :app module, APK name prefix
├── gradle/libs.versions.toml         Version catalog (every dependency version)
└── app/
    ├── build.gradle.kts              App ID, versions, SIMKL client ID, signing, R8, lint
    ├── proguard-rules.pro
    └── src/
        ├── debug/res/values/strings.xml      "Launcher for Simkl Dev" name for debug builds
        ├── main/
        │   ├── AndroidManifest.xml            Permissions, <queries>, HOME activity, service
        │   ├── kotlin/io/github/impca201/launcherforsimkl/
        │   │   ├── LauncherApp.kt             Application: shared objects, image loader, home channel
        │   │   ├── CrashReporter.kt           Shows the last crash on the next start
        │   │   ├── data/
        │   │   │   ├── SettingsStore.kt       DataStore files (backed up / device)
        │   │   │   ├── ContentRepository.kt   Library, playback, trending; sync; IMDb lookup
        │   │   │   ├── AppsRepository.kt      Installed apps, pictures, opening apps
        │   │   │   ├── PlayerLauncher.kt      Opens titles in Stremio / Nuvio
        │   │   │   ├── SignInFlow.kt          PIN sign-in
        │   │   │   ├── api/                   SimklApi (HTTP), Parsers, JSON helpers
        │   │   │   └── model/                 Plain-Kotlin models and logic: Models, Sync,
        │   │   │                              Channels (rows), Players (deep links), Apps
        │   │   │                              (pinned row), StockHome (override targets)
        │   │   ├── home/                      HomeButtonService, DefaultHome
        │   │   ├── tv/                        HomeChannel (publish), SystemChannels (read)
        │   │   └── ui/
        │   │       ├── MainActivity.kt        Home activity; Home presses and resumes
        │   │       ├── OpenActivity.kt        Opens a title from the system home screen
        │   │       ├── AppRoot.kt             Screen stack, dialogs, wiring
        │   │       ├── home/                  HomeScreen, Clock
        │   │       ├── apps/                  AllAppsScreen
        │   │       ├── settings/              SettingsScreen
        │   │       ├── signin/                SignInScreen (and the shared message layout)
        │   │       ├── components/            Tiles, dialogs, focus helper
        │   │       └── theme/                 Colours, shapes, TV spacing
        │   └── res/                           Strings (6 languages), icon, banners, XML configs
        └── test/kotlin/…                      Unit tests (data/) and LaunchTest (ui/)
```
