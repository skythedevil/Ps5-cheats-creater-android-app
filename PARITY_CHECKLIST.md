# Android parity plan — PS5 Debug Studio V1.5

This file is the acceptance checklist for the Android port. **Do not publish or share an APK as a completed release until every applicable V1.5 feature below is implemented and tested.** A successful compile alone is not feature completion.

Reference: https://github.com/alfizari/PS5Debug-studio/tree/V1.5

## Compatibility and transport
- [ ] Complete ps5debug-NG protocol 1.3 transport, including correct framing, status decoding, bounded packet sizes, timeouts and fail-closed reconnect on stream desynchronisation.
- [ ] Protocol version, firmware, branding/capability and platform queries.
- [ ] Process authentication/challenge response and capability negotiation.
- [ ] UDP console discovery.
- [ ] Command connection (744), async debugger event listener (755), kernel log stream (3232).
- [ ] Process list, process details, process maps/modules and process selection.
- [ ] Single and bulk memory read/write; safe chunking and per-entry status reporting.

## Scanner and address list
- [ ] First/next scans: exact, bigger, smaller, between, unknown initial, increased, decreased, changed, unchanged, increased/decreased by.
- [ ] Value types: byte, signed/unsigned 2/4/8 byte, float, double, UTF-8/UTF-16 string, AOB with wildcards.
- [ ] Turbo scan resident/snapshot modes, segmented scans, count/get paging, cancellation on a separate connection.
- [ ] Scan range, writable/code filters, alignment, zero handling, region classification, aliasing/read-engine options.
- [ ] Results paging, live value refresh, previous/first values, selection and multi-selection.
- [ ] Address list: add/edit/delete, type change, freeze/unfreeze, bulk freeze writes, save/load tables, copy as cheat.

## Memory inspection and code tools
- [ ] Hex editor: paged view, typed values, selection/copy formats, edit, write/revert and region dump.
- [ ] Disassembler: x86-64 decoding, navigation, AOB search, make AOB from selection, references/xrefs.
- [ ] Assemble-in-place and patch/NOP operations.
- [ ] Code-cave finder/map, cave assembly, hook generation and restore.
- [ ] Module-relative, pointer, buffer and input address expression resolver.

## Debugging
- [ ] Debug attach/detach and asynchronous interrupt/event channel.
- [ ] Pause/resume, step into/over/out, run-to-cursor.
- [ ] Software breakpoints and hardware execute/write/read-write watchpoints, sizes and slot tracking.
- [ ] Register view/edit, XMM/FPU state, stack, thread list and thread selection.
- [ ] Call-stack unwind, including frameless functions.
- [ ] Find what writes/accesses an address and link it to watchpoints.

## Calling, logging and scripting
- [ ] Remote function calls with up to six SysV x86-64 arguments.
- [ ] Remote allocation/free, buffers, strings and byte arrays.
- [ ] Stack-safe call trampoline, rate limiting and no blind retries.
- [ ] Remote assembly buffer and variable support.
- [ ] Function logger hooks, ring buffer, caller grouping and CSV export.
- [ ] Python scripting/API equivalent, saved scripts, cheat Python steps and safe cancellation.
- [ ] Kernel log streaming, error highlighting and activity log.

## Cheats, projects and exports
- [ ] Multi-game library, game metadata, title ID/version/content ID/process fields.
- [ ] Cheat editor: create, rename, duplicate, enable/disable, delete, reorder and edit steps.
- [ ] Full step engine: write, patch, asmpatch, alloc, code, hook, aob, call, read, set, check, python and every additional step in V1.5 source.
- [ ] Toggle/run modes, inputs, item ID lists, validation/check-and-fix, undo restoration and turn-all-off.
- [ ] Record cheat from Caller; add/update call steps with required buffers, variables and inputs.
- [ ] Projects: create/open/switch, game files, item lists, exports and cave map.
- [ ] Import/export tool JSON, .ps5c packs/share codes, .shn, .mc4, GoldHEN JSON and ShnExt where dependencies/private modules permit.
- [ ] Export validation, original/off-byte acquisition, code-cave placement and used-space management.

## Android product requirements
- [ ] Mobile-first navigation and readable UI on phone/tablet, landscape where useful.
- [ ] Persistent settings, project/file storage via Android document picker, operation queue and non-blocking UI.
- [ ] Clear capability indicators when the connected payload does not support an optional command.
- [ ] No fake controls: every visible action must work or be clearly marked unsupported.
- [ ] Offline protocol/mock tests for packet layouts, auth, scan semantics, read/write, debugger events and cheat step engine.
- [ ] Build, install, startup, UI and regression tests on Android; live PS5 tests for supported operations.

## Release gate
A release may be labelled **feature-complete** only when all applicable boxes are checked, the test suite passes, the APK installs and launches, and the README lists any unavoidable payload/dependency limitations. Until then, keep builds as internal development artifacts and do not present them as finished.
