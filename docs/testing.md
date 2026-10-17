# Testing

## Automatic (CI, on every pull request and push to `main`)

| Check | Command | Catches |
|---|---|---|
| Release script tests | `python3 -m unittest discover -s scripts/tests` | Version maths, keystore-secret decoding |
| Unit and launch tests | `./gradlew testDebugUnitTest` | Logic bugs in plain-Kotlin classes; start-up and composition crashes (`LaunchTest`) |
| Lint | `./gradlew lintDebug` | Android API misuse (minSdk 26), missing translations, TV requirements (banner, Leanback launcher), accessibility, hard-coded text (errors fail the build) |
| Debug build | `./gradlew assembleDebug` | Compile errors; produces the APK to test on the TV |
| Release build | `./gradlew assembleRelease` | Problems caused by R8 minification / resource shrinking |

The debug APK (`launcher-for-simkl-<version>-debug.apk`) and the test and lint reports are
attached to every run. The release workflow runs the unit tests and lint again before building a
signed APK.

Run all of it locally before pushing:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
```

## What is covered

| Test | Subject |
|---|---|
| `data/ParsersTest` | `Parsers` against SIMKL's documented response shapes: library items (all types, next episode, unknown statuses), id-only lists, playback (anime from the library, progress capped), trending files (bare and wrapped), PIN code and status, user, title ids |
| `data/SyncTest` | `Activities` from `/sync/activities`, `SyncPlan` (first sync, nothing changed, changes, removals), `LibraryMerge` |
| `data/ChannelsTest` | `ChannelConfig` (saved order with unknown and new channels, moving, hiding), `Rows`: Continue watching order and art, Up next (aired, not paused, watching only), Plan to watch order, hidden and empty rows, row length cap |
| `data/DeepLinksTest` | Stremio and Nuvio links for movies, shows and episodes, search and TMDB fallbacks, when an IMDb lookup is needed, `PlayerChoice` |
| `data/AppsTest` | Pinning, unpinning and moving apps, uninstalled apps, first-start defaults, saving; all-apps sorting; which home activities the Home button override replaces (only the stock home activity, never the launcher or the system resolver) and its bounce protection |
| `data/BackupRulesTest` | Backup and device transfer copy only `backed_up_settings`, never the sign-in |
| `ui/LaunchTest` | Starts `MainActivity` under Robolectric (API 35) signed out and signed in; the channel programme intent keeps the title; foreign intents open nothing |

## Strategy

- Decisions about what the user sees (rows, links, pinned apps, which home screen to replace) are
  plain Kotlin and unit tested, so the Android parts only carry data.
- The launch test replaces an emulator for start-up crashes.
- Not covered automatically: `SimklApi` requests, `ContentRepository` syncing, the PIN polling
  loop, `AppsRepository` (package manager), `HomeChannel` and `SystemChannels` (TvProvider),
  `HomeButtonService` (accessibility), and remote navigation on a real TV. These need a TV or
  emulator; check them by hand (below).

### Writing tests

- Every logic change gets a test in `app/src/test/kotlin/`, mirroring the package.
- Keep logic free of Android types so it runs on the JVM; use Robolectric only for start-up and
  composition.

## Manual checks on the TV

For each pull request (before or after it is merged: merging only waits for green CI), install the
debug APK from the CI run ("Launcher for Simkl Dev", next to the released app) and follow the steps
in the pull request. Before a release, also check:

- [ ] The home screen opens with the pinned apps focused; every row, button and tile can be
      reached with the D-pad, and focus is always visible.
- [ ] Sign in with the PIN code; Continue watching, Up next and Plan to watch fill.
- [ ] OK on an episode opens it in Stremio (and in Nuvio when chosen in Settings).
- [ ] Hold OK on an app in All apps › Pin; it appears in the Apps row; move it and unpin it.
- [ ] Settings › Home screen: the default home app choice (Android TV), or the Home button
      override (Google TV): Home opens the launcher, holding Home opens the TV's settings.
- [ ] After restarting the TV, the launcher takes over from the built-in home screen.
- [ ] Android TV home screen: the "Simkl: Up next" channel appears and opens titles.
- [ ] Language: the TV's language set to Nederlands: all text is translated.
- [ ] After a release: install the release APK over the previous one. It updates without signing out.

## Reading a crash from a release build

Release builds are minified. Download `launcher-for-simkl-<version>-mapping.txt` from the matching
GitHub Release and decode the trace with R8's `retrace` (Android SDK
`cmdline-tools/latest/bin/retrace`):

```bash
retrace launcher-for-simkl-<version>-mapping.txt stacktrace.txt
```

The in-app crash report (shown at the next start) gives you the stack trace and the version.
