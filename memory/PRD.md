# Nua - AI Personal Assistant - PRD v4 (Intelligence Architecture)

## Product Overview
Nua is a next-generation AI personal assistant built on **7 Intelligence Layers** — a sentient-feeling AI operating system that doesn't just answer questions but thinks, remembers, learns, dreams, and earns trust.

## 7 Intelligence Layers

### Layer 1: NUA Core
- Intent understanding with confidence scoring
- Context engine with prioritization
- Multi-modal reasoning (text + voice + image)
- Tool routing & skill registry
- Error recovery & hallucination safeguards
- Action verification pipeline

### Layer 2: Memory OS (6 Intelligence Types)
- **Identity Memory**: Name, people, relationships, dates, preferences
- **Episodic Memory**: Conversations, events, decisions, purchases
- **Semantic Memory**: Facts, knowledge, projects, contacts
- **Behavioral Memory**: Habits, routines, patterns, preferences
- **Emotional Memory**: Feelings, reactions, context
- **Relationship Memory**: Connections between people
- **Controls**: Every memory is inspectable — "Why do you remember this?" with confidence scores, source, usage tracking, and the ability to forget

### Layer 3: Trust Engine
- **Trust Score**: 0-100%, earned over time through interactions
- **Levels**: New → Learning → Building Trust → Trusted → Highly Trusted → Autonomous
- **Scoring**: Success (+3), Failure (-5), Correction (-2), Rejection (-1), Confirmation (+1), Prediction (+2)
- **Trust Ledger**: Full inspectable history of all trust events
- **Adaptive Autonomy**: "You've approved this 17 times. Handle it automatically?"

### Layer 4: Autonomous Action Engine (T0-T5)
- **T0**: Read-only (information retrieval)
- **T1**: Auto-execute (low-risk, safe actions)
- **T2**: Notify + execute (execute and inform)
- **T3**: Confirm first (ask before executing)
- **T4**: Multi-step approval (plan review required)
- **T5**: Never autonomous (explicit approval always)
- Every action logged with intent, plan, risk, result, verification

### Layer 5: NUA Goals
- User-defined goals with AI breakdown
- Sub-task tracking with progress calculation
- NUA analyzes goals using GPT-5.2 and provides actionable insights
- Goal-oriented intelligence (not just command-response)

### Layer 6: NUA Dreams 2.0
- Cross-referenced insights generated from ALL user data
- 10 dream categories: Opportunity, Pattern, Reminder, Concern, Optimization, Relationship, Finance, Productivity, Learning, Business
- Generated based on behavior, conversations, memories, expenses, goals
- Non-obvious discoveries users haven't thought of

### Layer 7: Enhanced NUA Core
- System prompt integrates all layers dynamically
- Memory-aware responses using full Memory OS context
- Trust-aware behavior with autonomy calibration
- Goal-aware suggestions and proactive help

## Tech Stack
- **AI**: OpenAI GPT-5.2 (chat + vision + dreams + goal analysis) via Emergent LLM Key
- **Voice**: OpenAI TTS (Nova) + Whisper STT
- **Weather**: Open-Meteo API (free)
- **News**: RSS feeds (NYTimes, TechCrunch)
- **Backend**: FastAPI + MongoDB (12+ collections)
- **Frontend**: Expo SDK 54 + Expo Router

## Navigation
- **Tab 1: Nua** — AI chat (text, voice, image)
- **Tab 2: Dashboard** — Trust score, weather, latest dream, news, quick actions, smart home/marketplace access
- **Tab 3: Memory** — 6-type Memory OS with inspectable controls
- **Tab 4: Life Hub** — Goals, Dreams, Reminders, Notes, Expenses (5 sub-tabs)
- **Stack**: Smart Home, Marketplace, History

## MongoDB Collections (12+)
conversations, messages, user_memory, reminders, notes, expenses, calendar_events, goals, dreams, trust_ledger, action_log, installed_skills

## 30+ API Endpoints
Core: chat, chat/voice, chat/image, tts
Memory OS: memory/os, memory/advanced, memory/explain, memory/forget
Trust: trust, trust/ledger
Actions: actions/tiers, actions/log
Goals: goals (CRUD), goals/subtask, goals/analyze
Dreams: dreams, dreams/generate, dreams/act
Plus: weather, news, calendar, smart-home, skills, marketplace, dashboard, conversations
