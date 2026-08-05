# Custom wake-word models

`Jarvis` is a Porcupine built-in keyword and works with no files in this directory.
Every other phrase in `services/WakePhrase.kt` (`WakePhrases.ALL`) needs a trained
Porcupine model dropped here before it will actually fire — until then,
`NuaForegroundService` just skips it (see `availableWakePhrases()`), it doesn't crash.

## Adding a wake word NUA doesn't have yet

1. Go to [Picovoice Console](https://console.picovoice.ai/) (same account as the
   `PICOVOICE_ACCESS_KEY` used to build the app) → **Porcupine** → **Create Wake Word**.
2. Type the phrase, choose **Android** as the target platform, train it, and download
   the exported `.ppn` file.
3. Drop it in this directory, named to match the `assetFileName` already declared for
   that phrase in `WakePhrases.ALL` — for example `hey_nua_android.ppn` for "Hey NUA".
   If you're adding a phrase that isn't in the catalog yet, add one line to
   `WakePhrases.ALL` first (id, display text, and the asset file name you're about to
   add here) — that's the only code change needed.
4. Rebuild. The phrase shows up as "Active" in Settings → Wake words, and
   `NuaForegroundService`'s persistent notification lists it, with zero other changes.

## Currently declared, awaiting a trained model

- `hey_nua_android.ppn` — "Hey NUA"
- `hello_nua_android.ppn` — "Hello NUA"
- `nua_android.ppn` — "NUA"
- `daddys_home_android.ppn` — "Daddy's Home"
- `wake_up_sleepy_head_android.ppn` — "Wake up Sleepy Head"
