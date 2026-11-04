# In-app AI Cheat Creator

## Startup and one-time AI setup

- On first launch, the app asks whether the user wants AI integration.
- Choosing No records that choice and future launches open directly to the home screen.
- Choosing Yes opens provider, model, endpoint and API-key settings. The user can also continue without AI.
- The API key is encrypted with AES-GCM using Android Keystore. Provider settings persist locally; blank key input retains the saved key.
- On later launches, the app does not ask the integration question again. The home screen offers Simple Search, AI-assisted Search, AI Settings and Advanced PS5 Tools. Simple Search opens a focused screen with only console connection, process selection, TurboScan controls and the game cheat file editor; Advanced PS5 Tools opens the full tool suite.
- OpenRouter requests include HTTP-Referer and X-Title headers.

## AI-assisted search flow

1. The user enters game name, optional title ID, and the cheat they want (for example, infinite health).
2. The app creates a new game project for that game and opens the PS5 connection/process screen.
3. The user connects and selects the actual game process, then taps **START AI SEARCH**.
4. The user taps START AI SEARCH after selecting the game process. AI chooses the numeric type and proposes an unknown-initial-value baseline scan; the app applies that read-only scan. The default scan scope prioritizes writable regions and skips slow regions. AI chooses later scan types/comparisons from the user's observations and actual results.
5. After each scan, the app loads a page of real results and sends up to 40 candidates (addresses and current/previous bytes) to AI.
6. AI asks the user to perform one controlled in-game action and report the exact visible value or attach a screenshot. The user can type the value or use **ATTACH GAME SCREENSHOT**.
7. AI proposes a next TurboScan action. In AI-assisted mode, the app applies valid read-only scan proposals automatically and loads the next result page. Manual mode still shows a confirmation dialog.
8. AI may request candidate-bound read-only memory inspection. Writes and cheat activation remain explicitly gated by user approval and live-byte verification.
9. AI-generated cheat drafts are validated and previewed; user approval adds the disabled draft to the same game's local cheat library.
10. After each accepted draft, the app asks whether to add another cheat to the same game file or finish and export. GoldHEN JSON is the primary export option; MC4 is offered as an alternative for supported fixed section-0 patches.

## Screenshot analysis

The screenshot picker accepts an image selected by the user and sends a compressed image to a multimodal chat-completions model. The AI can transcribe visible values and state uncertainty. There is no HDMI capture-card requirement or continuous live capture feature.

## Cheat-file compatibility and limitations

- Native local project format is `ps5cheat/1`; the app can export GoldHEN JSON, SHN XML and MC4 for supported fixed-address hex patches.
- GoldHEN JSON export supports only fixed hex patches with absolute addresses or supported main-module offsets. Unsupported steps/addresses must be rejected rather than silently converted.
- MC4 export passes through the existing SHN fixed section-0 conversion and encryption path. It is not a universal format for every PS5 cheat loader.
- A candidate value changing with gameplay is not sufficient by itself to prove it controls the desired mechanic. Strong evidence requires repeated controlled changes and candidate refinement.
- Runtime addresses can move between sessions. A stable module-relative offset or a verified game/version-specific patch is needed for a reusable cheat file.
- No universal automatic cheat can be guaranteed for every game. PS5 payload compatibility, title version, memory layout, ASLR and game-specific code matter.
- Memory writes, cheat application and activation remain user-controlled. Actual in-game effect requires console/gameplay validation.
