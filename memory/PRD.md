# Nua - AI Personal Assistant - PRD v2

## Product Overview
Nua is a next-generation AI personal assistant — intelligent, proactive, and deeply personal. Inspired by JARVIS, Nua goes beyond answering questions to actually doing things: managing reminders, tracking expenses, taking notes, and remembering everything about you.

## Tech Stack
- **Frontend**: React Native + Expo SDK 54, Expo Router (tab navigation)
- **Backend**: FastAPI (Python) on port 8001
- **Database**: MongoDB (conversations, messages, user_memory, reminders, notes, expenses)
- **AI**: OpenAI GPT-5.2 via Emergent LLM Key
- **Voice**: OpenAI TTS (Nova voice) + Whisper STT
- **Vision**: GPT-5.2 multimodal (image analysis)

## Core Features

### 1. Persistent Memory & Context Engine
- Automatically extracts and stores user preferences, habits, and personal info from conversations
- Memory persists across conversations and is loaded into every AI interaction
- Categories: personal, preference, habit, work, general
- Manual memory management (add, delete, view)

### 2. Action-Oriented Agent
- Nua doesn't just answer — she acts
- Automatic action extraction from chat:
  - "Remind me to..." → Creates reminder
  - "Note that..." → Saves note
  - "I spent $20 on..." → Logs expense
  - "My favorite..." → Stores memory
- Uses structured action blocks parsed from AI responses

### 3. Multi-Modal Understanding
- Text chat with GPT-5.2
- Voice input via OpenAI Whisper (hold-to-record)
- Voice output via OpenAI TTS (Nova voice)
- Image upload and analysis (photo → AI understanding)

### 4. Emotional Intelligence
- Detects emotional cues (stress, excitement, frustration)
- Adapts response tone accordingly
- Wellness check-in quick action

### 5. Proactive Suggestions
- Time-based suggestions (morning planning, evening reflection)
- Context-aware tips based on user data
- Dashboard surfaces what matters most

### 6. Financial & Life Management
- **Expenses**: Log, categorize, view summary with category breakdown
- **Reminders**: Create, toggle completion, delete
- **Notes**: Quick notes with timestamps

### 7. Modular Skills System
- 16 skills total: 9 active, 4 coming soon, 3 roadmap
- Active: Chat, Voice, Vision, Memory, Reminders, Notes, Expenses, Emotions, Proactive
- Coming Soon: Smart Home, Calendar, Email, Spotify, Rides
- Roadmap: Digital Twin, Investment Insights

### 8. Dark Futuristic HUD UI
- Deep black (#050505) + burnished gold (#FFB800)
- Animated typing indicators, message transitions
- Voice waveform visualization
- 4-tab navigation: Nua (Chat), Dashboard, Memory, Life Hub

## API Endpoints (18 total)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/health | System health |
| POST | /api/chat | AI chat with memory + action extraction |
| POST | /api/chat/voice | Voice → transcribe → AI response |
| POST | /api/chat/image | Image + text → AI analysis |
| POST | /api/tts | Text-to-speech |
| GET | /api/conversations | List conversations |
| GET | /api/conversations/search | Search |
| GET | /api/conversations/{id} | Detail |
| DELETE | /api/conversations/{id} | Delete |
| GET | /api/memory | List memories |
| POST | /api/memory | Add memory |
| DELETE | /api/memory/{id} | Delete memory |
| GET/POST/PATCH/DELETE | /api/reminders | CRUD |
| GET/POST/DELETE | /api/notes | CRUD |
| GET/POST/DELETE | /api/expenses | CRUD + summary |
| GET | /api/dashboard | Dashboard data |
| GET | /api/skills | Skills list |
| POST | /api/quick-action | Quick actions |

## Roadmap (Future Features)
- Wake word detection ("Hey Nua")
- Smart Home Control (Philips Hue, Google Nest)
- Calendar & Email Integration
- Spotify, Uber, WhatsApp integration
- Real-time weather & news
- Autonomous multi-step task execution
- Cross-platform continuity
- Developer SDK & skills marketplace
- Digital Twin (AI acting on user's behalf)
- Per-user fine-tuned AI model
