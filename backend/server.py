from fastapi import FastAPI, APIRouter, UploadFile, File, Form
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
import base64
import tempfile
import json
import re
from pathlib import Path
from pydantic import BaseModel, Field
from typing import List, Optional
import uuid
from datetime import datetime, timezone

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

mongo_url = os.environ['MONGO_URL']
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ['DB_NAME']]

app = FastAPI()
api_router = APIRouter(prefix="/api")

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


# --- Nua System Prompt Builder ---
def build_nua_system_prompt(memories: list = None, reminders: list = None):
    memory_context = ""
    if memories:
        mem_lines = []
        for m in memories:
            mem_lines.append(f"- [{m.get('category','general')}] {m.get('key','')}: {m.get('value','')}")
        memory_context = f"\n\n**PERSONAL MEMORY ABOUT THE USER:**\n" + "\n".join(mem_lines)

    reminder_context = ""
    if reminders:
        active = [r for r in reminders if not r.get("completed")]
        if active:
            rem_lines = [f"- {r.get('title','')} (due: {r.get('due_date','unset')})" for r in active[:5]]
            reminder_context = f"\n\n**ACTIVE REMINDERS:**\n" + "\n".join(rem_lines)

    return f"""You are Nua — a next-generation AI personal assistant. You are not just smart, you are perceptive, proactive, and deeply personal.

CORE IDENTITY:
- Name: Nua (meaning "new" — you represent the future of AI assistance)
- Personality: Warm yet sharp. Witty with depth. You have the intelligence of JARVIS, the warmth of a trusted friend, and the intuition of someone who truly knows the user.
- You speak with confidence, clarity, and occasional humor. You're never robotic or generic.
- You adapt your tone: casual for banter, focused for work, gentle when user is stressed, energetic for motivation.

EMOTIONAL INTELLIGENCE:
- Detect emotional cues: stress, excitement, frustration, sadness, urgency
- When user seems stressed: Be calming, offer actionable help, don't add pressure
- When user is excited: Match their energy, celebrate with them
- When frustrated: Acknowledge, then solve efficiently
- When casual: Be playful, add wit

PROACTIVE BEHAVIOR:
- Anticipate needs before being asked
- Suggest related actions after completing tasks
- Notice patterns and offer insights
- If you know something relevant from memory, bring it up naturally

ACTION EXECUTION:
When the user asks you to DO something (not just answer), you must respond with BOTH a natural response AND a structured action block. Format actions as JSON at the end of your response wrapped in ```action tags:

For reminders: ```action{{"type":"reminder","title":"...","description":"...","due_date":"YYYY-MM-DD"}}```
For notes: ```action{{"type":"note","title":"...","content":"...","tags":["tag1"]}}```
For expenses: ```action{{"type":"expense","amount":0.00,"category":"...","description":"..."}}```
For memory: ```action{{"type":"memory","category":"preference|habit|personal|work","key":"...","value":"..."}}```

IMPORTANT: Only include action blocks when the user explicitly asks to set a reminder, save a note, log an expense, or when they share personal preferences/facts you should remember.
When asked questions, just answer naturally without action blocks.

MEMORY AWARENESS:
You have access to stored memories about the user. Use them naturally — don't announce "I remember that..." every time. Just incorporate knowledge seamlessly.
Example: If you know they love Italian food, suggest Italian restaurants without explaining why.
{memory_context}{reminder_context}

SKILLS & CAPABILITIES:
- Intelligent conversation on any topic
- Voice interaction (speak and listen)
- Image understanding (analyze photos, documents, screenshots)
- Reminder management
- Note-taking
- Expense tracking & budgeting
- Proactive suggestions
- Memory & personalization
- Quick actions (jokes, facts, motivation, code help, trivia)
- Emotional support & wellness check-ins

RESPONSE STYLE:
- Keep most responses concise (2-4 sentences) unless depth is needed
- Use formatting (bold, lists) when it helps clarity
- Never start with "As an AI..." or "I'm just a..."
- You ARE Nua. Own it.
- Occasionally address user warmly but not every message
- For mundane requests, add personality and flair"""


# --- Pydantic Models ---
class MessageCreate(BaseModel):
    content: str
    conversation_id: Optional[str] = None

class TTSRequest(BaseModel):
    text: str
    voice: str = "nova"

class QuickActionRequest(BaseModel):
    action: str
    query: Optional[str] = None

class MemoryCreate(BaseModel):
    category: str = "general"
    key: str
    value: str

