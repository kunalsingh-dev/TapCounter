# Tap Counter (Naam Jap counter): final version

Counts with the screen, any Bluetooth headset button and the phone volume keys. Works with the screen off.

## Deploy from your phone (Termux + GitHub Actions)
1. Download the zip, then in Termux:
   ```
   cd ~ && rm -rf final && mkdir final && unzip -oq ~/storage/downloads/TapCounter-FINAL2.zip -d final
   bash ~/final/TC-FINAL2/deploy.sh
   ```
2. Paste a fresh GitHub token (classic, scopes **repo** + **workflow**) when asked, then delete it on GitHub.
3. github.com/YOUR_USER/TapCounter/actions: wait ~10 min for the green tick.
4. Releases > newest build > the .apk > install over the old app (same signing key, rising version number).
If a step is red: open it, copy the last 40 log lines and send them.

## First run
* The app opens on a **calculator**. Type **1234** and press **=** to open the counter (change it in Settings).
* Counter menu (the three dots): History, Analytics, Settings, Hide.
* Tap **Enable headset + volume buttons** (allow notifications + Bluetooth).

## Features
* **Counting:** tap anywhere (instant), headset buttons (single click instant), phone volume keys (a held key counts once).
  Touch speed Slow/Normal/Fast/Turbo. 27/54/108 beads per mala. Daily goal. Mala speed.
* **Stealth:** hold 4 or 5 fingers (default 1 s) to enter/leave. Screen goes black, dimmest brightness, status bar hidden.
  Leaving stealth shows the calculator.
* **Calculator disguise + face unlock:** the secret code opens the counter. Optionally your FACE opens it by itself, and
  another face (or two faces) switches back to the calculator. Set up in Settings > Face unlock.
* **History:** days with hour-by-hour charts, and a reset log with date and time.
* **Analytics:** 7/30-day bars, hour-of-day bars, totals, average, best day, streak, mala speed, goal.
* **Reset:** "current mala" or "ALL malas", only in Settings or History, never from headphones. Logged with date and time.
* **Look:** god name (any text, colour, size), picture, 6 themes + custom colour picker, background picture.
* The screens adapt to portrait, landscape, small phones and tablets.

## New in this version
* **Several counters:** Menu > Counters. Each has its own count, history, name, picture and its OWN secret code: type that
  code on the calculator and = to open that counter.
* **Stealth disguises:** Settings > Stealth: Black, fake Alarm, Clock, System update, Charging. They are only drawn (not tappable),
  so a tap anywhere still counts. OLED or LCD/IPS mode (dimmest-brightness option for LCD screens).
* **App icon and name:** Settings > App icon and name: Calculator (default), Clock or Tap Counter. The app is listed as
  "Calculator" in Android's app list and the background notification says "Calculator / Running".
* **Rapid Bluetooth taps:** Rapid-tap mode counts every new Play/Pause press, even if the headset skips the release signal.
* **Face re-confirm + security levels:** Normal / Strict / Maximum (blink + re-check every 20 s). The phone's own face/fingerprint
  can also be allowed (long-press the calculator number).

## Honest limits
* Bluetooth headset volume keys and fingerprint-sensor presses can't be read by apps.
* The headset's long-press "voice command" goes straight to Gemini / Google Assistant; apps cannot override it. Turn that gesture off
  in the earbuds' app or Google settings so the press arrives as a normal media key.
* iPhone Face ID uses an infrared 3D sensor. A normal phone camera cannot match that. The strongest option available to an app is the
  phone's own biometric (allowed in Settings); the in-app face check is a convenience disguise.
* LCD / IPS screens cannot be fully blacked out by an app; use a dim disguise and LCD mode.
* Many earbuds keep double/triple/long press for themselves. Use Learn mode to see what yours sends.
* Face unlock is a convenience disguise, not bank-grade security (look-alikes or a photo might fool it; bad light can miss you).
  The camera runs only while the app is on screen, and Android shows its camera-in-use dot.
* The calculator code and the face are the only ways in. Remember your code.
* Set the app's battery to Unrestricted so Android doesn't stop headset control.

## Project layout
```
app/src/main/cpp/                C++17 gesture engine + JNI
app/src/main/java/.../headset/   headset service, session, settings, rules
app/src/main/java/.../face/      face detection + recognition (MobileFaceNet), gate logic
app/src/main/java/.../ui/        Compose screens + shared UI kit
app/src/main/assets/             MobileFaceNet.tflite (see docs/THIRD_PARTY.md)
app/src/test/                    native + Kotlin unit tests
.github/workflows/android.yml    CI: tests + APK + GitHub Release
```
