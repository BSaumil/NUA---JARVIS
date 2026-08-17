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
import httpx
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

class SceneActivation(BaseModel):
    scene: str


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


# === WEATHER (Open-Meteo - Free, no API key) ===
WEATHER_CODES = {
    0: ("Clear sky", "sunny"), 1: ("Mainly clear", "sunny"), 2: ("Partly cloudy", "partly-sunny"),
    3: ("Overcast", "cloudy"), 45: ("Fog", "cloudy"), 48: ("Rime fog", "cloudy"),
    51: ("Light drizzle", "rainy"), 53: ("Moderate drizzle", "rainy"), 55: ("Dense drizzle", "rainy"),
    61: ("Slight rain", "rainy"), 63: ("Moderate rain", "rainy"), 65: ("Heavy rain", "rainy"),
    71: ("Slight snow", "snow"), 73: ("Moderate snow", "snow"), 75: ("Heavy snow", "snow"),
    80: ("Slight showers", "rainy"), 81: ("Moderate showers", "rainy"), 82: ("Violent showers", "thunderstorm"),
    95: ("Thunderstorm", "thunderstorm"), 96: ("Thunderstorm with hail", "thunderstorm"),
}

@api_router.get("/weather")
async def get_weather(lat: float = 28.6139, lon: float = 77.2090):
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.get(
                "https://api.open-meteo.com/v1/forecast",
                params={
                    "latitude": lat, "longitude": lon,
                    "current": "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature",
                    "hourly": "temperature_2m,weather_code",
                    "daily": "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset",
                    "timezone": "auto", "forecast_days": 5,
                }
            )
            data = r.json()
        current = data.get("current", {})
        wcode = current.get("weather_code", 0)
        desc, icon = WEATHER_CODES.get(wcode, ("Unknown", "cloudy"))
        daily = data.get("daily", {})
        forecast = []
        for i in range(min(5, len(daily.get("time", [])))):
            dc = daily.get("weather_code", [0])[i] if i < len(daily.get("weather_code", [])) else 0
            dd, di = WEATHER_CODES.get(dc, ("Unknown", "cloudy"))
            forecast.append({
                "date": daily["time"][i] if i < len(daily.get("time", [])) else "",
                "max": daily.get("temperature_2m_max", [0])[i] if i < len(daily.get("temperature_2m_max", [])) else 0,
                "min": daily.get("temperature_2m_min", [0])[i] if i < len(daily.get("temperature_2m_min", [])) else 0,
                "description": dd, "icon": di,
            })
        return {
            "temperature": current.get("temperature_2m", 0),
            "feels_like": current.get("apparent_temperature", 0),
            "humidity": current.get("relative_humidity_2m", 0),
            "wind_speed": current.get("wind_speed_10m", 0),
            "description": desc, "icon": icon, "weather_code": wcode,
            "forecast": forecast,
        }
    except Exception as e:
        logger.error(f"Weather error: {e}")
        return {"error": str(e)}


@api_router.get("/weather/search")
async def search_city(q: str):
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.get("https://geocoding-api.open-meteo.com/v1/search", params={"name": q, "count": 5})
            data = r.json()
        results = []
        for loc in data.get("results", []):
            results.append({
                "name": loc.get("name", ""), "country": loc.get("country", ""),
                "lat": loc.get("latitude", 0), "lon": loc.get("longitude", 0),
                "admin": loc.get("admin1", ""),
            })
        return {"results": results}
    except Exception as e:
        return {"error": str(e), "results": []}


# === NEWS (RSS Feeds - Free, no API key) ===
@api_router.get("/news")
async def get_news(category: str = "top"):
    feeds = {
        "top": "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml",
        "tech": "https://feeds.feedburner.com/TechCrunch/",
        "science": "https://rss.nytimes.com/services/xml/rss/nyt/Science.xml",
        "business": "https://rss.nytimes.com/services/xml/rss/nyt/Business.xml",
        "world": "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
    }
    feed_url = feeds.get(category, feeds["top"])
    try:
        import feedparser
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.get(feed_url)
        feed = feedparser.parse(r.text)
        articles = []
        for entry in feed.entries[:15]:
            articles.append({
                "title": entry.get("title", ""),
                "summary": entry.get("summary", "")[:200],
                "link": entry.get("link", ""),
                "published": entry.get("published", ""),
                "source": feed.feed.get("title", "News"),
            })
        return {"articles": articles, "category": category}
    except Exception as e:
        logger.error(f"News error: {e}")
        return {"articles": [], "error": str(e)}


