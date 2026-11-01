# Feature implementation status

Last updated: 2026-11-02. Updated with per-entry address-table write and read-back verification. Status is based on source code currently committed to `dev/v1.5-parity-batch`. "Implemented (not live-tested)" means a UI and protocol call exist; it does **not** mean verified against a physical PS5.

Legend: **Implemented** = code path exists; **Partial** = limited subset only; **Pending** = not implemented; **Test pending** = code exists but CI/device validation is not complete.

| Feature area | Status | What exists now | Still needed |
|---|---|---|---|
| Android app shell / readable mobile layout | Implemented (not device-tested) | Native Android screen, scroll layout, dark high-contrast panels, operation log | Phone/tablet UI regression and accessibility testing |
| TCP console connection | Partial | Connect/disconnect on port 744; protocol version handshake | Correct connection error recovery, discovery, firmware/branding/platform capability queries |
| Process management | Partial | Process list, PID selection, process info | Foreground app details, process lifecycle actions, process authentication/capability handling |
| Memory maps/modules | Implemented (not live-tested) | Map list, module-name filter, start/end/protection/offset display | Better module grouping, export and region filtering |
| Memory read | Implemented (not live-tested) | Address read, bounded 1–4096 bytes, hex dump | Paging, typed display, bulk reads |
| Memory write | Implemented (not live-tested) | Exact hex-byte writes up to 4096 bytes | Typed writes, undo history, multi-write and freeze |
| Exact byte scan | Partial | Client-side exact pattern scan over a user range capped at 16 MiB | AOB wildcard support, filters, paging and cancellation |
| Turbo value scanner | Partial (not live-tested) | Dedicated second connection, TurboScan capability probe, payload auth handshake, server-resident first scans (exact/bigger/smaller/between), next scans (exact/bigger/smaller/between/increased/decreased/changed/unchanged and by-value variants), and user-paged result retrieval for byte/integer/float/double types, out-of-band cancel command, and per-result hex editing/writing | Fuzzy scans, AOB/string scans, segmented multi-region scans, live result refresh, robust cancel-state recovery and real-console validation |
| Value scanner | Pending | — | First/next scans for all value types and compare modes |
| Address list / freeze table | Partial (not live-tested) | Persistent address table, add/edit/remove, typed values, manual/automatic refresh, per-entry WRITE with read-back verification, freeze/unfreeze scheduler; TurboScan results can be saved to the table | Bulk freeze controls, copy as cheat, robust reconnect/foreground lifecycle handling and real-console validation |
| Hex editor | Partial | Hex dump output in memory read panel | Interactive paged editor, typed cursor values, selection/copy, edit/revert, dump |
| Process disassembly | Partial | Calls payload disassembly command and displays instruction address/length/kind/bytes | Mnemonic/operand decoding, navigation, AOB, make signature, xrefs UI polish |
| Cross references | Partial | RIP-relative xref search in executable regions (first 1 MiB per region) | Full-range configurable scan, result navigation, xref types |
| Debug attach/detach | Partial | Attach/detach command controls | Port 755 async event listener and robust session state |
| Software breakpoints | Partial | Set/clear slots 0–29 | Breakpoint list, hit events, navigation, original-byte management UI |
| Hardware watchpoints | Partial | Set/clear slot controls with 8-byte write mode | Read/read-write modes, sizes, DR register view and hit events |
| Threads | Partial | Thread/LWP listing | Thread details, suspend/resume, selection and status |
| Registers | Partial | Read 176-byte general register set and display named qwords | Edit/write registers, FPU/XMM/YMM, FS/GS, debug registers |
| Stepping / run control | Partial | Process single-step command | Continue/pause, step over/out, run-to-cursor, event-driven UI |
| Call stack | Pending | — | Stack read and frameless unwind UI |
| Remote calls / buffers | Pending | — | Safe call trampoline, six arguments, alloc/free, buffers and call UI |
| Function logger | Pending | — | Hook generation, ring buffer, polling, caller grouping and export |
| Code caves / hooks / assembler | Pending | — | Cave finder/map, assembler, hook builder and safe restore |
| Cheat JSON library | Partial | Create/import/export `ps5cheat/1`; fixed hex patch steps; title-ID check; patch on/off restore | Full cheat editor, all step types, parameters, off steps, repeat, validation, groups and reorder |
| Cheat format conversion | Pending | — | GoldHEN JSON, SHN, MC4, ShnExt, ps5pack import/export |
| Cheat recorder / caller | Pending | — | Record memory changes/calls into cheat steps |
| Pointer scanner | Pending | — | Turbo pointer-scan capability, map build, chain search, resolve/rescan and persistence |
| Python scripting | Pending | — | Sandboxed/mobile-compatible script API, saved scripts and cancellation |
| Kernel log | Pending | — | Port 3232 streaming and log viewer |
| Console discovery | Implemented (not live-tested) | UDP broadcast probe to 255.255.255.255:1010; validates 4-byte echo and fills PS5 IP field | Test on real LANs, handle subnet-specific broadcast restrictions and multiple consoles |
| Project management | Pending | — | Multiple game projects, settings, items and file persistence |
| Automated tests | Pending | — | Protocol framing/status/auth/scan/debug/cheat tests and mock server |
| PS5 live tests | Pending | — | Verify each implemented operation with a supported ps5debug-NG payload |
| Release APK | Withheld by request | Internal CI builds only | Do not distribute until feature scope is implemented and tests pass |

## Current milestone delivered in source

- Memory maps/module inspection screen.
- Payload disassembly metadata retrieval and byte display.
- RIP-relative cross-reference search.
- Debugger attach/detach, software breakpoint and hardware write-watchpoint commands.
- Thread list, general-register read and process single-step command.

## Important limitations

- The debugger controls are not complete without the asynchronous TCP 755 event channel; do not treat stepping/breakpoints as fully usable until that is added and tested.
- The disassembly panel currently shows payload instruction boundaries/kinds and raw bytes; it is not yet a full mnemonic disassembler.
- The exact-byte scanner is a small-range fallback. An initial TurboScan exact-value path has now been added, but it is not yet a complete replacement for the desktop scanner and has not been live-tested.
- UDP discovery is now implemented in source but has not been verified against a physical console or different Android Wi-Fi networks.\n- No physical console validation has been claimed.
- No APK is to be shared as a user release until the release gate in [PARITY_CHECKLIST.md](PARITY_CHECKLIST.md) is met.
