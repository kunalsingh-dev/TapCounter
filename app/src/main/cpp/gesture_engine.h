#pragma once
#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace tapcounter {

// Values are mirrored in Kotlin (Gesture.nativeId). Do not renumber.
enum class Gesture : int { Single = 1, Double = 2, Triple = 3, LongPress = 4 };

struct GestureConfig {
    int64_t debounceMs = 80;        // ignore duplicate ACTION_DOWN within this window
    int64_t multiTapWindowMs = 350; // max gap between taps of one multi-click
    int64_t longPressMs = 600;      // hold time that counts as long press
};

struct GestureEvent {
    int keyCode;
    Gesture gesture;
    int64_t timeMs;
};

// Pure, timestamp-driven state machine. It never reads a clock and never sleeps:
// the caller passes timestamps and calls tick() at nextDeadlineMs().
// Thread-safe (single mutex).
class GestureEngine {
public:
    explicit GestureEngine(GestureConfig cfg = GestureConfig());

    void configure(const GestureConfig& cfg);

    // Max taps for one key (1..3, default 3). When the tap count reaches it, the gesture is
    // emitted immediately instead of waiting for the multi-tap window: maxTaps=1 means a
    // single click counts with zero delay.
    void setMaxTaps(int keyCode, int maxTaps);

    // Feed one key event. Returns gestures completed by this event
    // (including pending ones that expired before it).
    std::vector<GestureEvent> onKey(int keyCode, bool down, int64_t tMs);

    // Resolve timeouts (multi-tap window expiry, long press while held).
    std::vector<GestureEvent> tick(int64_t nowMs);

    // Earliest time tick() has something to do, or -1 if idle.
    int64_t nextDeadlineMs() const;

    void reset();

private:
    struct KeyState {
        bool down = false;
        bool longFired = false;
        int taps = 0;
        int64_t downTime = 0;
        int64_t lastUp = 0;
        int64_t lastDownAccepted = INT64_MIN / 2;
    };

    void tickLocked(int64_t now, std::vector<GestureEvent>& out);

    mutable std::mutex mu_;
    GestureConfig cfg_;
    std::unordered_map<int, KeyState> keys_;
    std::unordered_map<int, int> maxTaps_;
};

}  // namespace tapcounter