# === CALENDAR (Local Calendar - MongoDB based) ===
class CalendarEventCreate(BaseModel):
    title: str
    description: str = ""
    start_time: str
    end_time: str = ""
    location: str = ""
    color: str = "#FFB800"

@api_router.get("/calendar/events")
async def list_calendar_events():
    events = await db.calendar_events.find({}, {"_id": 0}).sort("start_time", 1).to_list(200)
    return {"events": events}

@api_router.post("/calendar/events")
async def create_calendar_event(event: CalendarEventCreate):
    doc = {
        "id": str(uuid.uuid4()), "title": event.title,
        "description": event.description, "start_time": event.start_time,
        "end_time": event.end_time, "location": event.location,
        "color": event.color, "created_at": now_iso(),
    }
    await db.calendar_events.insert_one(doc)
    return {"id": doc["id"], "message": "Event created"}

@api_router.delete("/calendar/events/{event_id}")
async def delete_calendar_event(event_id: str):
    await db.calendar_events.delete_one({"id": event_id})
    return {"deleted": True}

@api_router.get("/calendar/status")
async def calendar_status():
    count = await db.calendar_events.count_documents({})
    return {
        "local_events": count,
        "google_connected": False,
        "google_instructions": "To connect Google Calendar: 1) Set up Google Cloud project with Calendar API, 2) Configure OAuth credentials, 3) Connect via Settings. Full Google Calendar sync requires OAuth setup."
    }


# === SMART HOME (Mock Simulation) ===
MOCK_DEVICES = [
    {"id": "light-1", "name": "Living Room Light", "type": "light", "room": "Living Room", "status": "off", "brightness": 80, "color": "#FFB800"},
    {"id": "light-2", "name": "Bedroom Light", "type": "light", "room": "Bedroom", "status": "off", "brightness": 60, "color": "#FFFFFF"},
    {"id": "thermostat-1", "name": "Smart Thermostat", "type": "thermostat", "room": "Living Room", "status": "on", "temperature": 22, "mode": "auto"},
    {"id": "speaker-1", "name": "Smart Speaker", "type": "speaker", "room": "Living Room", "status": "off", "volume": 50, "playing": ""},
    {"id": "lock-1", "name": "Front Door Lock", "type": "lock", "room": "Entrance", "status": "locked"},
    {"id": "camera-1", "name": "Security Camera", "type": "camera", "room": "Entrance", "status": "on", "recording": True},
    {"id": "plug-1", "name": "Smart Plug", "type": "plug", "room": "Kitchen", "status": "off"},
    {"id": "blinds-1", "name": "Window Blinds", "type": "blinds", "room": "Bedroom", "status": "open", "position": 100},
]

# Store device states in memory (resets on restart)
device_states = {d["id"]: dict(d) for d in MOCK_DEVICES}

@api_router.get("/smart-home/devices")
async def list_smart_devices():
    return {"devices": list(device_states.values()), "is_simulation": True}

class DeviceControl(BaseModel):
    device_id: str
    action: str
    value: Optional[str] = None

@api_router.post("/smart-home/control")
async def control_device(ctrl: DeviceControl):
    dev = device_states.get(ctrl.device_id)
    if not dev:
        return {"error": "Device not found"}
    if ctrl.action == "toggle":
        if dev["type"] == "lock":
            dev["status"] = "unlocked" if dev["status"] == "locked" else "locked"
        elif dev["type"] == "blinds":
            dev["status"] = "closed" if dev["status"] == "open" else "open"
        else:
            dev["status"] = "off" if dev["status"] == "on" else "on"
    elif ctrl.action == "set_brightness" and ctrl.value:
        dev["brightness"] = int(ctrl.value)
    elif ctrl.action == "set_temperature" and ctrl.value:
        dev["temperature"] = int(ctrl.value)
    elif ctrl.action == "set_volume" and ctrl.value:
        dev["volume"] = int(ctrl.value)
    elif ctrl.action == "set_color" and ctrl.value:
        dev["color"] = ctrl.value
    device_states[ctrl.device_id] = dev
    return {"device": dev, "message": f"Updated {dev['name']}"}

