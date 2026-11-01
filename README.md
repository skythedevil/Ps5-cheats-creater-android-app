# PS5 Debug Studio Mobile

Android companion/port based on the public PS5Debug-studio V1.5 project (Apache-2.0). This is a mobile-first implementation, not a repackaged Windows executable.

## Feature tracker

See [FEATURE_STATUS.md](FEATURE_STATUS.md) for implemented, partial, pending and untested features. See [PARITY_CHECKLIST.md](PARITY_CHECKLIST.md) for the release acceptance gate.

## Development status

This repository is an in-progress Android port. The first debug build was only a connectivity/memory/patch prototype and is **not** a feature-complete release. No APK should be presented as finished until the [V1.5 parity checklist](PARITY_CHECKLIST.md) is complete and the applicable tests pass.

## V1 mobile scope
- Connect to a PS5 running ps5debug-NG over the local network (TCP 744)
- List processes and inspect process metadata
- Read and write process memory by address
- Exact-byte scan within a user-selected memory range (bounded to 16 MiB per scan)
- Import, create, edit, run and export PS5Debug Studio game-cheat JSON (`ps5cheat/1`) for fixed `patch` steps

## Not yet ported
The desktop debugger, disassembler, code-cave assembler, pointer scanner, function logger and arbitrary Python scripting are not represented as working Android features in this V1.

## Build
Use the **Build Android APK** GitHub Actions workflow. It builds `app/build/outputs/apk/debug/app-debug.apk` and uploads it as a workflow artifact.

## Connection
Your Android device and PS5 must be on the same reachable network, with the `ps5debug-NG` payload running on the console. Enter the PS5 IP address and port 744 in the app.

## License
The mobile implementation is released under Apache-2.0. The original project is https://github.com/alfizari/PS5Debug-studio.
