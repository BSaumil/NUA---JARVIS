# Nua - AI Personal Assistant - PRD v3

## Product Overview
Nua is a next-generation AI personal assistant — intelligent, proactive, and deeply personal. She doesn't just answer questions; she acts, remembers, and anticipates your needs across weather, news, smart home, calendar, finances, and more.

## Tech Stack
- **Frontend**: React Native + Expo SDK 54, Expo Router (tab + stack navigation)
- **Backend**: FastAPI (Python) on port 8001
- **Database**: MongoDB (8 collections)
- **AI**: OpenAI GPT-5.2 (chat + vision) via Emergent LLM Key
- **Voice**: OpenAI TTS (Nova) + Whisper STT
- **Weather**: Open-Meteo API (free, no key required)
- **News**: RSS feeds (NYTimes, TechCrunch)

## All Features

### Core AI
1. **Intelligent Chat** - GPT-5.2 powered with personality, emotion detection, proactive behavior
2. **Persistent Memory Engine** - Auto-extracts & stores preferences from conversations
3. **Action-Oriented Agent** - Creates reminders/notes/expenses from natural language
4. **Multi-Modal Input** - Text + Voice (Whisper) + Image upload (GPT-5.2 Vision)
5. **Voice Output** - TTS with Nova voice for all AI responses
6. **Emotional Intelligence** - Detects stress/excitement/frustration, adapts tone

### Real-Time Data
7. **Weather** - Live weather via Open-Meteo API with 5-day forecast, city search
8. **News Feed** - RSS-powered headlines from top sources (NYTimes, TechCrunch) across 5 categories
9. **Proactive Suggestions** - Time-based smart tips on Dashboard

### Life Management
10. **Reminders** - Create via chat or manually, toggle completion
11. **Notes** - Quick notes with timestamps
12. **Expense Tracking** - Log expenses, category breakdown, spending summary
13. **Calendar** - Local event management (Google Calendar OAuth ready for setup)

### Smart Home (Simulation)
14. **8 Mock Devices** - Lights, thermostat, speaker, lock, camera, plug, blinds
15. **Room-based Layout** - Devices grouped by Living Room, Bedroom, Kitchen, Entrance
16. **4 Scenes** - Morning, Focus, Movie, Night (auto-configure multiple devices)
17. **Real-time Toggle** - Control device states instantly

### Skills Marketplace
18. **10 Skills** - Weather Pro, News Feed, Smart Home Hub, Calendar Sync, Fitness, Recipes, Translator, Meditation, Stocks, Travel
19. **Install/Uninstall** - Modular skill management
20. **Category Filtering** - Browse by Utility, Health, Lifestyle, Finance, etc.
21. **Ratings & Downloads** - Social proof for skill quality

### Wake Word (Dev Build Required)
22. **Always-listening Mode** - Hold-to-record voice activation
23. **Note**: Actual "Hey Nua" wake word requires native Picovoice/Porcupine SDK (dev build only)

## Navigation
- **Tab 1: Nua** - AI chat with text, voice, image
- **Tab 2: Dashboard** - Weather, news, stats, quick actions, smart home/marketplace access
- **Tab 3: Memory** - What Nua knows about you
- **Tab 4: Life Hub** - Reminders, notes, expenses
- **Stack: Smart Home** - Device controls & scenes
- **Stack: Marketplace** - Skills browsing & management
- **Stack: History** - Conversation archives & search

## API Endpoints (30+)
Weather: /api/weather, /api/weather/search
News: /api/news
Calendar: /api/calendar/events (CRUD), /api/calendar/status
Smart Home: /api/smart-home/devices, /api/smart-home/control, /api/smart-home/scene
Marketplace: /api/skills/marketplace, /api/skills/install/{id}
+ All previous endpoints (chat, memory, reminders, notes, expenses, tts, dashboard, etc.)

## Roadmap
- Google Calendar OAuth sync
- Real smart home API integration (Philips Hue, Google Nest)
- Spotify/Music control
- Real-time stock tracking
- Multi-user awareness
- Cross-platform continuity
- Developer SDK & public API
- Digital Twin