class SceneActivation(BaseModel):
    scene: str

@api_router.post("/smart-home/scene")
async def activate_scene(req: SceneActivation):
    scenes = {
        "movie": [("light-1", "on", "30"), ("light-2", "off", None), ("blinds-1", "closed", None)],
        "morning": [("light-1", "on", "100"), ("light-2", "on", "80"), ("blinds-1", "open", None)],
        "night": [("light-1", "off", None), ("light-2", "off", None), ("lock-1", "locked", None)],
        "focus": [("light-1", "on", "60"), ("speaker-1", "off", None)],
    }
    actions = scenes.get(req.scene, [])
    for dev_id, status, brightness in actions:
        if dev_id in device_states:
            if status in ("on", "off", "locked", "unlocked", "open", "closed"):
                device_states[dev_id]["status"] = status
            if brightness and "brightness" in device_states[dev_id]:
                device_states[dev_id]["brightness"] = int(brightness)
    return {"scene": req.scene, "applied": True, "devices_affected": len(actions)}


# === SKILLS MARKETPLACE ===
@api_router.get("/skills/marketplace")
async def get_marketplace():
    installed = await db.installed_skills.find({}, {"_id": 0}).to_list(100)
    installed_ids = {s["skill_id"] for s in installed}
    marketplace = [
        {"id": "weather", "name": "Weather Pro", "description": "Real-time weather forecasts with 5-day outlook", "category": "Utility", "icon": "cloudy", "rating": 4.8, "downloads": "12.5K", "installed": "weather" in installed_ids, "free": True},
        {"id": "news", "name": "News Feed", "description": "Live headlines from top global sources", "category": "Information", "icon": "newspaper", "rating": 4.6, "downloads": "8.3K", "installed": "news" in installed_ids, "free": True},
        {"id": "smart_home", "name": "Smart Home Hub", "description": "Control lights, thermostat, locks and more", "category": "IoT", "icon": "home", "rating": 4.7, "downloads": "15.1K", "installed": "smart_home" in installed_ids, "free": True},
        {"id": "calendar", "name": "Calendar Sync", "description": "Manage events and sync with Google Calendar", "category": "Productivity", "icon": "calendar", "rating": 4.5, "downloads": "9.7K", "installed": "calendar" in installed_ids, "free": True},
        {"id": "fitness", "name": "Fitness Tracker", "description": "Track workouts, steps, and health goals", "category": "Health", "icon": "fitness", "rating": 4.4, "downloads": "6.2K", "installed": False, "free": True},
        {"id": "recipes", "name": "Recipe Chef", "description": "AI-powered recipe suggestions from ingredients", "category": "Lifestyle", "icon": "restaurant", "rating": 4.7, "downloads": "11.8K", "installed": False, "free": True},
        {"id": "translate", "name": "Universal Translator", "description": "Real-time translation across 50+ languages", "category": "Utility", "icon": "language", "rating": 4.9, "downloads": "20.3K", "installed": False, "free": False, "price": "$2.99"},
        {"id": "meditation", "name": "Mindful Moments", "description": "Guided meditation and breathing exercises", "category": "Health", "icon": "leaf", "rating": 4.8, "downloads": "7.1K", "installed": False, "free": True},
        {"id": "stocks", "name": "Stock Watcher", "description": "Real-time stock tracking and portfolio insights", "category": "Finance", "icon": "trending-up", "rating": 4.3, "downloads": "5.8K", "installed": False, "free": False, "price": "$4.99"},
        {"id": "travel", "name": "Travel Planner", "description": "AI trip planning with flights, hotels, itinerary", "category": "Lifestyle", "icon": "airplane", "rating": 4.6, "downloads": "8.9K", "installed": False, "free": True},
    ]
    categories = list(set(s["category"] for s in marketplace))
    return {"skills": marketplace, "categories": sorted(categories)}

