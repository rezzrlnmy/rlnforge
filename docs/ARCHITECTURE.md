# rlnForge — Architecture

## Layers

```
UI (Compose, Material 3)
  ChatScreen / ProjectScreen / SettingsScreen
    |
ViewModels
  ChatViewModel (chat + proposal Apply/Discard)
  ProjectViewModel (projects + file editor)
  SettingsViewModel (providers + test connection)
    |
Agent output -> files (on-device Kotlin, v0.2)
  - CodeProposals.parseProposedChanges: fenced code block with a file
    path in its header (```kotlin:path/to/File.kt) becomes a proposal
  - Proposed change card: diff summary + Apply / Discard per file
  - Apply writes the file only after the user taps it; Discard changes nothing
    |
Providers
  - OpenAI-compatible client (OpenAI, OpenRouter, Custom; DSN Relay PARKED)
  - Anthropic client
  - Gemini client
    |
Storage
  - Room: chats, messages, providers, projects + project file metadata
  - Files on disk: filesDir/projects/<projectId>/<path> (content, not Room)
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
