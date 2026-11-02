# In-app AI Cheat Creator

## Initial integration

The Android app now includes a persistent conversational AI workspace.

- Configure an OpenAI-compatible API base URL, model name and API key once.
- The API key is encrypted with AES-GCM using a key held in Android Keystore. The plaintext key is not written to app preferences or source code.
- Leave the API-key field blank when changing the endpoint/model to retain the previously saved key.
- The provider settings and chat history are stored locally on the device.
- The connection test sends a minimal chat-completions request.
- Disconnect AI clears the saved provider settings and Keystore key.

Default provider values are OpenAI-compatible:
- Base URL: https://api.openai.com/v1
- Model: gpt-4o-mini

A compatible provider may be used by changing the base URL and model.

## Current interaction

The assistant receives basic app context (connection state, selected PID and available TurboScan comparison modes). It can walk the user through a controlled experiment: record a visible value, perform a game action, report the changed value, refine the scan, and validate candidate addresses. Its system instructions explicitly prohibit inventing screen observations, addresses or successful tests.

## Not yet implemented

This first integration is not yet the fully autonomous agent described in the product plan. It does not:
- capture or stream the PS5 screen to a vision model;
- call TurboScan or Memory Editor as AI tools;
- execute an AI-proposed scan/write/freeze without a dedicated approval workflow;
- autonomously verify candidate addresses or generate a validated cheat file.

Those require a structured tool-call layer over the existing scanner/editor, a persistent experiment state machine, explicit user approval for writes, and a supported PS5 video-capture path. Do not describe those capabilities as available until implemented and tested.

## Security and operational notes

- Never commit API keys into GitHub or logs.
- API calls require internet access and may incur provider charges.
- The API key is encrypted at rest on the device; removing app data or uninstalling the app may remove access to the saved key.
- AI output is untrusted advice. Treat candidate addresses as unverified until repeated memory observations support them.