@api_router.post("/skills/install/{skill_id}")
async def install_skill(skill_id: str):
    await db.installed_skills.update_one(
        {"skill_id": skill_id},
        {"$set": {"skill_id": skill_id, "installed_at": now_iso()}},
        upsert=True
    )
    return {"installed": True, "skill_id": skill_id}

@api_router.delete("/skills/install/{skill_id}")
async def uninstall_skill(skill_id: str):
    await db.installed_skills.delete_one({"skill_id": skill_id})
    return {"uninstalled": True, "skill_id": skill_id}


# ╔══════════════════════════════════════════════════════════════╗
# ║         LAYER 2: MEMORY OS — 6 Intelligence Types           ║
# ╚══════════════════════════════════════════════════════════════╝

MEMORY_TYPES = ["identity", "episodic", "semantic", "behavioral", "emotional", "relationship"]

class AdvancedMemoryCreate(BaseModel):
    mem_type: str = "semantic"
    category: str = "general"
    key: str
    value: str
    confidence: float = 0.8
    why: str = ""

@api_router.get("/memory/os")
async def get_memory_os():
    """Get all memories organized by type with controls."""
    all_mems = await db.user_memory.find({}, {"_id": 0}).sort("updated_at", -1).to_list(500)
    grouped = {t: [] for t in MEMORY_TYPES}
    grouped["general"] = []
    for m in all_mems:
        mt = m.get("mem_type", m.get("category", "general"))
        if mt in grouped:
            grouped[mt].append(m)
        elif m.get("category") in grouped:
            grouped[m["category"]].append(m)
        else:
            grouped["general"].append(m)

    # Migrate old memories
    for m in all_mems:
        if "mem_type" not in m:
            mt = "semantic"
            cat = m.get("category", "general")
            if cat in ("personal",):
                mt = "identity"
            elif cat in ("preference", "habit"):
                mt = "behavioral"
            await db.user_memory.update_one({"id": m["id"]}, {"$set": {"mem_type": mt}})

    stats = {t: len(grouped.get(t, [])) for t in MEMORY_TYPES}
    stats["total"] = len(all_mems)
    return {"memories": grouped, "stats": stats, "types": MEMORY_TYPES}

@api_router.post("/memory/advanced")
async def add_advanced_memory(mem: AdvancedMemoryCreate):
    doc = {
        "id": str(uuid.uuid4()), "mem_type": mem.mem_type,
        "category": mem.category, "key": mem.key, "value": mem.value,
        "confidence": mem.confidence, "why": mem.why or f"Manually added by user",
        "source": "manual", "use_count": 0, "last_used": now_iso(),
        "created_at": now_iso(), "updated_at": now_iso(),
    }
    await db.user_memory.insert_one(doc)
    return {"id": doc["id"], "message": "Memory stored in " + mem.mem_type}

@api_router.get("/memory/explain/{memory_id}")
async def explain_memory(memory_id: str):
    """Why does NUA remember this?"""
    mem = await db.user_memory.find_one({"id": memory_id}, {"_id": 0})
    if not mem:
        return {"error": "Memory not found"}
    return {
        "memory": mem,
        "explanation": {
            "why": mem.get("why", "Extracted from conversation"),
            "source": mem.get("source", "conversation"),
            "confidence": mem.get("confidence", 0.8),
            "first_learned": mem.get("created_at", "unknown"),
            "last_used": mem.get("last_used", "never"),
            "times_used": mem.get("use_count", 0),
        }
    }

@api_router.post("/memory/forget/{memory_id}")
async def forget_memory(memory_id: str):
    """Explicitly forget a memory with trust logging."""
    mem = await db.user_memory.find_one({"id": memory_id}, {"_id": 0})
    if mem:
        await db.user_memory.delete_one({"id": memory_id})
        # Log to trust ledger
        await db.trust_ledger.insert_one({
            "id": str(uuid.uuid4()), "event_type": "correction",
            "description": f"User asked to forget: {mem.get('key', 'unknown')}",
            "score_change": -1, "created_at": now_iso()
        })
    return {"forgotten": True, "memory_id": memory_id}


