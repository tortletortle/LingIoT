# LingIoT

AI-assisted Android IoT control platform using speech-to-text, an LLM tool layer, and ESP32 over BLE or local Wi-Fi.

## Baseline

The V0.5 source tree is under `baseline/v0.5/`; the original compressed snapshot is retained at `baseline/LingIoT-v0.5-source.tar.xz`. The archive has 50 entries: 31 files and 19 directory entries. It contains the Android app, ESP32 firmware, LIP v2 protocol, virtual board, tests, and CI workflow.

- Version: V0.5
- Source files: 31
- Archive entries: 50 (including directories)
- Archive SHA-256: `171168eb4bcc2cc32a85aa7bb5c17f618f877d7c70179d3c65c7f2303671d515`
- Host regression: C++ control tests, Java Wire tests, 44 Capabilities assertions, 51 VirtualBoard + Telemetry assertions all passed when rerun before upload.

## Build and validation

See `baseline/v0.5/README.md`, `baseline/v0.5/DEMO_GUIDE.md`, and `baseline/v0.5/TEST_REPORT.md`.

Android APK build, ESP32 target build, BLE/Wi-Fi device integration, and physical hardware behavior remain unverified until built and tested on the target toolchains and hardware.

## Roadmap

V0.6 develops a declarative automation engine with typed triggers, conditions, actions, validation, deterministic simulation, and deployable rule plans. LLM output is treated as untrusted input and never executes arbitrary code or GPIO access.