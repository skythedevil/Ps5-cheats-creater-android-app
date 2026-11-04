# In-app AI Cheat Creator

## Provider setup and saved key

- Configure OpenAI, OpenRouter, or a custom OpenAI-compatible endpoint.
- API key is encrypted with AES-GCM using Android Keystore; leave the key field blank to retain the saved key.
- Provider endpoint/model and chat history persist locally. Disconnect AI removes the stored configuration and Keystore key.
- OpenRouter requests include HTTP-Referer and X-Title headers.
- Image analysis requires a model that accepts image input. The OpenRouter preset uses `openai/gpt-4o-mini`; model availability and billing depend on the user's provider account.

## PS5 screen image analysis

The AI section has an **ANALYZE PS5 SCREENSHOT WITH AI** action. It opens Android's image picker, downsamples the selected image to a maximum 1280-pixel edge, JPEG-compresses it, and sends it as a multimodal chat-completions message. The AI is instructed to read only visible gameplay values (such as health, ammo, money, coordinates and timers), report uncertainty, and use those observations to suggest a controlled scan. The image itself is not persisted in chat history; the textual conversation is saved locally.

This is screenshot-based vision, not continuous live capture. To capture a TV/monitor feed, the phone needs a compatible HDMI-to-USB UVC capture device and app-side UVC camera integration. Live UVC streaming, automatic frame sampling and video analysis are still pending. Android screen recording/MediaProjection cannot see a separate TV's HDMI output.

## Controlled AI cheat workflow

- AI can propose a first or next TurboScan; the app previews the type/comparison/value and requires the user to approve before running a read-only scan.
- The user can share up to 40 currently loaded TurboScan candidates (addresses and current/previous bytes) with AI.
- The user can approve read-only memory reads restricted to addresses and lengths in the currently loaded TurboScan results page.
- The user can approve a memory write only for a loaded candidate and explicit replacement bytes supplied in the conversation. The app reads live bytes, previews before/after, requires a second explicit approval, rechecks the original bytes, writes once, verifies read-back and attempts rollback if the write fails.
- The **VERIFY LOADED CANDIDATES (READ ONLY)** action compares up to 40 candidate snapshots against live process memory. A byte match only proves the snapshot still matches; it does not prove the address controls a particular game mechanic. Repeat controlled gameplay changes and scans before trusting a candidate.
- AI can propose a fixed-address hex cheat draft only from supplied/observed addresses and bytes. The app validates 1–32 patch steps (up to 4096 bytes each), previews the draft, and requires approval to save it disabled in the local game library. Saving a draft does not write to PS5 memory or prove the cheat effect.
- Existing cheat apply/restore paths use read-back verification and rollback attempts. Actual in-game effect still requires manual console/gameplay validation.

## Safety and limitations

- AI cannot infer memory addresses from pixels. Screen observations and memory candidates are separate evidence.
- No arbitrary shell commands, arbitrary remote payloads, or AI-generated code are executed.
- Memory writes, cheat application and activation remain user-controlled.
- Never commit API keys into GitHub or logs. API requests require internet and may incur provider charges.
- Physical PS5 protocol/device testing and end-to-end cheat-effect verification remain necessary before calling any cheat verified.
