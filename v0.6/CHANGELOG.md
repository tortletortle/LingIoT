# V0.5.0 — dynamic dashboard and simulator

- Native capability-driven controls, RGB swatch and bounded ADC trend chart.
- Foreground-only optional polling, failure stops polling, connection changes clear state.
- VirtualBoard, VirtualLink, Telemetry and host/Gradle tests.
- Preserve v0.4 firmware and LIP v2. No multi-device orchestration yet.

# Changelog

## 0.4.0 - 2026-11-20
- Add ESP32-local analog-threshold-to-LED automation, executed on-device at a 100 ms sampling interval.
- Add configurable threshold direction and hysteresis to avoid rapid switching near the boundary.
- Advertise and validate the new `set_analog_led_rule` capability through the existing LIP v2 protocol and dynamic LLM tool generation.
- Add Android controls for threshold, hysteresis, direction, enable and disable.
- Manual LED/blink commands and the global stop command cancel the active analog rule; rules are RAM-only and clear after reboot.
- Make local Wi-Fi routing fail closed when Android cannot identify a route to the ESP32, rather than falling back to the default network.
- Extend host tests for automation transitions, hysteresis, state validation and parameter limits.


## 0.3.0 - 2026-11-20
- Upgrade LIP request payload to protocol version 2 with structured `args`.
- Add RGB LED, standard positional servo and ADC input capabilities.
- Add optional TB6612FNG DC motor output with bounded PWM and 100–2000 ms auto-stop; disabled by default.
- Generate model tools from device-advertised operations while enforcing a local app allowlist and parameter bounds.
- Validate typed parameters, request IDs, device acknowledgements and persistent-state readback.
- Add quick controls for RGB, servo, analog input and motor demo.
- Add hardware-independent C++ control tests and expanded Android protocol/policy tests.
- Document pin mapping, wiring, power isolation and demo safety boundaries.

## 0.2.0 - 2026-11-20
- Add device capability discovery and dynamic LLM tool generation for the original LED profile.
- Add local operation/range policy, request ID validation and independent state readback.
- Add 18 host-side policy assertions and CI workflow.

## 0.1.0 - 2026-11-19
- Initial Android Java app, ESP32 BLE/Wi-Fi firmware, voice transcription, API integration and LED demo.