# ╔══════════════════════════════════════════════════════════════╗
# ║         LAYER 3: NUA TRUST ENGINE                           ║
# ╚══════════════════════════════════════════════════════════════╝

async def calculate_trust_score() -> dict:
    """Calculate NUA's trust score from the ledger."""
    events = await db.trust_ledger.find({}, {"_id": 0}).to_list(1000)
    if not events:
        return {"score": 0, "level": "New", "total_events": 0, "breakdown": {}}

    breakdown = {"success": 0, "failure": 0, "correction": 0, "rejection": 0, "confirmation": 0, "prediction": 0}
    total_change = 0
    for e in events:
        et = e.get("event_type", "")
        if et in breakdown:
            breakdown[et] += 1
        total_change += e.get("score_change", 0)

    # Score: base 0, +3 for success, -5 for failure, -2 for correction, +1 for confirmation, +2 for prediction
    raw = (breakdown["success"] * 3 + breakdown["confirmation"] * 1 +
           breakdown["prediction"] * 2 - breakdown["failure"] * 5 -
           breakdown["correction"] * 2 - breakdown["rejection"] * 1)
    max_possible = max(len(events) * 3, 1)
    score = max(0, min(100, int((raw / max_possible) * 100 + 50)))

    levels = [(90, "Autonomous"), (75, "Highly Trusted"), (60, "Trusted"),
              (40, "Building Trust"), (20, "Learning"), (0, "New")]
    level = next((l for s, l in levels if score >= s), "New")

    return {
        "score": score, "level": level, "total_events": len(events),
        "breakdown": breakdown, "raw_score": raw,
        "autonomy_suggestions": await get_autonomy_suggestions(breakdown),
    }

async def get_autonomy_suggestions(breakdown: dict) -> list:
    """Suggest actions NUA could do automatically based on trust."""
    suggestions = []
    if breakdown.get("success", 0) >= 5:
        suggestions.append("I've successfully handled several tasks. Consider letting me auto-execute low-risk actions.")
    if breakdown.get("confirmation", 0) >= 10:
        suggestions.append("You've confirmed many of my suggestions. I could start acting on similar ones automatically.")
    return suggestions

async def record_trust_event(event_type: str, description: str, score_change: float = 0):
    """Record a trust event."""
    auto_scores = {"success": 3, "failure": -5, "correction": -2, "rejection": -1, "confirmation": 1, "prediction": 2}
    if score_change == 0:
        score_change = auto_scores.get(event_type, 0)
    await db.trust_ledger.insert_one({
        "id": str(uuid.uuid4()), "event_type": event_type,
        "description": description, "score_change": score_change,
        "created_at": now_iso()
    })

@api_router.get("/trust")
async def get_trust():
    trust = await calculate_trust_score()
    recent = await db.trust_ledger.find({}, {"_id": 0}).sort("created_at", -1).to_list(20)
    return {**trust, "recent_events": recent}

@api_router.get("/trust/ledger")
async def get_trust_ledger():
    events = await db.trust_ledger.find({}, {"_id": 0}).sort("created_at", -1).to_list(100)
    return {"events": events}


# ╔══════════════════════════════════════════════════════════════╗
# ║         LAYER 4: AUTONOMOUS ACTION ENGINE (T0-T5)           ║
# ╚══════════════════════════════════════════════════════════════╝

ACTION_TIERS = {
    "T0": {"name": "Read-only", "description": "Information retrieval only", "auto": True},
    "T1": {"name": "Auto-execute", "description": "Safe, low-risk actions", "auto": True},
    "T2": {"name": "Notify + execute", "description": "Execute and inform user", "auto": True},
    "T3": {"name": "Confirm first", "description": "Ask before executing", "auto": False},
    "T4": {"name": "Multi-step approval", "description": "Plan review required", "auto": False},
    "T5": {"name": "Never autonomous", "description": "Always requires explicit approval", "auto": False},
}

@api_router.get("/actions/tiers")
async def get_action_tiers():
    return {"tiers": ACTION_TIERS}

@api_router.get("/actions/log")
async def get_action_log():
    actions = await db.action_log.find({}, {"_id": 0}).sort("created_at", -1).to_list(50)
    return {"actions": actions}