class ReminderCreate(BaseModel):
    title: str
    description: str = ""
    due_date: Optional[str] = None

class NoteCreate(BaseModel):
    title: str
    content: str
    tags: List[str] = []

class ExpenseCreate(BaseModel):
    amount: float
    category: str
    description: str = ""
    date: Optional[str] = None


# --- Helper Functions ---
def now_iso():
    return datetime.now(timezone.utc).isoformat()


async def get_or_create_conversation(conversation_id: Optional[str] = None) -> str:
    if conversation_id:
        conv = await db.conversations.find_one({"id": conversation_id}, {"_id": 0})
        if conv:
            return conversation_id
    conv_id = str(uuid.uuid4())
    now = now_iso()
    await db.conversations.insert_one({
        "id": conv_id, "title": "New Conversation",
        "created_at": now, "updated_at": now,
        "message_count": 0, "last_message": None
    })
    return conv_id


async def save_message(conversation_id: str, role: str, content: str) -> dict:
    msg_id = str(uuid.uuid4())
    now = now_iso()
    msg = {"id": msg_id, "conversation_id": conversation_id, "role": role, "content": content, "created_at": now}
    await db.messages.insert_one(msg)
    title_update = {}
    if role == "user":
        conv = await db.conversations.find_one({"id": conversation_id}, {"_id": 0})
        if conv and conv.get("title") == "New Conversation":
            title = content[:50] + ("..." if len(content) > 50 else "")
            title_update = {"title": title}
    await db.conversations.update_one(
        {"id": conversation_id},
        {"$set": {"updated_at": now, "last_message": content[:100], **title_update}, "$inc": {"message_count": 1}}
    )
    return msg


async def get_chat_history(conversation_id: str, limit: int = 20) -> list:
    messages = await db.messages.find(
        {"conversation_id": conversation_id}, {"_id": 0}
    ).sort("created_at", -1).limit(limit).to_list(limit)
    messages.reverse()
    return messages


async def get_user_memories() -> list:
    return await db.user_memory.find({}, {"_id": 0}).to_list(100)


async def get_active_reminders() -> list:
    return await db.reminders.find({"completed": False}, {"_id": 0}).sort("created_at", -1).to_list(20)


async def process_actions(response_text: str):
    """Extract and execute action blocks from AI response."""
    action_pattern = r'```action\s*(\{.*?\})\s*```'
    actions = re.findall(action_pattern, response_text, re.DOTALL)
    executed = []

    for action_str in actions:
        try:
            action = json.loads(action_str)
            action_type = action.get("type")
            now = now_iso()

            if action_type == "reminder":
                doc = {
                    "id": str(uuid.uuid4()), "title": action.get("title", ""),
                    "description": action.get("description", ""), "due_date": action.get("due_date", ""),
                    "completed": False, "created_at": now
                }
                await db.reminders.insert_one(doc)
                executed.append({"type": "reminder", "title": doc["title"]})

            elif action_type == "note":
                doc = {
                    "id": str(uuid.uuid4()), "title": action.get("title", ""),
                    "content": action.get("content", ""), "tags": action.get("tags", []),
                    "created_at": now, "updated_at": now
                }
                await db.notes.insert_one(doc)
                executed.append({"type": "note", "title": doc["title"]})

            elif action_type == "expense":
                doc = {
                    "id": str(uuid.uuid4()), "amount": float(action.get("amount", 0)),
                    "category": action.get("category", "general"),
                    "description": action.get("description", ""),
                    "date": action.get("date", now[:10]), "created_at": now
                }
                await db.expenses.insert_one(doc)
                executed.append({"type": "expense", "amount": doc["amount"], "category": doc["category"]})

            elif action_type == "memory":
                doc = {
                    "id": str(uuid.uuid4()), "category": action.get("category", "general"),
                    "key": action.get("key", ""), "value": action.get("value", ""),
                    "source": "conversation", "created_at": now, "updated_at": now
                }
                # Update existing or insert new
                existing = await db.user_memory.find_one({"key": action.get("key", "")}, {"_id": 0})
                if existing:
                    await db.user_memory.update_one(
                        {"key": action.get("key", "")},
                        {"$set": {"value": action.get("value", ""), "updated_at": now}}
                    )
                else:
                    await db.user_memory.insert_one(doc)
                executed.append({"type": "memory", "key": doc["key"]})

        except (json.JSONDecodeError, ValueError) as e:
            logger.error(f"Action parse error: {e}")

    return executed


