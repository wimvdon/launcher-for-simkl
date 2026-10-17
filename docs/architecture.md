# Architecture

## Overview

Launcher for Simkl is a single-module, single-activity Android TV app. Compose renders state; two repositories own the state and every outside call. There is no server of its own: the launcher talks directly to SIMKL's API and static data files, and to Android (installed apps, TvProvider, accessibility).

```
 MainActivity (HOME + LEANBACK_LAUNCHER) ─ AppRoot (stack of Route, dialogs)
                   │            ├─ HomeScreen (backdrop + focused title, Apps row, SIMKL rows, other apps' channels)
                   │            ├─ AllAppsScreen, SettingsScreen, SignInScreen
                   ▼
 LauncherApp (Application, one instance)
   ├─ OkHttpClient (50 MB HTTP cache) ─── also used by Coil for images (wsrv.nl)
   ├─ SettingsStore      DataStore: token and user (device), pinned apps, channels, player (backed up)
   ├─ SimklApi           api.simkl.com + data.simkl.in, headers, PIN sign-in
   ├─ ContentRepository  library + playback StateFlow, incremental sync, library.json, trending lists
   ├─ AppsRepository     installed apps (launcher intents), banners and icons, package changes
   ├─ PlayerLauncher     opens a title in Stremio or Nuvio (deep link, IMDb id lookup)
   ├─ SystemChannels     other apps' home screen channels (only when Android allows it)
   └─ HomeChannel        publishes "Up next" to the system home screen (TvProvider)
 HomeButtonService (accessibility service, optional) ── opens MainActivity on Home
 OpenActivity ── started from the system home screen's channel; opens the title like a tile does
```

## Components

| Component | Responsibility |
|---|---|
| `LauncherApp` | Creates the shared HTTP client, API client and repositories lazily; provides Coil's image loader; installs `CrashReporter`; keeps the system home screen channel in step with the rows. |
| `SimklApi` | The only class that builds HTTP requests. Adds `simkl-api-key`, `client_id`, `app-name`, `app-version` and the bearer token to API calls; leaves data.simkl.in URLs untouched and needs no client ID for them (so trending works in a build without one). |
| `Parsers` | Maps `JsonElement` responses to the app's models by hand (shared approach with Tracker for Simkl). Pure Kotlin, unit tested. |
| `ContentRepository` | Holds `ContentState` (library snapshot, trending lists, sync error). Loads `library.json` at start, syncs when the launcher comes to the front and the last sync is older than 15 minutes, refreshes trending hourly, writes the snapshot atomically. Looks up IMDb ids titles were listed without. |
| `SignInFlow` | SIMKL's PIN sign-in: shows the code, polls at SIMKL's interval until approved or expired. |
| `Rows`, `ChannelConfig` | Pure logic: which SIMKL rows show, in which order, and what is in them. |
| `DeepLinks`, `PlayerChoice` | Pure logic: Stremio and Nuvio links (see `docs/api.md`) and which app opens a title. |
| `PinnedApps`, `AppSorting`, `StockHome` | Pure logic: pinned row order, all-apps sorting, which home screens the override replaces. |
| `AppsRepository` | Lists launchable apps (TV and phone launcher intents, via `<queries>`), loads banners and icons, refreshes on install/remove, opens apps and their App info. |
| `HomeChannel` | Writes one preview channel ("Simkl: Up next") with Continue watching and Up next to TvProvider. Only data the launcher already has; no API call. |
| `SystemChannels` | Reads other apps' preview channels when the launcher holds `ACCESS_ALL_EPG_DATA`, which Android grants only to system apps. |
| `HomeButtonService` | Accessibility service, off until the user turns it on. Filters the Home key (short press opens the launcher, long press the device settings) and opens the launcher when the stock home activity comes to the front. Reads no window content. |
| `DefaultHome` | Asks Android to make the launcher the home app (RoleManager on Android 10+, else Home settings), and opens the accessibility settings. |
| `CrashReporter` | Saves an uncaught exception's stack trace and shows it once on the next start. Nothing is sent. |

## Data flow

**Start.** `LauncherApp` reads `library.json` and the installed apps; the first start pins the streaming apps (Stremio, Nuvio), YouTube and the store when installed. `MainActivity.onResume` calls `refreshIfStale()`.

**Sync** (only while signed in, only in the foreground):

1. `POST /sync/activities` returns timestamps per type.
2. No previous sync: `GET /sync/all-items/` loads everything.
3. Unchanged `all` timestamp: nothing to download for the lists.
4. Otherwise `GET /sync/all-items/?date_from=<last all>` for changed items, plus `?extended=simkl_ids_only` when `removed_from_list` changed (`SyncPlan`, `LibraryMerge`).
5. `GET /sync/playback` for Continue watching, then the snapshot is saved.

A 401/403 signs out (the token is gone or revoked) and the Sign in row returns.

**Rows.** `Rows.build` turns the snapshot and trending lists into the visible rows: Continue watching (paused, newest first), Up next (watching, next episode aired, not paused, most recently watched first), Plan to watch (newest first), Trending TV shows / movies / anime (SIMKL's daily files). Personal rows are hidden while signed out. At most 30 tiles per row.

**Opening a title.** `PlayerChoice.decide` picks the app (the setting when installed, else the only one installed, else ask). `PlayerLauncher` looks up the IMDb id when the tile lacks one (`GET /{type}/{id}`), builds the deep link and starts it with the app's package, so the other app (Nuvio also answers `stremio://`) doesn't interfere. An app without deep link support opens at its start screen.

**Home button.** On devices with a home app setting, `MainActivity` answers `CATEGORY_HOME` and `DefaultHome.request` asks Android to make it the default. On Google TV and Fire TV, `HomeButtonService` opens `MainActivity` with `CATEGORY_HOME`; `onNewIntent` then returns to the top of the home screen.

## Patterns

- **Repository + unidirectional state:** screens collect `StateFlow`s and call repository functions; no ViewModels (same as Tracker for Simkl).
- **Offline first:** the snapshot is the source of truth for the personal rows; the network only updates it.
- **Manual JSON mapping** instead of `@Serializable` API models; the on-disk snapshot does use `@Serializable` classes.
- **Remote first:** every screen sets initial focus (`requestAfterLayout`), every action is a focusable element, and long-press (hold OK) replaces context menus.

## Backup

Android's own backup (to the Google account) and device-to-device transfer copy only the
`backed_up_settings` DataStore file: pinned apps, channel order and visibility, the app titles
open in, and the home screen channel switch. The rules are an allow list, so nothing else is ever
copied (`res/xml/data_extraction_rules.xml` for Android 12+, `res/xml/backup_rules.xml` for
Android 8-11, guarded by `BackupRulesTest`).

| Stays on this device | Why |
|---|---|
| `device` DataStore: SIMKL token and user name | The sign-in must not leave the device. |
| `library.json` | Downloaded again after signing in. |
| `last_crash.txt`, HTTP and image caches | Only about this device. |

The file names are in `DataFiles` (`SettingsStore.kt`).
