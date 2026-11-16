# NUA Sovereign / NUA JARVIS — Standalone & Credential Audit

**Audit date:** 16 November 2026  
**Target Android version:** 0.5.1 / versionCode 4  
**Wear version:** 0.2.0  
**Repository:** BSaumil/NUA---JARVIS  
**Audit rule:** distinguish code existence from product wiring and distinguish API credentials from Android permissions/external activation.

---

## Executive result

NUA no longer requires a Claude API key to launch.

The Android app supports a real local-only operating mode:
- local Room memory and Second Brain;
- deterministic/keyword actions;
- Android calendar, app/media actions and other official-API capabilities where the user grants Android permissions;
- Open-Meteo weather;
- Model Mesh private text inference through Gemini Nano/AICore when the OS model is actually AVAILABLE;
- private Decision counterfactuals;
- personality preferences;
- trust/autonomy state;
- recipes and UAF execution;
- Evolution state/tuning;
- Presence identity and Wear phone-presence publishing.

A Claude key is an optional cloud capability. It is still required for Claude-specific cloud inference and the current cloud-only vision/page-transcription path.

---

## Status legend

| Status | Meaning |
|---|---|
| STANDALONE | No third-party API credential required. May still need a normal Android runtime/special permission. |
| LOCAL_MODEL | No app API key, but requires compatible Android AICore/Gemini Nano already available on the device. |
| CLOUD_KEY | Requires the user's Anthropic Claude API key and internet. |
| BUILD_KEY | Requires a developer/build-time credential rather than a user runtime key. |
| OAUTH_ACTIVATION | Needs a provider developer project + OAuth consent/authorization; an API key alone is not sufficient. |
| TOKEN_ACTIVATION | Needs an external service token/configuration. |
| COMPANION_CONFIG | Needs the NUA companion service and a configured local model/runtime. |
| FOUNDATION_ONLY | Code/models/tests exist, but the capability is not yet fully wired to a user-facing flow. |
| DEVICE_PERMISSION | No API credential; Android permission/special access is the gate. |

---

## Android core capability matrix

| Capability | Status | Requirement / current reality |
|---|---|---|
| App launch / core UI | STANDALONE | No external key. |
| Local Room memory, facts, goals, decisions, dreams | STANDALONE | Android app sandbox; database itself is not yet SQLCipher-encrypted. |
| Second Brain search/timeline | STANDALONE | Local persisted state. |
| Personality humor/directness preferences | STANDALONE | Persisted locally; now applied to both local and Claude conversational prompts. |
| Keyword intent routing | STANDALONE | No model call for recognized deterministic actions. |
| Gemini Nano/AICore text intelligence | LOCAL_MODEL | No API key; requires the OS private model to report AVAILABLE. NUA does not silently download/provision it. |
| Main chat, no Claude key | LOCAL_MODEL | Routed to HIGH + requiresOffline Model Mesh. If the OS model is unavailable, NUA explains that local AI is unavailable instead of forcing a key. |
| Main chat with Claude | CLOUD_KEY | Optional Anthropic key + internet; streamed Claude UX retained. |
| Intent classification / fact extraction / planning / What Now / briefings / goal review / Dreams | LOCAL_MODEL or CLOUD_KEY | Model Mesh is local-first; cloud fallback only where privacy/task policy permits and a Claude key exists. HIGH/offline work cannot fall through to cloud. |
| Image/vision analysis | CLOUD_KEY | Current verified private OS provider is text-only; direct Claude multimodal path remains. |
| PDF page transcription / scanned-document OCR-equivalent vision | CLOUD_KEY | PDF pages are rasterized and transcribed by current vision path. |
| DOCX / already-extracted text summarization and Q&A | LOCAL_MODEL or CLOUD_KEY | Refreshed to Model Mesh; local Nano can handle text if available. |
| Decision “What if?” counterfactual | LOCAL_MODEL | Explicit HIGH + requiresOffline; never cloud-egresses Decision Journal content. |
| Calendar read/write/reminders/invitations | DEVICE_PERMISSION | Uses Android CalendarContract; no Google OAuth required for the local calendar provider. |
| Weather | STANDALONE + network/location | Open-Meteo; no API key. Coarse location is enough for weather. |
| Notifications read/reply | DEVICE_PERMISSION | Notification Listener special access. Reply uses official RemoteInput where supported. Stripped from Personal Test build. |
| Media control | DEVICE_PERMISSION | Uses Android media/session access; notification-listener access may be needed. |
| SMS sending | DEVICE_PERMISSION | SEND_SMS plus contact resolution where used; no service API key. Stripped from Personal Test build. |
| Geofencing | DEVICE_PERMISSION | Fine/background location; no API key. Stripped from Personal Test build. |
| Biometric step-up | STANDALONE | Android BiometricPrompt; no external key. |
| Manual tap-to-talk STT | STANDALONE | Android SpeechRecognizer; provider/device service availability still applies. |
| Android TTS | STANDALONE | Android TTS engine; no NUA API key. |
| Wake word / always-listening foreground path | BUILD_KEY | Picovoice Porcupine access key compiled in. Empty key disables the wake-word service honestly. |
| Owner voice verification/enrollment | BUILD_KEY | Picovoice Eagle uses the Picovoice access key/product entitlement. |
| Home-screen widget | STANDALONE | Local data; no external key. |
| Android Auto | DEVICE/PLATFORM REVIEW | Code entry point exists; no API key. Real DHU/head-unit and Play category validation still required. Stripped from Personal Test. |
| Wear phone-presence tile | STANDALONE | Uses Wearable Data Layer; no API key. Now publishes a narrow Presence v1 identity/activity slice and displays recency on Wear. Physical watch validation remains. |
| Accessibility automation | FOUNDATION_ONLY | Service scaffold exists but no new stale system-navigation action was reintroduced; side effects must be migrated through the current governed execution architecture first. Stripped from Personal Test. |

