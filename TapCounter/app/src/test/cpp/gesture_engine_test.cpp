// Host-side test (no Android needed):  ../run_native_tests.sh
#include <cstdio>
#include <cstdlib>
#include <vector>

#include "../../main/cpp/gesture_engine.h"

using namespace tapcounter;

static int g_fail = 0;
#define CHECK(c) do { if (!(c)) { std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #c); g_fail++; } } while (0)

constexpr int PLAY = 85, NEXT = 87;
using V = std::vector<GestureEvent>;

static void append(V& dst, const V& src) { dst.insert(dst.end(), src.begin(), src.end()); }

static void singleClick() {
    GestureEngine e;
    CHECK(e.onKey(PLAY, true, 1000).empty());
    CHECK(e.onKey(PLAY, false, 1050).empty());
    CHECK(e.tick(1100).empty());
    CHECK(e.nextDeadlineMs() == 1400);
    V r = e.tick(1400);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Single && r[0].keyCode == PLAY);
    CHECK(e.nextDeadlineMs() == -1);
}

static void doubleClick() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 50);
    e.onKey(PLAY, true, 200); e.onKey(PLAY, false, 250);
    CHECK(e.tick(599).empty());
    V r = e.tick(600);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Double);
}

static void tripleClickIsImmediate() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 40);
    e.onKey(PLAY, true, 150); e.onKey(PLAY, false, 190);
    e.onKey(PLAY, true, 300);
    V r = e.onKey(PLAY, false, 340);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Triple);
    CHECK(e.tick(5000).empty());  // nothing left pending
}

static void longPressByTick() {
    GestureEngine e;
    e.onKey(PLAY, true, 0);
    CHECK(e.nextDeadlineMs() == 600);
    CHECK(e.tick(599).empty());
    V r = e.tick(600);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::LongPress);
    CHECK(e.onKey(PLAY, false, 900).empty());  // release is not a tap
    CHECK(e.tick(3000).empty());
}

static void longPressDetectedOnRelease() {
    GestureEngine e;
    e.onKey(PLAY, true, 0);
    V r = e.onKey(PLAY, false, 800);  // no tick in between
    CHECK(r.size() == 1 && r[0].gesture == Gesture::LongPress);
    CHECK(e.tick(3000).empty());
}

static void autoRepeatDownDoesNotRetrigger() {
    GestureEngine e;
    V all;
    e.onKey(PLAY, true, 0);
    for (int t = 100; t <= 900; t += 100) append(all, e.onKey(PLAY, true, t));
    append(all, e.tick(1000));
    int longs = 0;
    for (auto& g : all) if (g.gesture == Gesture::LongPress) longs++;
    CHECK(longs == 1);
}

static void debounceDuplicateEvents() {
    GestureEngine e;  // 80 ms
    e.onKey(PLAY, true, 0);
    e.onKey(PLAY, false, 30);
    e.onKey(PLAY, true, 50);   // duplicate DOWN -> ignored
    e.onKey(PLAY, false, 70);  // its UP -> ignored (no accepted DOWN)
    V r = e.tick(1000);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Single);
}

static void debounceBoundary() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 20);
    e.onKey(PLAY, true, 80); e.onKey(PLAY, false, 100);  // exactly 80 ms -> accepted
    V r = e.tick(1000);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Double);
}

static void debounceConfigurable() {
    GestureConfig c; c.debounceMs = 0;
    GestureEngine e(c);
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 10);
    e.onKey(PLAY, true, 20); e.onKey(PLAY, false, 30);
    V r = e.tick(1000);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Double);
}

static void keysAreIndependent() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 50);
    e.onKey(NEXT, true, 100); e.onKey(NEXT, false, 150);
    e.onKey(NEXT, true, 250); e.onKey(NEXT, false, 300);
    V r = e.tick(1000);
    CHECK(r.size() == 2);
    int singles = 0, doubles = 0;
    for (auto& g : r) {
        if (g.keyCode == PLAY && g.gesture == Gesture::Single) singles++;
        if (g.keyCode == NEXT && g.gesture == Gesture::Double) doubles++;
    }
    CHECK(singles == 1 && doubles == 1);
}

