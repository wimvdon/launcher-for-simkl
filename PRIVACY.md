# Privacy policy

_Last updated: 2026-10-17_

Launcher for Simkl is an unofficial home screen for Android TV that shows what you watch on
[SIMKL](https://simkl.com). It has no analytics, advertising or third-party crash reporting.

## What the app sends, and to whom

- **SIMKL (api.simkl.com).** When you sign in, the app asks SIMKL for a short code that you enter
  at simkl.com/pin on your phone or computer; SIMKL then gives the app an access token. The app
  uses SIMKL's API to read your lists, your paused playback and your user name, and to look up a
  title's IMDb id when it needs one to open the title. It never changes your lists. Every request
  carries the app's public client ID and name and version; requests for your data carry your
  access token. The app only talks to SIMKL while it is on screen, never in the background.
- **SIMKL's data files (data.simkl.in).** The trending lists are downloaded as public files.
  Nothing about you is sent with them.
- **Images (wsrv.nl).** Posters and backgrounds are loaded through the wsrv.nl image proxy that
  SIMKL asks apps to use. The proxy sees which image is requested and your IP address.
- **Stremio and Nuvio.** Opening a title hands its IMDb (or TMDB) id, its episode, or its name to
  that app on your TV. What it does next is under its own privacy policy.

## What the app stores on your device

- Your SIMKL access token and user name.
- Your pinned apps, channel order, which channels are shown, the app titles open in, and whether
  "Up next" is shown on the TV's home screen.
- A copy of your library and paused playback, so the home screen opens fast and works offline.
- An HTTP and image cache.
- If the app crashes: the error's stack trace with the app version and device model, shown to you
  the next time the app opens. It is deleted after it is shown and never sent anywhere.

Only your settings (pinned apps, channels, the app titles open in, the home screen channel) are
included in Android's own backup to your Google account, if you turned that on, and in a transfer
to a new device. Not your sign-in (you sign in again) and not the library copy (downloaded again).
Signing out deletes the token and the library copy; uninstalling the app deletes everything.

## The Home button override

Google TV and some other devices always open their own home screen when you press Home. If you
turn on the override (Android's Accessibility settings), Android tells the app when the Home button
is pressed and when the built-in home screen comes to the front, so it can open itself instead.
That is all it receives: it can't read what is on the screen or what you type, and it sends
nothing anywhere. You can turn it off in the same place at any time.

## Permissions

- **Internet** and **network state**: to talk to SIMKL and load pictures.
- **Write home screen channels** (`WRITE_EPG_DATA`): to show "Up next" as a channel on the TV's
  built-in home screen. Only what the launcher already shows; you can turn it off in Settings.
- **Read all home screen channels** (`ACCESS_ALL_EPG_DATA`): to show other apps' channels. Android
  only grants it to apps that are part of the system, so on a normal installation the app never has
  it and doesn't show them.
- **Which apps are installed** (through Android's app list for launchers, not "all packages"):
  to show and open your apps. This list never leaves the device.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue at https://github.com/wimvdon/launcher-for-simkl/issues.