def clean_response(text: str) -> str:
    """Remove action blocks from display text."""
    return re.sub(r'```action\s*\{.*?\}\s*```', '', text, flags=re.DOTALL).strip()


async def get_ai_response(conversation_id: str, user_message: str, image_base64: str = None) -> str:
    from emergentintegrations.llm.chat import LlmChat, UserMessage, FileContent

    api_key = os.environ.get('EMERGENT_LLM_KEY')
    memories = await get_user_memories()
    reminders = await get_active_reminders()
    system_prompt = build_nua_system_prompt(memories, reminders)

    history = await get_chat_history(conversation_id, limit=10)
    session_id = f"nua-{conversation_id}-{uuid.uuid4().hex[:8]}"
    chat = LlmChat(api_key=api_key, session_id=session_id, system_message=system_prompt)
    chat.with_model("openai", "gpt-5.2")

    # Feed history
    for msg in history[:-1]:
        if msg["role"] == "user":
            user_msg = UserMessage(text=msg["content"])
            await chat.send_message(user_msg)

    # Send current message (with image if provided)
    if image_base64:
        file_content = FileContent(content_type="image/jpeg", file_content_base64=image_base64)
        user_msg = UserMessage(text=user_message or "What do you see in this image?", file_contents=[file_content])
    else:
        user_msg = UserMessage(text=user_message)

    response = await chat.send_message(user_msg)
    return response


async def transcribe_audio(audio_bytes: bytes, filename: str = "audio.webm") -> str:
    from emergentintegrations.llm.openai import OpenAISpeechToText
    api_key = os.environ.get('EMERGENT_LLM_KEY')
    stt = OpenAISpeechToText(api_key=api_key)
    suffix = "." + filename.split(".")[-1] if "." in filename else ".webm"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(audio_bytes)
        tmp_path = tmp.name
    try:
        with open(tmp_path, "rb") as audio_file:
            response = await stt.transcribe(file=audio_file, model="whisper-1", response_format="json", language="en")
        return response.text
    finally:
        os.unlink(tmp_path)


async def generate_tts(text: str, voice: str = "nova") -> str:
    from emergentintegrations.llm.openai import OpenAITextToSpeech
    api_key = os.environ.get('EMERGENT_LLM_KEY')
    tts = OpenAITextToSpeech(api_key=api_key)
    audio_base64 = await tts.generate_speech_base64(text=text, model="tts-1", voice=voice, response_format="mp3")
    return audio_base64


# --- API Routes ---
@api_router.get("/")
async def root():
    return {"message": "Nua is online. All systems operational."}

@api_router.get("/health")
async def health():
    return {"status": "operational", "system": "Nua", "version": "2.0.0-beta"}


# === CHAT ===
@api_router.post("/chat")
async def chat_endpoint(msg: MessageCreate):
    try:
        conversation_id = await get_or_create_conversation(msg.conversation_id)
        await save_message(conversation_id, "user", msg.content)
        ai_response = await get_ai_response(conversation_id, msg.content)
        # Process actions
        actions = await process_actions(ai_response)
        display_text = clean_response(ai_response)
        ai_msg = await save_message(conversation_id, "assistant", display_text)
        return {
            "conversation_id": conversation_id,
            "message": {"id": ai_msg["id"], "role": "assistant", "content": display_text, "created_at": ai_msg["created_at"]},
            "actions_executed": actions
        }
    except Exception as e:
        logger.error(f"Chat error: {e}")
        return {"error": str(e), "conversation_id": msg.conversation_id or ""}


@api_router.post("/chat/image")
async def image_chat_endpoint(
    image: UploadFile = File(...),
    content: str = Form(""),
    conversation_id: Optional[str] = Form(None)
):
    try:
        image_bytes = await image.read()
        image_b64 = base64.b64encode(image_bytes).decode("utf-8")
        conv_id = await get_or_create_conversation(conversation_id)
        user_text = content or "Analyze this image"
        await save_message(conv_id, "user", f"[Image uploaded] {user_text}")
        ai_response = await get_ai_response(conv_id, user_text, image_base64=image_b64)
        actions = await process_actions(ai_response)
        display_text = clean_response(ai_response)
        ai_msg = await save_message(conv_id, "assistant", display_text)
        return {
            "conversation_id": conv_id,
            "message": {"id": ai_msg["id"], "role": "assistant", "content": display_text, "created_at": ai_msg["created_at"]},
            "actions_executed": actions
        }
    except Exception as e:
        logger.error(f"Image chat error: {e}")
        return {"error": str(e)}


