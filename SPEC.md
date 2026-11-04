# rlnForge — SPEC

> Open-source AI agent for Android. Chat with an agent, pick any model,
> and have it write a real native Android app for you.

- **Name:** rlnForge
- **Repo:** https://github.com/rezzylrn/rlnforge (public, MIT)
- **Package:** `com.rln.forge`
- **Status:** v0.1 — spec + scaffold. App code is built by Hermes, directed/reviewed by rlnmy.

## 1. What it is

A Muse-style agent app for Android, open source:

1. Chat with an AI agent (not just a chatbot).
2. The agent writes code — a full native Android app — from a prompt.
3. The generated app is built (APK) and previewed / installed on a real phone.

It is **not** a wrapper around one provider. The user chooses where the
intelligence comes from.

## 2. Providers — three ways to pay for intelligence

| Mode | How it works |
| --- | --- |
| **DSN Relay (subscription, ours)** | One key from us. Our gateway routes to many models. Billed by us. |
| **BYOK** | User pastes their own key: Anthropic, OpenAI, Gemini, OpenRouter, etc. They pay the provider directly. |
| **Custom endpoint** | User sets base URL + key + model name. Any OpenAI-compatible (or Anthropic-compatible) endpoint works, including self-hosted / local servers. |

Rules:

- Keys are stored encrypted on-device (`EncryptedSharedPreferences`). They are
  never sent to our server except when the DSN Relay mode is active.
- Model is always `provider/model`, switchable per chat.
- No key is ever logged, committed, or pasted into a file.

## 3. Stack (locked 2026-11-04)

- **App itself:** Kotlin, Jetpack Compose, Material 3, single-activity, single-module.
- **Generated apps:** same stack — Kotlin + Compose, single-activity,
  single-module. This matches Google AI Studio Build output exactly, so
  generated code can be opened in AI Studio / Android Studio with no conversion.
- **Local storage:** Room (projects, chats) + DataStore (settings).
- **Agent loop:** on-device Kotlin. Direct API calls to the chosen provider.
  No server needed for BYOK / custom endpoint.
- **DSN Relay backend:** Node.js + Express, OpenAI-compatible (`/v1/chat/completions`,
  `/v1/models`). Separate service, separate repo/scope — billing lives there.
- **Build server:** LightVela VM and/or GitHub Actions. Gradle on the rlnmy VM
  cannot download dependencies (Java network is blocked in that sandbox), so
  it is **not** a build server.

## 4. Preview — what is real and what is not

- **Google AI Studio (prototyping):** Build mode generates Kotlin + Compose,
  previews in a cloud Android emulator in the browser, installs to a physical
  phone over ADB, and can publish to the Play Internal Test Track. This is a
  *development tool we use*, not a component embedded in rlnForge.
  Limits: Compose only, no Java/XML/NDK, cloud emulator cannot test camera,
  NFC, Bluetooth, or Google Play Services (Maps, sign-in).
- **Firebase Studio:** do not use. Sunsetting 22 Mar 2027, new workspaces
  closed 22 Jun 2026.
- **In rlnForge v1, preview =** install the built APK on the device, plus
  screenshots/video from a server-side emulator where available. Live emulator
  streaming inside our own app is out of scope for v1.

## 5. MVP roadmap

### v0.1 — Chat + providers (this repo now)
- Chat UI (Compose, Material 3, dark calm low-contrast)
- Provider manager: DSN Relay, Anthropic, OpenAI, Gemini, OpenRouter, Custom
- Model picker per chat, encrypted key storage
- Streaming responses, markdown + code blocks

### v0.2 — Agent mode
- Tool loop: list/read/write files in a project workspace, apply diffs
- Project view: file tree, file viewer, diff preview before apply
- Approval gate: agent proposes changes, user approves before files change

### v0.3 — Builder
- Prompt -> full Kotlin Compose project scaffold
- Send to build server -> APK artifact -> download + install
- Export project as ZIP / push to GitHub

### Later (not v1)
- DSN Relay billing / subscriptions
- Play Internal Track publish from the app
- Multi-agent / sub-agents

## 6. Security / privacy

- No telemetry by default.
- BYOK keys never leave the device except to the provider the user chose.
- Generated code runs only after the user approves a build; builds run on the
  build server, not with device permissions.
- No camera / location / contact harvesting. That is out of scope, permanently.

## 7. Repo layout (planned)

```
rlnforge/
  SPEC.md
  README.md
  app/                  # Android app (Kotlin + Compose)
    src/main/
      AndroidManifest.xml
      java/com/rln/forge/
        MainActivity.kt
        chat/           # chat UI + viewmodel
        providers/      # provider clients (OpenAI-compatible, Anthropic, Gemini)
        agent/          # tool loop
        projects/       # project storage, file tree
  relay/                # DSN Relay (Node.js) — scope terpisah, v0.2+
  docs/
    ARCHITECTURE.md
```

## 8. Definition of done (v0.1)

- [ ] App builds on GitHub Actions / LightVela and installs on a real phone
- [ ] Chat works end-to-end with at least one BYOK provider + one custom endpoint
- [ ] Keys survive restart, encrypted, never in logs
- [ ] README is short, plain English, no AI slop
