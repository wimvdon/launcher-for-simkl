# Usage guide

For people using the launcher on a TV. It assumes the APK is installed (see the [README](../README.md#quick-start)).

## The home screen

- **Top:** the focused title's backdrop, name, type, year, episode and description; with an app or
  button focused, today's date. Top right: the time, **All apps** and **Settings**.
- **Apps:** your pinned apps, then the **All apps** tile. The first start pins Stremio, Nuvio,
  YouTube and the Play Store when they are installed.
- **SIMKL rows ("channels")**, in the order set in Settings:
  - **Continue watching:** movies and episodes you paused (in an app that reports playback to
    SIMKL), with how far you got.
  - **Up next:** the next episode of every show you're watching that has aired.
  - **Plan to watch:** your watchlist, the latest added first.
  - **Trending TV shows / movies / anime:** SIMKL's trending titles of today. These work without
    signing in.
- **Channels from other apps** appear below, only on devices where Android allows it (see below).

Press **OK** on a title to open it in Stremio or Nuvio. **Hold OK** to choose the app for this
title. Press **Home** at any time to come back to the top.

## Signing in to SIMKL

1. **Settings › Account › Sign in** (or the **Sign in** tile on the home screen).
2. On your phone or computer, go to **simkl.com/pin** and enter the code the TV shows.
3. The TV continues by itself and fills Continue watching, Up next and Plan to watch.

The code is valid for about 15 minutes; **New code** starts again. **Sign out** (Settings ›
Account) deletes the sign-in and the copy of your library from the TV. The launcher only reads
your SIMKL lists; mark episodes watched in Stremio, Nuvio or Tracker for Simkl.

## Opening titles in Stremio or Nuvio

**Settings › Open titles in:** **Ask every time**, **Stremio** or **Nuvio**. If only one of them
is installed, titles open there without asking.

- **Stremio** opens the title's page, or the exact episode for Continue watching and Up next.
  Titles SIMKL knows no IMDb id for open Stremio's search.
- **Nuvio** opens the title's page (Nuvio has no link to one episode).

## Apps

- **All apps** lists every app A to Z. **OK** opens an app; **hold OK** to pin it to the home
  screen, unpin it, or open its App info.
- On the home screen, **hold OK** on a pinned app to move it left or right, or unpin it.

## Channels

**Settings › Channels:** select a row to turn it on or off; the arrows on the right move it up or
down. Rows that need SIMKL show "Shows after you sign in" while you're signed out.

- **Up next on the TV's home screen** (Android TV only): also publishes Continue watching and Up
  next as a channel, "Simkl: Up next", on the built-in home screen, for when you use it. Selecting
  a title there opens it like a tile here.
- **Channels from other apps:** Android only shares these with the built-in home screen (the
  permission is reserved for system apps). On a normal TV they can't show here; on a rooted device
  where the launcher is installed as a privileged system app they appear below the SIMKL rows.

## Making it the home screen

### Android TV with a home app setting (Nvidia Shield, many Sony, Philips, TCL TVs)

**Settings › Home screen › Default home app**, then choose **Launcher for Simkl**. On some
devices this opens **Settings › Apps › Default apps › Home** (or **Home screen**) instead.

### Google TV, Chromecast with Google TV, Google TV Streamer, Fire TV

These always open their own home screen and have no setting to change it. Use the **Home button
override**:

1. **Settings › Home screen › Home button override** opens Android's Accessibility settings.
2. Choose **Launcher for Simkl: Home button** and turn it on.
3. Press **Home**: the launcher opens. **Hold Home** for the TV's own settings.

If the switch is greyed out ("restricted setting"), Android 13+ blocks it for apps installed
from outside the Play Store: open **Settings › Apps › Launcher for Simkl**, press the menu (⋮)
and choose **Allow restricted settings**, then try again. If your TV has no such menu, use a
computer (below).

After a restart the built-in home screen shows briefly before the launcher takes over.

### With a computer (ADB)

For TVs that block the steps above. Turn on **Developer options › USB debugging** (or **Network
debugging**) on the TV, install [Android platform tools](https://developer.android.com/tools/releases/platform-tools)
on the computer, and connect:

```bash
adb connect <tv-ip-address>
```

Allow the override's restricted setting:

```bash
adb shell appops set io.github.impca201.launcherforsimkl ACCESS_RESTRICTED_SETTINGS allow
```

Turn the override on (this replaces any other accessibility service you turned on):

```bash
adb shell settings put secure enabled_accessibility_services io.github.impca201.launcherforsimkl/.home.HomeButtonService
adb shell settings put secure accessibility_enabled 1
```

Optional, Google TV only: turn off the built-in home screen entirely, so Android uses this
launcher as its home app. Google TV's settings panel and profiles go with it; reach the TV's
settings from the launcher's **Settings › Home screen › TV settings** instead.

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

To undo it:

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
```

The launcher's **Settings › Home screen › Set up with a computer** shows the same commands, with
the right names for a test (Dev) build.

## Languages

English, Dutch, French, German, Spanish and Italian, following the TV's language. On Android 13+
a different language can be chosen for the app alone in the TV's app settings.