async def log_action(action: str, tier: str, intent: str, result: str, risk: str = "low"):
    doc = {
        "id": str(uuid.uuid4()), "action": action, "tier": tier,
        "intent": intent, "result": result, "risk_level": risk,
        "verified": result == "success", "created_at": now_iso()
    }
    await db.action_log.insert_one(doc)
    if result == "success":
        await record_trust_event("success", f"Action completed: {action}")
    else:
        await record_trust_event("failure", f"Action failed: {action}")
    return doc


# ╔══════════════════════════════════════════════════════════════╗
# ║         LAYER 5: NUA GOALS                                  ║
# ╚══════════════════════════════════════════════════════════════╝

class GoalCreate(BaseModel):
    title: str
    description: str = ""

class SubTaskCreate(BaseModel):
    title: str

@api_router.get("/goals")
async def list_goals():
    goals = await db.goals.find({}, {"_id": 0}).sort("created_at", -1).to_list(50)
    return {"goals": goals}

@api_router.post("/goals")
async def create_goal(goal: GoalCreate):
    doc = {
        "id": str(uuid.uuid4()), "title": goal.title,
        "description": goal.description, "status": "active",
        "progress": 0, "sub_tasks": [], "insights": [],
        "created_at": now_iso(), "updated_at": now_iso(),
    }
    await db.goals.insert_one(doc)
    await log_action(f"Created goal: {goal.title}", "T1", "goal_creation", "success")
    return {"id": doc["id"], "message": "Goal created"}

@api_router.post("/goals/{goal_id}/subtask")
async def add_subtask(goal_id: str, task: SubTaskCreate):
    subtask = {"id": str(uuid.uuid4()), "title": task.title, "completed": False}
    await db.goals.update_one({"id": goal_id}, {"$push": {"sub_tasks": subtask}, "$set": {"updated_at": now_iso()}})
    return {"subtask": subtask}

@api_router.patch("/goals/{goal_id}/subtask/{subtask_id}")
async def toggle_subtask(goal_id: str, subtask_id: str):
    goal = await db.goals.find_one({"id": goal_id}, {"_id": 0})
    if not goal:
        return {"error": "Goal not found"}
    for st in goal.get("sub_tasks", []):
        if st["id"] == subtask_id:
            st["completed"] = not st["completed"]
    completed = sum(1 for s in goal.get("sub_tasks", []) if s.get("completed"))
    total = len(goal.get("sub_tasks", []))
    progress = int((completed / max(total, 1)) * 100)
    status = "completed" if progress == 100 and total > 0 else "active"
    await db.goals.update_one({"id": goal_id}, {"$set": {
        "sub_tasks": goal["sub_tasks"], "progress": progress,
        "status": status, "updated_at": now_iso()
    }})
    if status == "completed":
        await record_trust_event("success", f"Goal completed: {goal.get('title', '')}")
    return {"progress": progress, "status": status}

@api_router.delete("/goals/{goal_id}")
async def delete_goal(goal_id: str):
    await db.goals.delete_one({"id": goal_id})
    return {"deleted": True}

@api_router.post("/goals/{goal_id}/analyze")
async def analyze_goal(goal_id: str):
    """NUA analyzes goal progress and provides insights."""
    from emergentintegrations.llm.chat import LlmChat, UserMessage
    goal = await db.goals.find_one({"id": goal_id}, {"_id": 0})
    if not goal:
        return {"error": "Goal not found"}

    memories = await get_user_memories()
    api_key = os.environ.get('EMERGENT_LLM_KEY')
    session_id = f"goal-analysis-{uuid.uuid4().hex[:8]}"
    chat = LlmChat(api_key=api_key, session_id=session_id,
        system_message="You are Nua, analyzing a user's goal. Provide 2-3 actionable insights. Be concise and encouraging.")
    chat.with_model("openai", "gpt-5.2")

    context = f"Goal: {goal['title']}\nDescription: {goal.get('description','')}\nProgress: {goal['progress']}%\n"
    context += f"Sub-tasks: {json.dumps(goal.get('sub_tasks', []))}\n"
    if memories:
        context += f"User context: {json.dumps([{'key':m['key'],'value':m['value']} for m in memories[:10]])}"

    resp = await chat.send_message(UserMessage(text=f"Analyze this goal and suggest next steps:\n{context}"))
    insight = {"text": resp, "generated_at": now_iso()}
    await db.goals.update_one({"id": goal_id}, {"$push": {"insights": insight}})
    return {"insight": resp, "goal_id": goal_id}


