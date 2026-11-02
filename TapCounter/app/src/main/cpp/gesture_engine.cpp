#include "gesture_engine.h"

#include <algorithm>

namespace tapcounter {
namespace {

constexpr int kMaxTaps = 3;

Gesture gestureForTaps(int taps) {
    switch (taps) {
        case 1: return Gesture::Single;
        case 2: return Gesture::Double;
        default: return Gesture::Triple;
    }
}

GestureConfig sanitize(GestureConfig c) {
    c.debounceMs = std::max<int64_t>(0, c.debounceMs);
    c.multiTapWindowMs = std::max<int64_t>(50, c.multiTapWindowMs);
    c.longPressMs = std::max<int64_t>(200, c.longPressMs);
    return c;
}

}  // namespace

GestureEngine::GestureEngine(GestureConfig cfg) : cfg_(sanitize(cfg)) {}

void GestureEngine::configure(const GestureConfig& cfg) {
    std::lock_guard<std::mutex> lock(mu_);
    cfg_ = sanitize(cfg);
}

void GestureEngine::setMaxTaps(int keyCode, int maxTaps) {
    std::lock_guard<std::mutex> lock(mu_);
    maxTaps_[keyCode] = std::min(kMaxTaps, std::max(1, maxTaps));
}

void GestureEngine::reset() {
    std::lock_guard<std::mutex> lock(mu_);
    keys_.clear();
}

void GestureEngine::tickLocked(int64_t now, std::vector<GestureEvent>& out) {
    const size_t start = out.size();
    for (auto& entry : keys_) {
        const int key = entry.first;
        KeyState& s = entry.second;
        if (s.down && !s.longFired && now - s.downTime >= cfg_.longPressMs) {
            // "tap, then hold": flush the taps first, then the long press.
            if (s.taps > 0) {
                out.push_back({key, gestureForTaps(s.taps), s.lastUp});
                s.taps = 0;
            }
            s.longFired = true;
            out.push_back({key, Gesture::LongPress, s.downTime + cfg_.longPressMs});
        } else if (!s.down && s.taps > 0 && now - s.lastUp >= cfg_.multiTapWindowMs) {
            out.push_back({key, gestureForTaps(s.taps), s.lastUp + cfg_.multiTapWindowMs});
            s.taps = 0;
        }
    }
    std::stable_sort(out.begin() + static_cast<std::ptrdiff_t>(start), out.end(),
                     [](const GestureEvent& a, const GestureEvent& b) { return a.timeMs < b.timeMs; });
}

std::vector<GestureEvent> GestureEngine::tick(int64_t nowMs) {
    std::lock_guard<std::mutex> lock(mu_);
    std::vector<GestureEvent> out;
    tickLocked(nowMs, out);
    return out;
}

std::vector<GestureEvent> GestureEngine::onKey(int keyCode, bool down, int64_t t) {
    std::lock_guard<std::mutex> lock(mu_);
    std::vector<GestureEvent> out;

    // Resolve anything that expired before this event (late tick, long press
    // whose UP arrives after the threshold, etc.).
    tickLocked(t, out);

    KeyState& s = keys_[keyCode];

    if (down) {
        if (s.down) return out;                                   // auto-repeat while held
        if (t - s.lastDownAccepted < cfg_.debounceMs) return out;  // duplicate DOWN
        s.lastDownAccepted = t;
        s.down = true;
        s.longFired = false;
        s.downTime = t;
    } else {
        if (!s.down) return out;  // UP without accepted DOWN (e.g. debounced duplicate)
        s.down = false;
        if (s.longFired) {
            s.longFired = false;
            s.taps = 0;
            return out;  // release after long press is not a tap
        }
        s.taps++;
        s.lastUp = t;
        const auto limitIt = maxTaps_.find(keyCode);
        const int limit = limitIt == maxTaps_.end() ? kMaxTaps : limitIt->second;
        if (s.taps >= limit) {
            out.push_back({keyCode, gestureForTaps(s.taps), t});  // nothing more can follow: emit now
            s.taps = 0;
        }
    }
    return out;
}

int64_t GestureEngine::nextDeadlineMs() const {
    std::lock_guard<std::mutex> lock(mu_);
    int64_t best = -1;
    for (const auto& entry : keys_) {
        const KeyState& s = entry.second;
        int64_t d = -1;
        if (s.down && !s.longFired) d = s.downTime + cfg_.longPressMs;
        else if (!s.down && s.taps > 0) d = s.lastUp + cfg_.multiTapWindowMs;
        if (d >= 0 && (best < 0 || d < best)) best = d;
    }
    return best;
}

}  // namespace tapcounter
