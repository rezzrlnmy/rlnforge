# rlnForge — Architecture

## Layers

```
UI (Compose, Material 3)
  ChatScreen / ProjectScreen / SettingsScreen
    |
ViewModels
    |
Agent loop (on-device Kotlin)
  - builds prompt, calls provider, parses tool calls
  - applies file diffs only after user approval
    |
Providers
  - OpenAI-compatible client (DSN Relay, OpenAI, OpenRouter, custom)
  - Anthropic client
  - Gemini client
    |
Storage
  - Room: projects, chats, messages
  - EncryptedSharedPreferences: API keys
```

## Why on-device agent loop

BYOK and custom endpoints need no server. The phone talks straight to the
provider the user chose. The server only exists for two jobs: DSN Relay
(subscription routing) and building APKs.

## Build flow (v0.3)

```
prompt -> agent generates Kotlin Compose project
       -> project pushed to build server (LightVela / GitHub Actions)
       -> Gradle build -> APK artifact
       -> app downloads APK -> user installs
```

Gradle does not run on the rlnmy dev VM (Java network blocked there).
It runs on LightVela or GitHub Actions, where dependencies can download.