---

## Cloud and connector matrix

| Connector/service | Status | Requirement / wiring state |
|---|---|---|
| Open-Meteo | STANDALONE | Product-wired weather path; no key. |
| Hacker News public API | FOUNDATION_ONLY / no key | Manifest exists and is keyless, but no main user action/UI is wired to the catalog entry yet. |
| Frankfurter currency API | FOUNDATION_ONLY / no key | Manifest exists and is keyless; no main user-facing finance action yet. |
| Google / Gmail / Drive | OAUTH_ACTIVATION | Connector manifest declares Google scopes/hosts, but production OAuth activation/binding is not implemented. A simple API key is not enough. Main EmailRepository remains unconfigured. |
| Microsoft Graph / Outlook / OneDrive | OAUTH_ACTIVATION | Manifest exists; OAuth activation and product binding remain. |
| Dropbox | OAUTH_ACTIVATION | Manifest exists; OAuth activation and product binding remain. |
| Slack | OAUTH_ACTIVATION | Manifest exists; OAuth activation and product binding remain. |
| Home Assistant | TOKEN_ACTIVATION + product wiring | Connector framework supports bearer-token style access, but Android SmartHomeRepository is still bound to the unconfigured implementation. |
| Companion mounted files | COMPANION_CONFIG | Uses paired/signed companion path and explicit mounted roots; no cloud API key. |
| MCP/A2A remote tools | FOUNDATION_ONLY / explicit trust | Governed gateway exists behind UAF/privacy/trust; useful remote servers/tools still need owner configuration and product onboarding. |
| External skill imports | FOUNDATION_ONLY | Safe declarative importer/validation exists; user-facing marketplace/review/install flow is incomplete. |

---

## Companion / desktop / iOS

| Surface | Status | Requirement / current reality |
|---|---|---|
| NUA Companion service | STANDALONE | Pairing, presence, mounted files and sandbox do not require a cloud API key. Non-loopback serving requires TLS cert/key. |
| Companion local inference via Ollama | COMPANION_CONFIG | Set NUA_OLLAMA_URL; optional NUA_OLLAMA_MODEL. No Anthropic key needed. |
| Companion OpenAI-compatible local endpoint | COMPANION_CONFIG | Set NUA_LOCAL_OPENAI_URL; optional model setting. Intended for local/trusted endpoint. |
| Windows Tauri shell | STANDALONE / thin | CI-buildable executable; currently constrained to localhost health/presence surface. Signed installer/updater and richer paired jobs remain. |
| Shared Python desktop client | COMPANION_CONFIG | Pair/status/ask flows can talk to companion; ask needs a configured companion model. |
| iOS/iPadOS SwiftUI shell | STANDALONE / thin | Simulator-build verified; health/presence only. Physical signing/TestFlight and privileged pairing/jobs remain. |
| macOS/Linux | COMPANION_CONFIG | Companion service runs cross-platform; polished native control surfaces remain future work. |

---

## Deep Research and OpenJarvis-derived systems

