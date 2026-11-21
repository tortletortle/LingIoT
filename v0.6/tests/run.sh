#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
g++ -std=c++17 -Wall -Wextra -Werror tests/control_test.cpp -o "$tmp/control"; "$tmp/control"
javac -d "$tmp" android/app/src/main/java/com/lingiot/demo/Wire.java tests/WireTest.java; java -cp "$tmp" WireTest
javac -d "$tmp" android/app/src/main/java/com/lingiot/demo/Capabilities.java tests/CapabilitiesTest.java; java -cp "$tmp" CapabilitiesTest
javac -cp "$tmp" -d "$tmp" android/app/src/main/java/com/lingiot/demo/{VirtualBoard,Telemetry}.java tests/VirtualBoardTest.java; java -cp "$tmp" VirtualBoardTest
