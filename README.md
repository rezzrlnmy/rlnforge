# rlnForge

Open-source AI agent for Android. Chat with an agent, pick any model, and have it write a real native Android app for you.

## What it does

- Chat with an AI agent, not just a chatbot.
- The agent writes code — a full native Android app — from a prompt.
- The generated app is built into an APK and installed on your phone.

## Providers — your key, your choice

- **DSN Relay** — one key from us, routed to many models.
- **BYOK** — bring your own Anthropic, OpenAI, Gemini, or OpenRouter key.
- **Custom endpoint** — any OpenAI-compatible endpoint, including self-hosted.

Keys are stored encrypted on-device. They are never sent to our server unless you use DSN Relay.

## Stack

Kotlin + Jetpack Compose (the app, and the apps it generates). Node.js for the DSN Relay. Builds run on a build server / GitHub Actions.

## Status

v0.1 — spec + scaffold. See [SPEC.md](SPEC.md) for the full plan and roadmap.

## License

MIT. See [LICENSE](LICENSE).
