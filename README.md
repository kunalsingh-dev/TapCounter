# Tap Counter: Bluetooth headphone buttons → counter

Any standard Bluetooth headset works (no device-specific code). Buttons are captured through an
active `MediaSessionCompat` owned by a `connectedDevice` foreground service.

## Build and install entirely from your phone (GitHub Actions)

You need: a free GitHub account, the Termux app (install from F-Droid, not the Play Store), and this zip.

1. **GitHub**: in the mobile browser create a new empty repository (e.g. `TapCounter`, private is fine).
2. **Token**: GitHub → Settings → Developer settings → Personal access tokens → *Tokens (classic)* →
   Generate new token, tick **repo** and **workflow**, copy it.
3. **Termux**, one-time setup:
   ```
   termux-setup-storage
   pkg update -y && pkg install -y git unzip
   git config --global user.name "YourName"
   git config --global user.email "you@example.com"
   ```
4. **Push** (adjust the zip name and your GitHub user/repo):
   ```
   cd ~
   cp ~/storage/downloads/TapCounter-complete.zip .
   unzip TapCounter-complete.zip && cd TapCounter
   git init -b main
   git add -A && git commit -m "Tap Counter headset control"
   git remote add origin https://github.com/YOUR_USER/TapCounter.git
   git push -u origin main
   ```
   When asked: username = your GitHub name, password = the token.
5. **Wait** 8–12 min: GitHub app/website → repo → **Actions** → "Android CI". Two jobs: `tests` and `build`.
6. **Install**: repo → **Releases** → newest "Build N" → tap `app-debug.apk` → open it → allow
   "install unknown apps" for your browser when asked. New builds install over old ones (fixed debug key).
7. **If a step is red**: open it, copy the last ~40 log lines and send them to me; I'll fix the cause.

To change code later on the phone: edit files in the GitHub web editor (or in Termux), commit, and a new APK is built.

## Using it
* **Count:** tap anywhere on the screen (a finger resting longer than 0.45 s does not count), press any Bluetooth
  headset button (single click = +1, instant), or press a phone volume key (a held key counts once).
* **Stealth:** hold FOUR or FIVE fingers on the screen for 1 s (adjustable 0.3-5 s) to enter and again to leave. On headphones,
  hold Play/Pause ~1.2 s. In stealth the screen is dimmed to the lowest level, the time bar is hidden, and a tap still counts.
* **Calculator disguise:** leaving stealth (and starting the app) shows a working calculator. Type your secret code
  (default **1234**, change it in Settings) and press = to open the counter. Menu ⋮ -> Hide also shows the calculator.
* **Backgrounds:** Settings -> Background: 6 colour themes and an optional picture.
* **Resets:** Settings -> Reset: 'current mala' (ring to 0, everything else kept) or 'ALL malas' (all-time to 0). Both are
  logged with date and time in History.
* **No decrement, no reset on the main screen.** Reset exists only in Settings → Danger zone (confirmed). Headphones and
  volume keys can never reset or decrement.
* **History:** menu (⋮) → History: every count is saved automatically by date and by hour.
* **Mala:** 27/54/108 beads (Settings). Ring shows progress, "Today's Mala Count" shows completed malas, a long triple
  buzz marks each completed mala (Settings → Vibration).
* **Look:** Settings → god name (any text), colour, size and an optional picture.
* **Any Bluetooth device:** Settings → Learn mode captures whatever key your device sends.

## Honest limits (read this)
* **Volume up/down on Bluetooth headsets are not key events.** Most headsets use AVRCP absolute volume:
  the headset changes its own volume and just tells the phone. Nothing arrives, so they can't count.
* **Reliable on most headsets:** Play/Pause (or the single hook button), Next, Previous.
* Many earbuds process double/triple taps themselves and send *Next/Previous*. That is why Learn mode exists.
* **Long press** is frequently reserved by the headset (voice assistant, ANC) and sends nothing or goes to the assistant.
* Our own double/triple/long detection only works when the headset sends raw press/release events.
* Single clicks are delayed by the multi-click window (default 350 ms); lower it for snappier counting.
* Conflict detection is a heuristic. Android has no API for "who owns the media buttons".
* The service must be running; it does not auto-start at boot.
* Aggressive battery savers (some Xiaomi/Huawei/Samsung modes) kill background services. Exempt the app.

## Keep-alive audio trade-off
Android prefers the app that most recently *played* audio. A session that only claims "playing" can lose
priority. So the service loops 1 s of ±1-LSB samples (≈ −90 dBFS, inaudible) with no audio focus requested
(it never pauses your music). Cost: Bluetooth stays in A2DP streaming mode → a little more battery on phone
and headset. Toggle: Settings → Keep-alive silent audio.

## Layout
```
app/src/main/cpp/            C++17 gesture engine + JNI (libtapgesture.so)
app/src/main/java/.../headset/  settings, rules, session manager, service, controller
app/src/main/java/.../ui/       Compose screens (counter, settings, learn mode)
app/src/test/cpp/            native tests (run_native_tests.sh)
app/src/test/java/           Kotlin unit tests (RESET rule, codec, DataStore)
docs/                        TEST_CHECKLIST.md, headset-study-note.html
.github/workflows/android.yml  CI: tests + APK + GitHub Release
```

## Adding this to your existing app instead
Copy `cpp/`, the `headset/` package, `HeadsetService`, the manifest entries and the Gradle bits.
Make your counter implement `CounterActions` (increment, decrement, toggleStealth, forceStealthOn).
It intentionally has no reset(). Rename the package in Kotlin and in `jni_bridge.cpp` (`Java_com_example_...`).
