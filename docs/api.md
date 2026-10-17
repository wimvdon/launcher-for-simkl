# API and entry points

The launcher has no API of its own. This page lists what it calls (SIMKL), the links it opens in
other apps (Stremio, Nuvio), and what other apps and Android can start.

## SIMKL API

Base URL `https://api.simkl.com`, implemented in `data/api/SimklApi.kt` (shared approach with
Tracker for Simkl). Every API request sends:

| Where | Name | Value |
|---|---|---|
| Header | `simkl-api-key` | The app's client ID (`BuildConfig.SIMKL_CLIENT_ID`) |
| Header | `Authorization` | `Bearer <access token>` once signed in |
| Header | `User-Agent` | `launcher-for-simkl/<version>` |
| Query | `client_id`, `app-name`, `app-version` | Client ID, `launcher-for-simkl`, version name |

Files on `data.simkl.in` get no extra query parameters and need no client ID.

### Endpoints used

| Method | Path | Used for |
|---|---|---|
| GET | `/oauth/pin` | Start the PIN sign-in: `user_code`, `verification_url`, `expires_in`, `interval` |
| GET | `/oauth/pin/{user_code}` | Poll every `interval` seconds until `result: OK` with `access_token` |
| POST | `/sync/activities` | What changed since the last sync |
| GET | `/sync/all-items/` (`extended=full`, `next_watch_info=yes`, `date_from`) | Full or incremental library |
| GET | `/sync/all-items/?extended=simkl_ids_only` | Which titles are still in the lists (after a removal) |
| GET | `/sync/playback` | Paused episodes and movies (Continue watching) |
| GET | `/{tv,anime,movies}/{id}` | IMDb and TMDB ids of a title listed without them |
| POST | `/users/settings` | User name |
| GET | `https://data.simkl.in/discover/trending/{tv,anime,movies}/today_100.json` | Trending rows |

Images are loaded from `https://wsrv.nl/?url=https://simkl.in/...` (the image proxy SIMKL asks
apps to use). The launcher never writes to SIMKL.

### Rules followed

- `/sync/activities` first, then only what changed (`SyncPlan`).
- Syncs only while the launcher is on screen and at most every 15 minutes (or after signing in);
  trending at most hourly. No background calls; the home screen channel is written from the last
  sync's data.
- The PIN sign-in polls at the `interval` SIMKL returns and stops when the code expires.
- Trending rows credit SIMKL ("Trending data from Simkl").

### Errors

- A non-2xx response throws `ApiException(code, message)`; `401`/`403` signs out and brings back
  the Sign in tile.
- A build without a client ID never calls the API: sign-in explains it, trending still works, and
  a signed-in launcher shows "This build can't reach your Simkl lists".
- Network errors keep the cached rows and show "Offline: showing your last sync".

## Deep links into Stremio and Nuvio

Built in `data/model/Players.kt` (`DeepLinks`), started with the app's package name so the other
app (Nuvio also answers `stremio://`) doesn't intercept it.

| App | Packages | Title | Link |
|---|---|---|---|
| Stremio | `com.stremio.one` | Movie | `stremio:///detail/movie/{imdb}/{imdb}` |
| | | Show | `stremio:///detail/series/{imdb}` |
| | | Episode | `stremio:///detail/series/{imdb}/{imdb}:{season}:{episode}` (season 1 when SIMKL gives none) |
| | | No IMDb id | `stremio:///search?search={title}` |
| Nuvio | `com.nuvio.tv`, `com.nuvio.app` | Movie or show | `nuvio://meta?type={movie,series}&id={imdb}` |
| | | Only a TMDB id | `nuvio://tmdb/{movie,tv}/{tmdb}` |

Sources: Stremio's [deep links documentation](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deep-links.md)
and Nuvio's `DeepLinkParser` ([NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV)). Before
building a link, a title without an IMDb id is looked up once (`GET /{type}/{id}`). When the app
doesn't accept the link (an old version), it opens at its start screen.

## Android TV home screen (TvProvider)

| What | Where | Notes |
|---|---|---|
| Publish | `TvContract.Channels` (`TYPE_PREVIEW`, internal id `up_next`) and `PreviewPrograms` | One channel, "Simkl: Up next", at most 20 programmes, rewritten when the rows change. `requestChannelBrowsable` once (an app's first channel is always shown). |
| Read | Other apps' browsable preview channels and their programmes | Only with `ACCESS_ALL_EPG_DATA` (signature/privileged). A programme opens its own `intent_uri`, without selector or URI grants. |

## Entry points for other apps and Android

| Entry point | Handled by | Effect |
|---|---|---|
| Launcher / Leanback launcher | `ui/MainActivity` | Opens the launcher. |
| `MAIN` + `HOME` | `ui/MainActivity` (`singleTask`) | The home screen; pressing Home while it's open returns to the top. |
| A programme of "Simkl: Up next" | `ui/OpenActivity` (exported, explicit intent with extras) | Opens that title in Stremio or Nuvio, asking which when needed. Incomplete intents do nothing. |
| Accessibility service | `home/HomeButtonService` | Only bound by Android (`BIND_ACCESSIBILITY_SERVICE`) after the user turns it on. |

The debug build (`io.github.impca201.launcherforsimkl.debug`) has the same entry points under its
own package.
