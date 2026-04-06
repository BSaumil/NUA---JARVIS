# J.A.R.V.I.S. AI Personal Assistant - PRD

## Product Overview
A personal AI assistant app inspired by Iron Man's JARVIS. Features intelligent conversations with a sassy personality, voice input/output capabilities, conversation history management, and quick action commands. Android-based beta version.

## Tech Stack
- **Frontend**: React Native with Expo SDK 54, Expo Router (tab-based navigation)
- **Backend**: FastAPI (Python) running on port 8001
- **Database**: MongoDB (local)
- **AI Model**: OpenAI GPT-5.2 via Emergent LLM Key
- **Voice**: OpenAI TTS (tts-1, onyx voice) + Whisper STT (whisper-1)
- **Integrations**: emergentintegrations library for all OpenAI services

## Features Implemented

### Core Features
1. **AI Chat with JARVIS Personality** - Sassy, witty British-humor AI assistant with multi-turn conversation support
2. **Voice Input (STT)** - Hold-to-record microphone with OpenAI Whisper transcription
3. **Voice Output (TTS)** - Text-to-speech playback of AI responses using OpenAI TTS
4. **Conversation History** - Persistent conversations stored in MongoDB with search
5. **Quick Actions** - Instant access to jokes, facts, motivation, code help, trivia, calculations

### UI/UX
- Dark futuristic HUD theme (Iron Man inspired)
- Gold/Amber (#FFB800) accent on deep black (#050505) background
- Animated typing indicators and message transitions
- Voice waveform visualization during recording
- Tab navigation: Chat, History, Quick Actions

## API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/health | System health check |
| POST | /api/chat | Send message, get AI response |
| POST | /api/chat/voice | Voice input → transcribe → AI response |
| POST | /api/tts | Text-to-speech generation |
| GET | /api/conversations | List all conversations |
| GET | /api/conversations/search?q= | Search conversations |
| GET | /api/conversations/{id} | Get conversation detail |
| DELETE | /api/conversations/{id} | Delete conversation |
| DELETE | /api/conversations | Clear all conversations |
| POST | /api/quick-action | Execute quick action |

## MongoDB Collections
- `conversations` - id, title, created_at, updated_at, message_count, last_message
- `messages` - id, conversation_id, role, content, created_at

## Upcoming Features (Roadmap)
- Smart Home Control
- Calendar Integration
- Email Briefing
- Stock Market Tracker
- Flight Status Monitor
- Image Recognition
- Real-time Weather
- News Briefing

## Authentication
- Skipped for beta version
- Planned: JWT-based auth or Google social login
