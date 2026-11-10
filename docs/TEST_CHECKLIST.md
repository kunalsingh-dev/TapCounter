# Real-device test checklist

Setup: install the APK, open the app, Enable headset control (allow notification + Bluetooth),
pair a headset, run Learn mode once and confirm the key name shown.

## A. Basic mapping (screen on)
- [ ] Learn mode shows a key name + code when you press Play/Pause. Nothing is counted while learning.
- [ ] Single click → +1 and one short buzz (after ≈ the multi-click window).
- [ ] Double click → −1. Triple click → stealth toggles (screen goes black / returns).
- [ ] Long press (hold > 600 ms) → stealth forced ON + double buzz; releasing does not add a count.
- [ ] Next / Previous: map them in Learn mode and confirm.
- [ ] Count never goes below 0.
- [ ] Try to find any way to assign Reset to a headphone button: the dropdown must never offer it.

## B. Phone speaker off
- [ ] Mute media volume on the phone (or route audio to the headset only). Buttons still count.
- [ ] Play nothing at all. Buttons still count (keep-alive on).
- [ ] Turn Keep-alive OFF in settings: note whether your headset still works (some do, some don't).
- [ ] Confirmation tick plays in the headset only (enable it in settings).

## C. Screen locked / background
- [ ] Lock the phone, wait 2 minutes, press buttons, unlock: count increased.
- [ ] Swipe the app away from recents: the notification stays, buttons still count.
- [ ] 30 minutes idle, screen off: still works. If not, exempt the app from battery optimisation and retest.
- [ ] Notification "Lock" button: presses are ignored while locked; "Unlock" restores. Learn mode still captures while locked.

## D. Music app also running
- [ ] Start Spotify/YouTube Music/etc. Your music is not paused or ducked by this app.
- [ ] Press the headset button: note who reacts (count and/or the music app).
- [ ] If the music app took the buttons, the red "Another app is playing audio" card appears. Tap **Re-take control**, press again → counts.
- [ ] Pause the music app: warning disappears.

## E. Headset disconnect / reconnect
- [ ] Turn the headset off: status shows "not connected", no crash, no stuck vibration.
- [ ] Turn it on again: within ≈ 2 s status shows connected; buttons count again WITHOUT opening the app.
- [ ] Walk out of range and back: same.
- [ ] Switch to a second Bluetooth device and back.
- [ ] Phone call during use: after hanging up, buttons count again (tap Re-take control if not).

## F. Debounce
- [ ] Headsets that send DOWN twice: a single press must count exactly once.
- [ ] Raise debounce to 300 ms: fast double click may be merged; lower to 0: duplicates may appear.

## G. Record for each headset
Model, key names + codes from Learn mode, which gestures worked, whether it needed keep-alive.

## H. v2 additions
- [ ] Single headset click counts with no noticeable delay; 10 quick clicks = 10 counts.
- [ ] Hold Play/Pause 1.2 s: stealth toggles (double buzz); releasing adds no count.
- [ ] Screen: two fingers held 1 s toggles stealth; a single finger held > 0.5 s does not count; normal tap counts.
- [ ] Stealth: screen at lowest brightness, status bar hidden; leaving stealth restores brightness and bars.
- [ ] Volume keys, app open: each fresh press = +1; holding the key = exactly one count.
- [ ] Volume keys, screen OFF (service running): each fresh press = +1; holding = one count. (Device dependent.)
- [ ] Turn "Phone volume buttons count" off: volume keys change volume again. Hardware lock on: same.
- [ ] Reach 108: ring resets, "Today's Mala Count" +1, long triple buzz.
- [ ] History shows today's total and hourly breakdown; survives app restart; new day starts a new entry.
- [ ] No − button, no Reset button on the main screen.

## I. v3 additions
- [ ] Tap counts the instant the finger lands (no wait for release); a finger left down counts once only.
- [ ] Four or five fingers held ~1 s: stealth on (double buzz), no stray count. Again ~1 s: stealth off and the CALCULATOR appears.
- [ ] Calculator really calculates (2+3=5, 10/4=2.5, 1/0=Error).
- [ ] Type 1234 then = : the counter opens. Type 12+34= : just 46. Wrong codes do nothing special.
- [ ] Change the secret code in Settings; the new one works, the old one does not.
- [ ] Start the app fresh (swipe it away, reopen): calculator first.
- [ ] While the calculator is showing, headphone / volume counting still works.
- [ ] Menu ⋮ -> Hide shows the calculator immediately.
- [ ] Reset current mala: ring 0, today/all-time/malas unchanged; entry appears in History > Resets with date+time.
- [ ] Reset ALL malas: ring, today, all-time and malas read 0; History keeps the days and shows the reset with date+time and old numbers.
- [ ] Counting after Reset ALL starts from 0 again.
- [ ] Background themes change the screen colours; background picture can be added and removed.

## J. v5 additions
- [ ] Settings > Touch speed: Slow ignores touches closer than 0.4 s; Turbo counts every touch.
- [ ] A touch ignored by the speed limit does not count and does not buzz.
- [ ] Background colour picker: palette dots and sliders change the screen colours; the last swatch selects Custom.
- [ ] Daily goal 2 malas: info box shows Goal 0/2; finishing the 2nd mala today gives the rising buzz once and a tick.
- [ ] After a mala, "Last mala" appears under the ring; Analytics > Mala speed lists it.
- [ ] Analytics: 7-day and 30-day bars match History; hour chart peaks at the hours you counted; streak is right.
- [ ] Reset ALL does not delete Analytics data (history is kept).

## K. Final version: face unlock
- [ ] Settings > Face unlock > Set up: allow camera, look at the screen; 8 samples fill; "Saved".
- [ ] Test: your own similarity is clearly above the "needs" value; someone else's stays clearly lower.
- [ ] Turn Face unlock ON, close the app completely, reopen: calculator first, then the counter opens by itself within ~2 s.
- [ ] Hand the phone to someone else (face visible): the calculator stays. Typing 1234 = still works as the backup.
- [ ] You and another person both in view: calculator stays (2+ faces).
- [ ] Counter open, another person steps in front of the camera: it switches to the calculator.
- [ ] Dim room, glasses on/off, different angle: if you are missed, lower strictness a little and re-test.
- [ ] Deny camera permission: calculator + code still works, nothing crashes.
- [ ] Delete my face data: face unlock turns off.

## L. Final version: layouts
- [ ] Portrait phone, small phone, landscape, split-screen: nothing overlaps or is cut off on Counter, Settings, History, Analytics, Calculator.
- [ ] System font size set to Large: texts wrap, buttons stay usable.
- [ ] History > Days: tap a day for its hourly chart. History > Resets: both reset cards and the dated log read cleanly.
- [ ] Analytics: 7 and 30 day bars with labels, stat tiles, hour chart, mala speed, goal.

## M. Counters, disguises, icon, rapid taps
- [ ] Menu > Counters > add "Test" with code 5678. On the calculator type 5678 = : the empty Test counter opens. 1234 = opens Main.
- [ ] Counts, History and Analytics are separate per counter. Delete Test: its data is gone; Main is untouched.
- [ ] Stealth with Alarm / Clock / Update / Charging: the animation runs, tapping anywhere counts, 4-5 fingers leaves stealth.
- [ ] LCD mode on an IPS phone: black screen is as dark as the phone allows (switch back to OLED if the screen turns off).
- [ ] Settings > App icon and name > Clock: the home-screen icon and name change (wait a few seconds). Switch back to Calculator.
- [ ] Android Settings > Apps lists the app as "Calculator"; the notification reads "Calculator / Running".
- [ ] Headset Play/Pause: 10 very fast taps give ~10 counts with Rapid-tap mode on.
- [ ] Face security level Maximum: blink to unlock; after ~20 s without your face the counter hides again.
- [ ] Allow the phone's own biometric; long-press the calculator number: the system prompt appears and opens the counter.
- [ ] Calculator: portrait, landscape and a long result (e.g. 123456789 x 987654321) all fit.