| System | Status | Reality |
|---|---|---|
| DeepResearchEngine | FOUNDATION_ONLY | Resumable engine/evidence model exists and has tests. It is not yet a complete user-facing research product and broad search-source discovery is not fully wired. LOCAL_PRIVATE mode is compatible with no-key private Model Mesh. |
| WASM sandbox | ENGINE SHIPPED | Companion bounded compute exists; user-facing job UX and broader runtime integration remain. |
| Energy/thermal/cost routing | ENGINE SHIPPED | Routing policy/telemetry foundations exist; physical-device tuning/benchmarking remains. |
| Evolution Engine Phase 2 | ENGINE SHIPPED | Governed low-risk config/spec optimization exists; authority/security policy is not self-modifiable. |
| Sovereign Operators / Skill Packages | ENGINE SHIPPED | Runtime/package governance exists; end-user builder/marketplace still needs product UX. |
| Presence Mesh | ENGINE SHIPPED, DEVICE VALIDATION OPEN | Signed pairing/companion protocol exists; Wear read-only presence is now restored. Real multi-device acceptance/discovery/TLS UX is next. |

---

## Personal Test build

The dedicated `personalTest` Android variant:
- application id suffix: `.personaltest`;
- separate install from production NUA;
- removes SEND_SMS, READ_CONTACTS, fine/background location, QUERY_ALL_PACKAGES and battery-optimization exemption;
- removes Accessibility, Notification Listener, Android Auto and geofence components;
- has an APK-level CI audit that fails if the restricted permissions survive manifest merging.

This is the preferred sideload test build. It does not disable Play Protect.

---

## PR / branch reconciliation

- OpenJarvis benchmark/final, Evolution Engine, Claude OpenJarvis work and integration branches are already represented in Main.
- The dedicated Personal Test/local-first refresh branch contains the current new work.
- Old PR #34 (debug-manifest sideload workaround) is superseded by the dedicated personalTest variant and should be closed, not merged.
- The old October Claude branch was not safe to merge wholesale because it is far behind Main. Useful unique logic was selectively recovered:
  - personality axes/preferences;
  - local-only counterfactual decision simulator;
  - shadow accuracy;
  - CommitmentGraph temporal pure logic;
  - Wear Presence concept, reimplemented against current Presence v1.
- The old direct Accessibility system-navigation action is intentionally not restored. It needs a modern governed execution design first.

---

## Release blockers vs architecture blockers

### External activation, not code bugs
- Anthropic key for optional Claude cloud/vision.
- Picovoice build key/entitlement for wake word and voice ID.
- Google/Microsoft/Dropbox/Slack OAuth developer projects and grants.
- Home Assistant token/endpoint.
- Apple signing/TestFlight and Windows production code signing.
- Physical Android/Wear/Auto/iOS/Windows/macOS/Linux acceptance devices.

### Product wiring still required
- OAuth connector UI/bindings.
- Deep Research user experience + broad source discovery.
- external skills review/install marketplace.
- MCP/A2A server configuration/trust UI.
- full Presence Mesh discovery/pair/manage UX.
- richer Windows/iOS job/chat surfaces.
- local multimodal/vision path.
- smart-home implementation binding.

---

# Recommended next phase — NUA JARVIS 0.6

1. **Provider & Capability Onboarding**
   - one “NUA Ready” screen that detects AICore/Nano, Claude key, Picovoice, companion nodes, permissions and connector auth;
   - no mystery failures and no mandatory cloud prompt.

2. **Presence Mesh Productization**
   - mDNS/local discovery;
   - QR pairing UI and device manager;
   - LAN TLS trust UX;
   - Android ↔ Windows/macOS/Linux real inference handoff;
   - Wear device acceptance.

3. **Connector Activation Layer**
   - Google OAuth first: Gmail + Drive + Calendar;
   - Microsoft Graph second;
   - Home Assistant direct binding;
   - disconnect/revoke/scope UI.

4. **Provider-neutral streaming Model Mesh**
   - local and companion streaming through the same conversation interface;
   - Claude becomes one provider, not a special chat path.

5. **Local/Companion Multimodal**
   - route vision/PDF page understanding to a verified local/companion vision model when available;
   - remove the remaining hard Claude dependency from image/PDF intelligence.

6. **Deep Research Product**
   - search/source discovery connectors;
   - citation/evidence UI;
   - resumable jobs and private-doc-only mode.

7. **Sovereign Skills / Automation Builder UX**
   - review imported skill provenance/capability diff;
   - build/edit recipes/operators visually;
   - no executable-package bypass.

8. **World Model 2.0**
   - persist commitment entities and temporal validity;
   - expose counterfactuals/commitments in Command Centre;
   - strengthen proactive reasoning over goals, decisions and commitments.

9. **Cross-platform parity**
   - Windows tray/installer/updater + paired jobs;
   - iOS pairing/chat/research + TestFlight;
   - macOS control surface;
   - shared protocol conformance suite.

10. **Production hardening**
    - real Room migrations and migration tests;
    - encrypted local database;
    - release signing/store policy;
    - Play permission strategy;
    - device matrix, crash/diagnostic strategy, threat-model re-audit.

The architectural priority is now **productization and real-device sovereignty**, not adding another large agent framework.
