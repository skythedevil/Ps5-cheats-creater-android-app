# PS5 Debug Studio Mobile

Android companion/port based on the public PS5Debug-studio V1.5 project (Apache-2.0). This is a native Android implementation, not a repackaged Windows executable.

## Development status

This repository is an in-progress Android port. Several major desktop workflows now have mobile implementations, but **V1.5 parity is not complete**. A successful GitHub Actions build proves compilation only; it does not prove the protocol works against every ps5debug-NG payload or that an operation is safe on a live game. Do not treat a development APK as a feature-complete release until the [parity checklist](PARITY_CHECKLIST.md) and live-device validation are complete.

## Implemented mobile workflows

- TCP 744 connection, protocol version handshake, UDP 1010 console discovery, and optional firmware/branding/capability/platform queries.
- Process listing, process details, memory maps/modules, bounded memory reads and writes.
- Memory-write read-back verification with original-byte rollback; bounded protocol frames and fail-closed handling for interrupted streams.
- Exact byte/AOB scanning with wildcards, Boyer–Moore–Horspool acceleration for exact patterns, UTF-8/UTF-16LE text conversion, cancellation, and a configurable 1–2,000 result cap. Exact/AOB matches can be bulk-read using their actual live bytes and imported into the address table.
- TurboScan resident/snapshot scans, result paging/editing, and one-action batch saving of a loaded result page to the address table (payload/device validation pending).
- Address table persistence, typed values, authenticated batched refresh, per-entry and bulk freeze using payload multi-write status results.
- Paged hex editor with verified writes, original-byte restore, binary patch-file import/export, and typed signed/unsigned integer, float, double, UTF-8 and UTF-16LE inspection.
- Partial x64 assembler, executable cave scanning and hook builder with guarded writes/restore.
- Debug attach/detach, TCP 755 event listener, software breakpoints, configurable 1/2/4/8-byte hardware write/read-write watchpoints, thread listing, process/thread stepping, pause/resume, GP register editing, and XMM0 editing through the full 832-byte FPU/YMM state image.
- Remote buffer allocation/free, verified buffer writes, and six-argument remote calls through cached stack-safe x64 trampolines; explicit disconnect attempts to free session-owned buffers and trampoline pools.
- TCP 3232 kernel-log stream viewer.
- Cheat JSON library editing/persistence, repeat patch groups, conditional `check` steps, typed `read` reports, safe six-argument `call` steps, execution `log`/`sleep` steps, NOP patches, assembled-code steps in activation and deactivation sequences, verified turn-all-off restoration, and partial GoldHEN JSON, SHN XML and MC4 conversion.

See [FEATURE_STATUS.md](FEATURE_STATUS.md) for exact implemented/partial/pending distinctions and [PARITY_CHECKLIST.md](PARITY_CHECKLIST.md) for the release gate.

AI chat supports structured, validated TurboScan proposals with an explicit user approval step; the action is read-only and does not perform memory writes.

## Important limitations

- Many operations remain unverified on a physical PS5. Payload version, firmware, permissions, network routing and game state can affect support.
- The disassembler is not yet a full x86-64 mnemonic/operand decoder; assembler, pointer scanning, hooks and debugger coverage remain partial.
- Full Cheat Engine parity is not implemented: advanced scan modes/filters, full debugger run control, all cheat step types, project management, caller/function logger, Python scripting and several import/export formats remain pending.
- Remote calls can still crash a game if the target function or arguments are wrong. They are not automatically retried.
- Keep backups and use memory-write / hook / register-edit operations only when you understand the target address and have a recovery path.

## Build

Use the **Build Android APK** GitHub Actions workflow. It builds `app/build/outputs/apk/debug/app-debug.apk` and uploads it as a development artifact. A successful build is not a completed release.

## Connection

Your Android device and PS5 must be on the same reachable network, with a compatible `ps5debug-NG` payload running on the console. Enter the PS5 IP address and command port 744 in the app.

## License

The mobile implementation is released under Apache-2.0. The original project is https://github.com/alfizari/PS5Debug-studio.
