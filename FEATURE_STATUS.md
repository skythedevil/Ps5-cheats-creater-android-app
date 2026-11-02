# Feature implementation status

Last updated: 2026-11-02. Batch 1 pointer scanner and hex editor work, plus Batch 2 x64 assembler, hook builder and TCP 755 debug-event listener are implemented in source and CI-built. Cheat-object editing, structured step add/edit/remove, deletion, reordering, duplication, local library persistence and rollback-safe patch application have now been added; latest code commit CI build succeeded. Physical-console validation remains pending.

Legend: **Implemented** = code path exists; **Partial** = limited subset only; **Pending** = not implemented; **Test pending** = code exists but CI/device validation is not complete.

| Feature area | Status | What exists now | Still needed |
|---|---|---|---|
| Pointer scanner | Partial (CI built) | Bounded client-side pointer map over readable regions with module-first prioritization; 8-byte aligned candidates; configurable scan budget, offset and depth; path-specific cycle prevention; cancellation and caps | Turbo-assisted maps, save/load, live resolve/rescan, console performance validation |
| Assembler | Partial (offline) | Built-in x86-64 subset encoder: NOP, RET, INT3, push/pop 64-bit registers, MOV r64/imm64, relative and register-indirect JMP/CALL, DB bytes; verified memory write and original-byte restore | Full x86-64 assembler, labels, expressions, memory operands, validation against Keystone/console |
| Hook builder | Partial | 14-byte absolute JMP patch, NOP padding and trampoline return bytes; auto-captures original bytes; validates executable cave/padding and detour region; verified trampoline/patch write, rollback and original-byte/cave restore; reads/caches original bytes from the console and whole-instruction length | Executable allocation, instruction-boundary validation, RIP-relative relocation, register/flags preservation templates and live testing |
| Live debugger events | Partial (CI built) | Phone TCP 755 listener accepts 1184-byte payload event packets and displays LWP, status, RIP/RBP/RSP; starts before debugger attach; listener lifecycle cleaned up on activity destroy | Validate packet timing/endianness on console, reconnect/event loop hardening, breakpoint/watchpoint/step event classification |
| Android app shell / readable mobile layout | Implemented (not device-tested) | Native Android screen, scroll layout, dark high-contrast panels, operation log | Phone/tablet UI regression and accessibility testing |
| TCP console connection | Partial | Connect/disconnect on port 744; protocol version handshake | Correct connection error recovery, discovery, firmware/branding/platform capability queries |
| Process management | Partial | Process list, PID selection, process info | Foreground app details, process lifecycle actions, process authentication/capability handling |
| Memory maps/modules | Implemented (not live-tested) | Map list, module-name filter, start/end/protection/offset display | Better module grouping, export and region filtering |
| Memory read | Implemented (not live-tested) | Address read, bounded 1–4096 bytes, hex dump | Paging, typed display, bulk reads |
| Memory write | Implemented (not live-tested) | Exact hex-byte writes up to 4096 bytes | Typed writes, undo history, multi-write and freeze |
| Exact byte scan | Partial | Client-side exact pattern scan over a user range capped at 16 MiB | AOB wildcard support, filters, paging and cancellation |
| Turbo value scanner | Partial (not live-tested) | Dedicated second connection, TurboScan capability probe, payload auth handshake, server-resident first scans (exact/bigger/smaller/between), next scans (exact/bigger/smaller/between/increased/decreased/changed/unchanged and by-value variants), and user-paged result retrieval for byte/integer/float/double types, out-of-band cancel command, and per-result hex editing/writing with read-back verification | Fuzzy scans, AOB/string scans, segmented multi-region scans, live result refresh, robust cancel-state recovery and real-console validation |
| Value scanner | Pending | — | First/next scans for all value types and compare modes |
| Address list / freeze table | Partial (not live-tested) | Persistent address table, add/edit/remove, typed values, manual/automatic refresh, per-entry WRITE with read-back verification; scan-result edits also read back and verify, per-entry and bulk freeze/unfreeze controls, guarded one-second freeze scheduler; TurboScan results can be saved to the table | Copy as cheat, robust reconnect/foreground lifecycle handling, freeze write/read-back reporting and real-console validation |
| Hex editor | Partial (not live-tested) | Bounded 1–4096 byte paging, editable hex-byte buffer, previous/next page navigation, write with read-back verification, restore-original snapshot | Typed cursor values, selection/copy formats, region dump/export and real-console validation |
| Process disassembly | Partial | Calls payload disassembly command and displays instruction address/length/kind/bytes | Mnemonic/operand decoding, navigation, AOB, make signature, xrefs UI polish |
| Cross references | Partial | RIP-relative xref search in executable regions (first 1 MiB per region) | Full-range configurable scan, result navigation, xref types |
| Debug attach/detach | Partial | Attach/detach controls plus phone TCP 755 async event listener for 1184-byte debugger event packets | Physical-console validation, robust session state and event classification |
| Software breakpoints | Partial | Set/clear slots 0–29 | Breakpoint list, hit events, navigation, original-byte management UI |
| Hardware watchpoints | Partial | Set/clear slot controls with 8-byte write mode | Read/read-write modes, sizes, DR register view and hit events |
| Threads | Partial | Thread/LWP listing | Thread details, suspend/resume, selection and status |
| Registers | Partial | Read 176-byte general register set and display named qwords | Edit/write registers, FPU/XMM/YMM, FS/GS, debug registers |
| Stepping / run control | Partial | Process single-step command | Continue/pause, step over/out, run-to-cursor, event-driven UI |
| Call stack | Partial (not live-tested) | Frame-pointer chain reader with RBP/RSP auto-fill from thread registers, depth limit and frame output | Frameless unwind, symbolication, click-to-navigate and real-console validation |
| Remote calls / buffers | Pending | — | Safe call trampoline, six arguments, alloc/free, buffers and call UI |
| Function logger | Pending | — | Hook generation, ring buffer, polling, caller grouping and export |
| Code caves / hooks / assembler | Partial | Bounded executable-padding scan; x64 subset assembler; assembler writes only to verified executable padding; absolute jump hook patch/trampoline generation, guarded install and restore | Full assembler, RIP-relative relocation, instruction-boundary decoding, executable allocation and physical-console validation |
| Cheat JSON library | Partial (CI build pending) | Create/import/export `ps5cheat/1`; fixed hex patch steps; title-ID check; patch on/off restore; full JSON cheat-object editor for multi-step entries; native step add/edit/remove dialog; delete, reorder and duplicate controls; local game/cheat library auto-save and restore; rollback attempt when a multi-step patch application fails | Support for all step types/parameters/off steps/repeat, groups, deeper validation and real-device regression |
| Cheat format conversion | Partial | GoldHEN JSON import/export, SHN XML import/export and MC4 AES-256-CBC import/export for fixed section-0 / absolute hex patch lines; unsupported sections are skipped and non-patch GoldHEN export is rejected | ShnExt, ps5pack, section-aware mappings and richer step conversion |
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
