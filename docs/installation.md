# Installation

For developers who build the launcher. To just install it, download the APK from [Releases](https://github.com/wimvdon/launcher-for-simkl/releases) and follow the [usage guide](usage.md).

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 or newer | CI uses Temurin 21. Android Studio's bundled JBR works. |
| Android SDK | Platform 37 | `compileSdk` and `targetSdk` are 37. |
| Device or emulator | Android 8.0+ (API 26) | An Android TV or Google TV emulator image, or a real TV with ADB debugging on. |
| SIMKL client ID | any SIMKL app | From [simkl.com/settings/developer](https://simkl.com/settings/developer). The PIN sign-in needs no redirect URI, so Tracker for Simkl's client ID works too. Without one, the launcher starts and shows trending rows but cannot sign in. |
| Git | any | Debug builds are versioned with `git describe`. |

## Build and run

```bash
git clone https://github.com/wimvdon/launcher-for-simkl.git
cd launcher-for-simkl
echo "simklClientId=<your client ID>" >> ~/.gradle/gradle.properties
./gradlew installDebug
```

`installDebug` installs on the connected TV (`adb connect <tv-ip>` first). Or open the project in Android Studio and press **Run**.

The checks CI runs:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
```

## Testing a pull request

Open the pull request › **Checks** › **CI** › **Artifacts** and download `launcher-for-simkl-<version>-debug.apk`. Install it on the TV with a downloader app or `adb install`. Debug builds have the application ID `io.github.impca201.launcherforsimkl.debug` and the name **Launcher for Simkl Dev**, so they install next to the released launcher with their own sign-in and settings. When both are installed, the Home button override and the default home app setting are per app: turn on the one you're testing.

## Release builds

Signed APKs are built only by `.github/workflows/release.yml`. It needs, under **Settings › Secrets and variables › Actions**:

| Name | Kind | Value |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | secret | The keystore file as base64 |
| `ANDROID_KEYSTORE_PASSWORD` | secret | Keystore password |
| `ANDROID_KEY_ALIAS` | secret | Key alias |
| `ANDROID_KEY_PASSWORD` | secret | Key password |
| `SIMKL_CLIENT_ID` | variable | The SIMKL client ID (not a secret, per SIMKL's API rules). Also used by CI for the debug APK. |

The names without `ANDROID_` also work. The workflow decodes the keystore with `scripts/decode_keystore.py`, checks the password, alias and key password before building, runs the tests and lint, and builds with `-PappVersionName`, `-PappVersionCode` and `-PsimklClientId`.

## Signing key

Releases are signed with your own key, stored as four **repository secrets**. You need a
computer with Java once (any JDK, or Android Studio, which includes one).

**Create the keystore.** Pick a strong password and use it for both prompts (with a PKCS12
keystore, the key password *is* the keystore password).

macOS / Linux (Terminal):
```sh
keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
Windows (PowerShell). If `keytool` is not found, use Android Studio's copy:
`& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"` instead of `keytool`.
```powershell
keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 `
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
It asks for your name etc. Any values are fine; they are visible in the APK's certificate.

**Encode it as base64** (this copies it to the clipboard):

| System | Command |
|---|---|
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\upload.jks")) \| Set-Clipboard` |
| macOS | `base64 -i upload.jks \| pbcopy` |
| Linux | `base64 -w 0 upload.jks \| xclip -selection clipboard` (or `base64 -w 0 upload.jks > upload.b64` and copy the file's content) |

**Add the secrets.** In the repository: **Settings → Secrets and variables → Actions →
New repository secret**, four times:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the base64 text from the clipboard |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `upload` (the `-alias` you used) |
| `ANDROID_KEY_PASSWORD` | the key password (same as the keystore password for PKCS12) |

Or with the GitHub CLI: `gh secret set ANDROID_KEYSTORE_BASE64 < upload.b64`, and
`gh secret set ANDROID_KEYSTORE_PASSWORD` (it prompts for the value), and so on.

The release workflow is forgiving about copy/paste mistakes in the base64 secret (line breaks,
quotes, missing `=` and so on). If something is still wrong, the run explains what without showing
the secret. It also checks the passwords and alias before building.

## Backing up the key

Store **`upload.jks` and both passwords** in at least two safe places, e.g. a password manager
(as an attachment) and an encrypted USB stick. Do **not** put them in the repository, email or
chat. GitHub secrets cannot be read back, so they are not a backup. Then delete `upload.b64` and
any other copies on your computer that aren't backups.

## Making a release

**Actions › Release › Run workflow** on `main`, choose `patch`, `minor` or `major` (or `none` to only build an APK artifact). Pushing a tag such as `v1.2.3` also releases. The versionCode is `major × 10000 + minor × 100 + patch`; the first `minor` release is `0.1.0`.

It refuses to publish when not on `main`, when the tag or release exists, when the version is not higher than the last release, or when nothing changed. Each release has `launcher-for-simkl-<version>.apk` and `launcher-for-simkl-<version>-mapping.txt`.

## GitHub settings

Apply these once per repository (**Settings**):

1. **General → Pull Requests**: allow **squash merging** (you may untick merge commits and
   rebase merging) and tick **Automatically delete head branches**.
2. **Branches → Add branch ruleset** (or classic *branch protection rule*) for `main`:
   - Require a pull request before merging.
   - Require status checks to pass → add **`Test, lint and build`** (the CI job; it appears in the
     list after CI has run once).
   - Block force pushes.

   ⚠️ Branch protection/rulesets on **private** repositories need **GitHub Pro** (or Team).
   On a free account they work only for **public** repositories. Without it, CI still runs and
   shows ✅/❌ on every pull request, but GitHub won't *stop* you merging a red one, so only
   merge when the checks are green.

The same settings with the [GitHub CLI](https://cli.github.com/) (replace `OWNER/REPO`):
```sh
gh repo edit OWNER/REPO --enable-squash-merge --delete-branch-on-merge
gh api -X PUT repos/OWNER/REPO/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["Test, lint and build"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