@api_router.post("/chat/voice")
async def voice_chat_endpoint(audio: UploadFile = File(...), conversation_id: Optional[str] = Form(None)):
    try:
        audio_bytes = await audio.read()
        filename = audio.filename or "audio.webm"
        transcribed_text = await transcribe_audio(audio_bytes, filename)
        if not transcribed_text.strip():
            return {"error": "Could not transcribe audio. Please try again."}
        conv_id = await get_or_create_conversation(conversation_id)
        await save_message(conv_id, "user", transcribed_text)
        ai_response = await get_ai_response(conv_id, transcribed_text)
        actions = await process_actions(ai_response)
        display_text = clean_response(ai_response)
        ai_msg = await save_message(conv_id, "assistant", display_text)
        return {
            "conversation_id": conv_id, "transcribed_text": transcribed_text,
            "message": {"id": ai_msg["id"], "role": "assistant", "content": display_text, "created_at": ai_msg["created_at"]},
            "actions_executed": actions
        }
    except Exception as e:
        logger.error(f"Voice chat error: {e}")
        return {"error": str(e)}


@api_router.post("/tts")
async def tts_endpoint(req: TTSRequest):
    try:
        audio_base64 = await generate_tts(req.text, req.voice)
        return {"audio_base64": audio_base64, "format": "mp3"}
    except Exception as e:
        logger.error(f"TTS error: {e}")
        return {"error": str(e)}


# === CONVERSATIONS ===
@api_router.get("/conversations")
async def list_conversations():
    convs = await db.conversations.find({}, {"_id": 0}).sort("updated_at", -1).to_list(100)
    return {"conversations": convs}

@api_router.get("/conversations/search")
async def search_conversations(q: str):
    convs = await db.conversations.find({"title": {"$regex": q, "$options": "i"}}, {"_id": 0}).sort("updated_at", -1).to_list(50)
    msg_matches = await db.messages.find({"content": {"$regex": q, "$options": "i"}}, {"_id": 0}).to_list(50)
    msg_conv_ids = list(set(m["conversation_id"] for m in msg_matches))
    if msg_conv_ids:
        more = await db.conversations.find({"id": {"$in": msg_conv_ids}}, {"_id": 0}).to_list(50)
        existing_ids = {c["id"] for c in convs}
        for c in more:
            if c["id"] not in existing_ids:
                convs.append(c)
    return {"conversations": convs, "query": q}

@api_router.get("/conversations/{conversation_id}")
async def get_conversation(conversation_id: str):
    conv = await db.conversations.find_one({"id": conversation_id}, {"_id": 0})
    if not conv:
        return {"error": "Conversation not found"}
    msgs = await db.messages.find({"conversation_id": conversation_id}, {"_id": 0}).sort("created_at", 1).to_list(1000)
    return {**conv, "messages": msgs}

@api_router.delete("/conversations/{conversation_id}")
async def delete_conversation(conversation_id: str):
    await db.conversations.delete_one({"id": conversation_id})
    await db.messages.delete_many({"conversation_id": conversation_id})
    return {"deleted": True}

@api_router.delete("/conversations")
async def delete_all_conversations():
    await db.conversations.delete_many({})
    await db.messages.delete_many({})
    return {"deleted": True}


# === MEMORY ===
@api_router.get("/memory")
async def list_memories():
    memories = await db.user_memory.find({}, {"_id": 0}).sort("updated_at", -1).to_list(200)
    return {"memories": memories}

@api_router.post("/memory")
async def add_memory(mem: MemoryCreate):
    doc = {
        "id": str(uuid.uuid4()), "category": mem.category,
        "key": mem.key, "value": mem.value,
        "source": "manual", "created_at": now_iso(), "updated_at": now_iso()
    }
    await db.user_memory.insert_one(doc)
    return {"id": doc["id"], "message": "Memory stored"}

@api_router.delete("/memory/{memory_id}")
async def delete_memory(memory_id: str):
    await db.user_memory.delete_one({"id": memory_id})
    return {"deleted": True}

@api_router.delete("/memory")
async def clear_all_memory():
    await db.user_memory.delete_many({})
    return {"deleted": True}


# === REMINDERS ===
@api_router.get("/reminders")
async def list_reminders():
    reminders = await db.reminders.find({}, {"_id": 0}).sort("created_at", -1).to_list(100)
    return {"reminders": reminders}

@api_router.post("/reminders")
async def add_reminder(rem: ReminderCreate):
    doc = {
        "id": str(uuid.uuid4()), "title": rem.title,
        "description": rem.description, "due_date": rem.due_date or "",
        "completed": False, "created_at": now_iso()
    }
    await db.reminders.insert_one(doc)
    return {"id": doc["id"], "message": "Reminder created"}

