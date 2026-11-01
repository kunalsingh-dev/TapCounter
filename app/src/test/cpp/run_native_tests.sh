#!/usr/bin/env bash
# Works on Linux, GitHub Actions and Termux (pkg install clang).
set -euo pipefail
cd "$(dirname "$0")"
CXX="${CXX:-c++}"
$CXX -std=c++17 -Wall -Wextra -Werror -fsanitize=address,undefined -g \
  gesture_engine_test.cpp ../../main/cpp/gesture_engine.cpp -o /tmp/gesture_test -pthread
/tmp/gesture_test