# ╔══════════════════════════════════════════════════════════════╗
# ║         LAYER 6: NUA DREAMS 2.0                             ║
# ╚══════════════════════════════════════════════════════════════╝

DREAM_CATEGORIES = ["opportunity", "pattern", "reminder", "concern", "optimization",
                    "relationship", "finance", "productivity", "learning", "business"]

@api_router.get("/dreams")
async def list_dreams():
    dreams = await db.dreams.find({}, {"_id": 0}).sort("created_at", -1).to_list(50)
    return {"dreams": dreams, "categories": DREAM_CATEGORIES}

@api_router.post("/dreams/generate")
async def generate_dream():
    """Generate a NUA Dream — cross-referenced insight from all data."""
    from emergentintegrations.llm.chat import LlmChat, UserMessage
    api_key = os.environ.get('EMERGENT_LLM_KEY')

    # Gather all context
    memories = await db.user_memory.find({}, {"_id": 0}).to_list(100)
    convs = await db.conversations.find({}, {"_id": 0}).sort("updated_at", -1).to_list(10)
    goals = await db.goals.find({"status": "active"}, {"_id": 0}).to_list(10)
    expenses = await db.expenses.find({}, {"_id": 0}).sort("created_at", -1).to_list(20)
    reminders = await db.reminders.find({}, {"_id": 0}).to_list(20)
    trust = await calculate_trust_score()
    recent_msgs = await db.messages.find({}, {"_id": 0}).sort("created_at", -1).to_list(30)

    context = "=== USER MEMORIES ===\n"
    for m in memories:
        context += f"[{m.get('mem_type','general')}] {m.get('key','')}: {m.get('value','')}\n"
    context += "\n=== RECENT CONVERSATIONS (titles) ===\n"
    for c in convs:
        context += f"- {c.get('title','')} ({c.get('message_count',0)} msgs)\n"
    context += "\n=== ACTIVE GOALS ===\n"
    for g in goals:
        context += f"- {g.get('title','')} ({g.get('progress',0)}% done)\n"
    context += "\n=== RECENT EXPENSES ===\n"
    for e in expenses[:10]:
        context += f"- ${e.get('amount',0)} on {e.get('category','')} ({e.get('date','')})\n"
    context += f"\n=== TRUST SCORE: {trust['score']}% ({trust['level']}) ===\n"
    context += "\n=== RECENT MESSAGES ===\n"
    for msg in recent_msgs[:15]:
        context += f"[{msg.get('role','')}]: {msg.get('content','')[:100]}\n"

    dream_prompt = f"""Based on ALL the following data about the user, generate ONE non-obvious, cross-referenced insight.
This should NOT be a summary. It should be a genuine discovery — something the user hasn't thought of.

Pick ONE category: {', '.join(DREAM_CATEGORIES)}

Format your response as:
CATEGORY: [category]
TITLE: [short punchy title]
INSIGHT: [2-3 sentences of the actual insight]
IMPACT: [low/medium/high]

{context}

Generate a genuinely useful, non-obvious insight. Think like a brilliant advisor who notices patterns the user missed."""

    session_id = f"dream-{uuid.uuid4().hex[:8]}"
    chat = LlmChat(api_key=api_key, session_id=session_id,
        system_message="You are Nua's Dream Engine. You cross-reference all user data to find non-obvious insights.")
    chat.with_model("openai", "gpt-5.2")
    resp = await chat.send_message(UserMessage(text=dream_prompt))

    # Parse response
    category = "pattern"
    title = "New Insight"
    content = resp
    impact = "medium"
    for line in resp.split("\n"):
        if line.startswith("CATEGORY:"):
            cat = line.replace("CATEGORY:", "").strip().lower()
            if cat in DREAM_CATEGORIES:
                category = cat
        elif line.startswith("TITLE:"):
            title = line.replace("TITLE:", "").strip()
        elif line.startswith("INSIGHT:"):
            content = line.replace("INSIGHT:", "").strip()
        elif line.startswith("IMPACT:"):
            impact = line.replace("IMPACT:", "").strip().lower()

    dream = {
        "id": str(uuid.uuid4()), "category": category, "title": title,
        "content": content, "impact": impact, "acted_on": False,
        "source_data": f"{len(memories)} memories, {len(convs)} conversations, {len(goals)} goals",
        "created_at": now_iso(),
    }
    await db.dreams.insert_one(dream)
    return {"dream": {k: v for k, v in dream.items() if k != "_id"}}