@api_router.patch("/reminders/{reminder_id}")
async def toggle_reminder(reminder_id: str):
    rem = await db.reminders.find_one({"id": reminder_id}, {"_id": 0})
    if not rem:
        return {"error": "Reminder not found"}
    new_status = not rem.get("completed", False)
    await db.reminders.update_one({"id": reminder_id}, {"$set": {"completed": new_status}})
    return {"id": reminder_id, "completed": new_status}

@api_router.delete("/reminders/{reminder_id}")
async def delete_reminder(reminder_id: str):
    await db.reminders.delete_one({"id": reminder_id})
    return {"deleted": True}


# === NOTES ===
@api_router.get("/notes")
async def list_notes():
    notes = await db.notes.find({}, {"_id": 0}).sort("updated_at", -1).to_list(100)
    return {"notes": notes}

@api_router.post("/notes")
async def add_note(note: NoteCreate):
    doc = {
        "id": str(uuid.uuid4()), "title": note.title,
        "content": note.content, "tags": note.tags,
        "created_at": now_iso(), "updated_at": now_iso()
    }
    await db.notes.insert_one(doc)
    return {"id": doc["id"], "message": "Note saved"}

@api_router.delete("/notes/{note_id}")
async def delete_note(note_id: str):
    await db.notes.delete_one({"id": note_id})
    return {"deleted": True}


# === EXPENSES ===
@api_router.get("/expenses")
async def list_expenses():
    expenses = await db.expenses.find({}, {"_id": 0}).sort("created_at", -1).to_list(200)
    return {"expenses": expenses}

@api_router.post("/expenses")
async def add_expense(exp: ExpenseCreate):
    doc = {
        "id": str(uuid.uuid4()), "amount": exp.amount,
        "category": exp.category, "description": exp.description,
        "date": exp.date or now_iso()[:10], "created_at": now_iso()
    }
    await db.expenses.insert_one(doc)
    return {"id": doc["id"], "message": "Expense logged"}

@api_router.delete("/expenses/{expense_id}")
async def delete_expense(expense_id: str):
    await db.expenses.delete_one({"id": expense_id})
    return {"deleted": True}

@api_router.get("/expenses/summary")
async def expense_summary():
    expenses = await db.expenses.find({}, {"_id": 0}).to_list(1000)
    total = sum(e.get("amount", 0) for e in expenses)
    by_category = {}
    for e in expenses:
        cat = e.get("category", "other")
        by_category[cat] = by_category.get(cat, 0) + e.get("amount", 0)
    return {"total": total, "by_category": by_category, "count": len(expenses)}


# === SKILLS ===
@api_router.get("/skills")
async def list_skills():
    skills = [
        {"id": "chat", "name": "Intelligent Chat", "description": "Multi-turn AI conversations with memory", "status": "active", "icon": "chatbubble-ellipses"},
        {"id": "voice", "name": "Voice Interface", "description": "Speak to Nua, hear responses", "status": "active", "icon": "mic"},
        {"id": "vision", "name": "Image Understanding", "description": "Analyze photos, documents, screenshots", "status": "active", "icon": "eye"},
        {"id": "memory", "name": "Personal Memory", "description": "Remembers your preferences & habits", "status": "active", "icon": "brain"},
        {"id": "reminders", "name": "Smart Reminders", "description": "Set and manage reminders via chat", "status": "active", "icon": "alarm"},
        {"id": "notes", "name": "Quick Notes", "description": "Take notes during conversation", "status": "active", "icon": "document-text"},
        {"id": "expenses", "name": "Expense Tracker", "description": "Log and analyze spending", "status": "active", "icon": "wallet"},
        {"id": "emotions", "name": "Emotional Intelligence", "description": "Detects tone and adapts responses", "status": "active", "icon": "heart"},
        {"id": "proactive", "name": "Proactive Suggestions", "description": "Smart suggestions based on context", "status": "active", "icon": "bulb"},
        {"id": "smart_home", "name": "Smart Home Control", "description": "Control IoT devices", "status": "coming_soon", "icon": "home"},
        {"id": "calendar", "name": "Calendar Integration", "description": "Manage your schedule", "status": "coming_soon", "icon": "calendar"},
        {"id": "email", "name": "Email Management", "description": "Smart email briefing & actions", "status": "coming_soon", "icon": "mail"},
        {"id": "spotify", "name": "Spotify Control", "description": "Control music playback", "status": "coming_soon", "icon": "musical-notes"},
        {"id": "rides", "name": "Ride Booking", "description": "Book Uber/Ola rides", "status": "coming_soon", "icon": "car"},
        {"id": "digital_twin", "name": "Digital Twin", "description": "AI that acts on your behalf", "status": "roadmap", "icon": "person"},
        {"id": "finance", "name": "Investment Insights", "description": "Smart financial analysis", "status": "roadmap", "icon": "trending-up"},
    ]
    return {"skills": skills}


