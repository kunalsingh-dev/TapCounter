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