static void lateNextDownFlushesPending() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 50);
    V r = e.onKey(PLAY, true, 500);  // window (350) already expired
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Single);
    e.onKey(PLAY, false, 550);
    r = e.tick(900);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Single);
}

static void tapThenHold() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 50);
    e.onKey(PLAY, true, 200);
    V r = e.tick(800);
    CHECK(r.size() == 2);
    CHECK(r.size() == 2 && r[0].gesture == Gesture::Single && r[1].gesture == Gesture::LongPress);
}

static void configurableLongPress() {
    GestureConfig c; c.longPressMs = 300;
    GestureEngine e(c);
    e.onKey(PLAY, true, 0);
    V r = e.tick(300);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::LongPress);
    e.configure(GestureConfig{80, 350, 1000});
    e.onKey(PLAY, false, 400);
    e.onKey(PLAY, true, 600);
    CHECK(e.tick(1599).empty());
    CHECK(e.tick(1600).size() == 1);
}

static void resetClearsState() {
    GestureEngine e;
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 50);
    e.reset();
    CHECK(e.nextDeadlineMs() == -1);
    CHECK(e.tick(5000).empty());
}

static void maxTapsOneIsInstant() {
    GestureEngine e;
    e.setMaxTaps(PLAY, 1);
    e.onKey(PLAY, true, 1000);
    V r = e.onKey(PLAY, false, 1050);  // no waiting at all
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Single);
    CHECK(e.nextDeadlineMs() == -1);
    // rapid counting: every click counts at once
    int n = 0;
    for (int i = 0; i < 10; i++) {
        e.onKey(PLAY, true, 2000 + i * 120);
        n += (int)e.onKey(PLAY, false, 2000 + i * 120 + 40).size();
    }
    CHECK(n == 10);
}

static void maxTapsTwoGivesInstantDouble() {
    GestureEngine e;
    e.setMaxTaps(PLAY, 2);
    e.onKey(PLAY, true, 0); e.onKey(PLAY, false, 40);
    CHECK(e.nextDeadlineMs() == 390);  // single still waits for a possible 2nd tap
    e.onKey(PLAY, true, 150);
    V r = e.onKey(PLAY, false, 190);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::Double);
}

static void maxTapsIsPerKeyAndClamped() {
    GestureEngine e;
    e.setMaxTaps(PLAY, 1);
    e.setMaxTaps(NEXT, 99);  // clamped to 3
    e.onKey(NEXT, true, 0); e.onKey(NEXT, false, 40);
    CHECK(e.nextDeadlineMs() == 390);
    e.setMaxTaps(NEXT, -5);  // clamped to 1
    e.onKey(NEXT, true, 1000);
    V r = e.onKey(NEXT, false, 1030);
    CHECK(!r.empty());
}

static void longPressStillWorksWithMaxTapsOne() {
    GestureEngine e;
    e.setMaxTaps(PLAY, 1);
    e.onKey(PLAY, true, 0);
    V r = e.tick(600);
    CHECK(r.size() == 1 && r[0].gesture == Gesture::LongPress);
    CHECK(e.onKey(PLAY, false, 900).empty());
}

int main() {
    maxTapsOneIsInstant(); maxTapsTwoGivesInstantDouble(); maxTapsIsPerKeyAndClamped();
    longPressStillWorksWithMaxTapsOne();
    singleClick(); doubleClick(); tripleClickIsImmediate(); longPressByTick();
    longPressDetectedOnRelease(); autoRepeatDownDoesNotRetrigger(); debounceDuplicateEvents();
    debounceBoundary(); debounceConfigurable(); keysAreIndependent(); lateNextDownFlushesPending();
    tapThenHold(); configurableLongPress(); resetClearsState();
    if (g_fail == 0) std::printf("ALL NATIVE TESTS PASSED\n");
    return g_fail == 0 ? 0 : 1;
}