@api_router.patch("/dreams/{dream_id}/act")
async def act_on_dream(dream_id: str):
    await db.dreams.update_one({"id": dream_id}, {"$set": {"acted_on": True}})
    return {"acted_on": True}


# ╔══════════════════════════════════════════════════════════════╗
# ║  ENHANCED: NUA CORE — Memory-aware chat with trust tracking ║
# ╚══════════════════════════════════════════════════════════════╝

# Override the build_nua_system_prompt to use full Memory OS + Trust + Goals
_original_build = build_nua_system_prompt

def build_nua_system_prompt_v2(memories: list = None, reminders: list = None):
    """Enhanced system prompt with all 7 intelligence layers."""
    base = _original_build(memories, reminders)

    # Add trust context
    trust_addition = "\n\nTRUST & AUTONOMY:\n"
    trust_addition += "Track your performance. When you successfully help, note it. When user corrects you, learn.\n"
    trust_addition += "For action extraction, classify risk: low-risk (T1), medium (T3), high (T4).\n"

    # Add memory classification guidance
    memory_addition = "\n\nMEMORY CLASSIFICATION:\n"
    memory_addition += "When storing memories, use these action types:\n"
    memory_addition += '```action{"type":"memory","mem_type":"identity","category":"name","key":"...","value":"...","why":"...","confidence":0.9}```\n'
    memory_addition += "Types: identity (name/people/dates), episodic (events/decisions), semantic (facts/knowledge), "
    memory_addition += "behavioral (habits/routines), emotional (feelings/reactions), relationship (connections between people)\n"

    # Add goal awareness
    goal_addition = "\n\nGOAL AWARENESS:\n"
    goal_addition += "If you detect the user is working toward a goal, mention relevant progress or suggest next steps.\n"
    goal_addition += "For new goals: ```action{\"type\":\"goal\",\"title\":\"...\",\"description\":\"...\"}```\n"

    return base + trust_addition + memory_addition + goal_addition

# Monkey-patch the system prompt builder
build_nua_system_prompt = build_nua_system_prompt_v2


# Enhanced action processing to handle goals and trust
_original_process = process_actions

async def enhanced_process_actions(response_text: str):
    """Process actions with goal support and trust tracking."""
    executed = await _original_process(response_text)

    # Also check for goal actions
    action_pattern = r'```action\s*(\{.*?\})\s*```'
    actions = re.findall(action_pattern, response_text, re.DOTALL)
    for action_str in actions:
        try:
            action = json.loads(action_str)
            if action.get("type") == "goal":
                doc = {
                    "id": str(uuid.uuid4()), "title": action.get("title", ""),
                    "description": action.get("description", ""), "status": "active",
                    "progress": 0, "sub_tasks": [], "insights": [],
                    "created_at": now_iso(), "updated_at": now_iso(),
                }
                await db.goals.insert_one(doc)
                executed.append({"type": "goal", "title": doc["title"]})
                await record_trust_event("success", f"Created goal: {doc['title']}")
        except (json.JSONDecodeError, ValueError):
            pass

    # Record successful action processing
    if executed:
        for action in executed:
            await log_action(
                f"{action.get('type','unknown')}: {action.get('title', action.get('key', '?'))}",
                "T1", "auto_extraction", "success"
            )

    return executed

process_actions = enhanced_process_actions


# Include router & middleware
app.include_router(api_router)
app.add_middleware(CORSMiddleware, allow_credentials=True, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()