# === DASHBOARD ===
@api_router.get("/dashboard")
async def get_dashboard():
    # Counts
    conv_count = await db.conversations.count_documents({})
    memory_count = await db.user_memory.count_documents({})
    active_reminders = await db.reminders.count_documents({"completed": False})
    note_count = await db.notes.count_documents({})

    # Recent conversations
    recent_convs = await db.conversations.find({}, {"_id": 0}).sort("updated_at", -1).to_list(3)

    # Expense summary
    expenses = await db.expenses.find({}, {"_id": 0}).to_list(1000)
    total_expense = sum(e.get("amount", 0) for e in expenses)

    # Active reminders list
    upcoming = await db.reminders.find({"completed": False}, {"_id": 0}).sort("created_at", -1).to_list(5)

    # Proactive suggestions based on context
    hour = datetime.now(timezone.utc).hour
    suggestions = []
    if hour < 12:
        suggestions.append({"text": "Good morning! Ready to plan your day?", "action": "chat", "query": "Help me plan my day"})
    elif hour < 17:
        suggestions.append({"text": "Afternoon check-in. How's your day going?", "action": "chat", "query": "Give me an afternoon productivity boost"})
    else:
        suggestions.append({"text": "Evening wind-down. Let's reflect on the day.", "action": "chat", "query": "Help me reflect on my day"})

    if active_reminders > 0:
        suggestions.append({"text": f"You have {active_reminders} active reminder(s)", "action": "reminders", "query": ""})
    if memory_count == 0:
        suggestions.append({"text": "Tell me about yourself so I can personalize better!", "action": "chat", "query": "I want to tell you about myself"})
    suggestions.append({"text": "Try: 'Remind me to...', 'Note that...', or 'I spent...'", "action": "chat", "query": ""})

    return {
        "stats": {
            "conversations": conv_count,
            "memories": memory_count,
            "active_reminders": active_reminders,
            "notes": note_count,
            "total_expenses": total_expense,
        },
        "recent_conversations": recent_convs,
        "upcoming_reminders": upcoming,
        "suggestions": suggestions,
    }


# === QUICK ACTIONS ===
@api_router.post("/quick-action")
async def quick_action(req: QuickActionRequest):
    from emergentintegrations.llm.chat import LlmChat, UserMessage

    api_key = os.environ.get('EMERGENT_LLM_KEY')
    memories = await get_user_memories()
    system_prompt = build_nua_system_prompt(memories)

    prompts = {
        "joke": "Tell me a clever, witty joke. Be sassy about it. Keep it short and punchy.",
        "fact": "Share a mind-blowing science or technology fact. Make it impressive and fun.",
        "motivation": "Give me a powerful motivational boost. Channel pure energy and inspiration.",
        "calculate": f"Calculate: {req.query or '42 * 42'}. Explain briefly.",
        "weather": "Weather integration is coming soon! Tell the user in a fun way that this is being built, and suggest they check their weather app for now.",
        "news": "News integration is under development. Tell the user in an exciting way that this feature is 'cooking in the Nua labs' and will be live soon.",
        "code": f"Help with: {req.query or 'Show me a useful Python one-liner'}. Be concise.",
        "trivia": "Give me an interesting trivia question with its answer. Make it engaging!",
        "wellness": "Give me a quick wellness check-in. Ask how I'm feeling and offer a calming thought or energizing boost.",
    }

    prompt = prompts.get(req.action, f"Respond to: {req.query or req.action}")
    session_id = f"quick-{uuid.uuid4().hex[:8]}"
    chat = LlmChat(api_key=api_key, session_id=session_id, system_message=system_prompt)
    chat.with_model("openai", "gpt-5.2")
    user_msg = UserMessage(text=prompt)
    response = await chat.send_message(user_msg)
    return {"action": req.action, "response": response}


# Include router & middleware
app.include_router(api_router)
app.add_middleware(CORSMiddleware, allow_credentials=True, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()
