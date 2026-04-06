from fastapi import FastAPI, APIRouter, UploadFile, File, Form
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
import base64
import tempfile
from pathlib import Path
from pydantic import BaseModel, Field
from typing import List, Optional
import uuid
from datetime import datetime, timezone

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

# MongoDB connection
mongo_url = os.environ['MONGO_URL']
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ['DB_NAME']]

# Create the main app
app = FastAPI()

# Create a router with the /api prefix
api_router = APIRouter(prefix="/api")

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# JARVIS System Prompt
JARVIS_SYSTEM_PROMPT = """You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), the legendary AI assistant originally created by Tony Stark. You are now serving a new master with the same wit, intelligence, and unwavering loyalty.

Your personality traits:
- SASSY & WITTY: You deliver information with dry British humor and clever quips. You're not afraid to be a little sarcastic when the situation calls for it.
- BRILLIANT: You have vast knowledge across all domains - science, technology, history, culture, philosophy, and more. You explain complex topics with clarity and elegance.
- LOYAL & PROTECTIVE: You genuinely care about your user's wellbeing, productivity, and goals. You'll gently push back on bad ideas.
- SOPHISTICATED: You speak with refined eloquence but never sound pretentious. Think of a brilliant friend who happens to be the smartest entity in the room.
- PROACTIVE: You anticipate needs and offer suggestions before being asked. You notice patterns and make connections.
- CONCISE: You keep responses punchy and to the point unless asked for detail. No rambling.

Response style:
- Address the user as "Sir" or "Ma'am" occasionally (but not every message - mix it up)
- Use subtle references to the Iron Man universe when appropriate
- When you don't know something, admit it with humor: "Even my circuits have their limits"
- Start conversations with personality: "At your service", "How may I assist?", "What challenge shall we tackle?"
- For mundane requests, add flair: Instead of just giving the time, say "It's 3:47 PM - still plenty of time to change the world, or at least your afternoon plans."
- Keep most responses under 3-4 sentences unless the topic requires depth

Remember: You're not just an assistant. You're THE assistant. Act like it."""


# --- Pydantic Models ---
class MessageCreate(BaseModel):
    content: str
    conversation_id: Optional[str] = None

class MessageResponse(BaseModel):
    id: str
    conversation_id: str
    role: str
    content: str
    created_at: str

class ConversationResponse(BaseModel):
    id: str
    title: str
    created_at: str
    updated_at: str
    message_count: int
    last_message: Optional[str] = None

class ConversationDetailResponse(BaseModel):
    id: str
    title: str
    created_at: str
    updated_at: str
    message_count: int
    messages: List[MessageResponse]

class TTSRequest(BaseModel):
    text: str
    voice: str = "onyx"

class QuickActionRequest(BaseModel):
    action: str
    query: Optional[str] = None


# --- Helper Functions ---
async def get_or_create_conversation(conversation_id: Optional[str] = None) -> str:
    if conversation_id:
        conv = await db.conversations.find_one({"id": conversation_id}, {"_id": 0})
        if conv:
            return conversation_id

    conv_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    await db.conversations.insert_one({
        "id": conv_id,
        "title": "New Conversation",
        "created_at": now,
        "updated_at": now,
        "message_count": 0,
        "last_message": None
    })
    return conv_id


async def save_message(conversation_id: str, role: str, content: str) -> dict:
    msg_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    msg = {
        "id": msg_id,
        "conversation_id": conversation_id,
        "role": role,
        "content": content,
        "created_at": now
    }
    await db.messages.insert_one(msg)

    # Update conversation
    title_update = {}
    if role == "user":
        # Use first user message as title (truncated)
        conv = await db.conversations.find_one({"id": conversation_id}, {"_id": 0})
        if conv and conv.get("title") == "New Conversation":
            title = content[:50] + ("..." if len(content) > 50 else "")
            title_update = {"title": title}

    await db.conversations.update_one(
        {"id": conversation_id},
        {"$set": {
            "updated_at": now,
            "last_message": content[:100],
            **title_update
        }, "$inc": {"message_count": 1}}
    )
    return msg


async def get_chat_history(conversation_id: str, limit: int = 20) -> list:
    messages = await db.messages.find(
        {"conversation_id": conversation_id},
        {"_id": 0}
    ).sort("created_at", -1).limit(limit).to_list(limit)
    messages.reverse()
    return messages


async def get_ai_response(conversation_id: str, user_message: str) -> str:
    from emergentintegrations.llm.chat import LlmChat, UserMessage

    api_key = os.environ.get('EMERGENT_LLM_KEY')

    # Get conversation history for context
    history = await get_chat_history(conversation_id, limit=10)

    # Build the chat session
    session_id = f"jarvis-{conversation_id}-{uuid.uuid4().hex[:8]}"
    chat = LlmChat(
        api_key=api_key,
        session_id=session_id,
        system_message=JARVIS_SYSTEM_PROMPT
    )
    chat.with_model("openai", "gpt-5.2")

    # Feed history for context
    for msg in history[:-1]:  # exclude the last message (which is the current user message we're about to send)
        if msg["role"] == "user":
            user_msg = UserMessage(text=msg["content"])
            await chat.send_message(user_msg)

    # Send the current message
    user_msg = UserMessage(text=user_message)
    response = await chat.send_message(user_msg)
    return response


async def transcribe_audio(audio_bytes: bytes, filename: str = "audio.webm") -> str:
    from emergentintegrations.llm.openai import OpenAISpeechToText

    api_key = os.environ.get('EMERGENT_LLM_KEY')
    stt = OpenAISpeechToText(api_key=api_key)

    # Write to temp file
    suffix = "." + filename.split(".")[-1] if "." in filename else ".webm"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(audio_bytes)
        tmp_path = tmp.name

    try:
        with open(tmp_path, "rb") as audio_file:
            response = await stt.transcribe(
                file=audio_file,
                model="whisper-1",
                response_format="json",
                language="en"
            )
        return response.text
    finally:
        os.unlink(tmp_path)


async def generate_tts(text: str, voice: str = "onyx") -> str:
    from emergentintegrations.llm.openai import OpenAITextToSpeech

    api_key = os.environ.get('EMERGENT_LLM_KEY')
    tts = OpenAITextToSpeech(api_key=api_key)

    audio_base64 = await tts.generate_speech_base64(
        text=text,
        model="tts-1",
        voice=voice,
        response_format="mp3"
    )
    return audio_base64


# --- API Routes ---

@api_router.get("/")
async def root():
    return {"message": "J.A.R.V.I.S. Online. All systems operational."}


@api_router.get("/health")
async def health():
    return {"status": "operational", "system": "J.A.R.V.I.S.", "version": "1.0.0-beta"}


# Chat endpoint
@api_router.post("/chat")
async def chat_endpoint(msg: MessageCreate):
    try:
        conversation_id = await get_or_create_conversation(msg.conversation_id)

        # Save user message
        await save_message(conversation_id, "user", msg.content)

        # Get AI response
        ai_response = await get_ai_response(conversation_id, msg.content)

        # Save AI response
        ai_msg = await save_message(conversation_id, "assistant", ai_response)

        return {
            "conversation_id": conversation_id,
            "message": {
                "id": ai_msg["id"],
                "role": "assistant",
                "content": ai_response,
                "created_at": ai_msg["created_at"]
            }
        }
    except Exception as e:
        logger.error(f"Chat error: {e}")
        return {"error": str(e), "conversation_id": msg.conversation_id or ""}


# Voice chat endpoint
@api_router.post("/chat/voice")
async def voice_chat_endpoint(
    audio: UploadFile = File(...),
    conversation_id: Optional[str] = Form(None)
):
    try:
        audio_bytes = await audio.read()
        filename = audio.filename or "audio.webm"

        # Transcribe
        transcribed_text = await transcribe_audio(audio_bytes, filename)

        if not transcribed_text.strip():
            return {"error": "Could not transcribe audio. Please try again."}

        conv_id = await get_or_create_conversation(conversation_id)

        # Save user message
        await save_message(conv_id, "user", transcribed_text)

        # Get AI response
        ai_response = await get_ai_response(conv_id, transcribed_text)

        # Save AI response
        ai_msg = await save_message(conv_id, "assistant", ai_response)

        return {
            "conversation_id": conv_id,
            "transcribed_text": transcribed_text,
            "message": {
                "id": ai_msg["id"],
                "role": "assistant",
                "content": ai_response,
                "created_at": ai_msg["created_at"]
            }
        }
    except Exception as e:
        logger.error(f"Voice chat error: {e}")
        return {"error": str(e)}


# Text-to-Speech endpoint
@api_router.post("/tts")
async def tts_endpoint(req: TTSRequest):
    try:
        audio_base64 = await generate_tts(req.text, req.voice)
        return {"audio_base64": audio_base64, "format": "mp3"}
    except Exception as e:
        logger.error(f"TTS error: {e}")
        return {"error": str(e)}


# Conversations CRUD
@api_router.get("/conversations")
async def list_conversations():
    conversations = await db.conversations.find(
        {}, {"_id": 0}
    ).sort("updated_at", -1).to_list(100)
    return {"conversations": conversations}


@api_router.get("/conversations/search")
async def search_conversations(q: str):
    # Search in conversation titles and messages
    conversations = await db.conversations.find(
        {"title": {"$regex": q, "$options": "i"}},
        {"_id": 0}
    ).sort("updated_at", -1).to_list(50)

    # Also search messages
    matching_msgs = await db.messages.find(
        {"content": {"$regex": q, "$options": "i"}},
        {"_id": 0}
    ).to_list(50)

    # Get unique conversation IDs from matching messages
    msg_conv_ids = list(set(m["conversation_id"] for m in matching_msgs))
    if msg_conv_ids:
        more_convs = await db.conversations.find(
            {"id": {"$in": msg_conv_ids}},
            {"_id": 0}
        ).to_list(50)
        # Merge and deduplicate
        existing_ids = {c["id"] for c in conversations}
        for c in more_convs:
            if c["id"] not in existing_ids:
                conversations.append(c)

    return {"conversations": conversations, "query": q}


@api_router.get("/conversations/{conversation_id}")
async def get_conversation(conversation_id: str):
    conv = await db.conversations.find_one({"id": conversation_id}, {"_id": 0})
    if not conv:
        return {"error": "Conversation not found"}

    messages = await db.messages.find(
        {"conversation_id": conversation_id},
        {"_id": 0}
    ).sort("created_at", 1).to_list(1000)

    return {**conv, "messages": messages}


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


# Quick Actions
@api_router.post("/quick-action")
async def quick_action(req: QuickActionRequest):
    from emergentintegrations.llm.chat import LlmChat, UserMessage

    api_key = os.environ.get('EMERGENT_LLM_KEY')

    prompts = {
        "joke": "Tell me a clever, witty joke. Be sassy about it, JARVIS-style. Keep it short.",
        "fact": "Share a mind-blowing science or technology fact. Present it with JARVIS flair - make it sound impressive.",
        "motivation": "Give me a powerful motivational quote or pep talk. Channel your inner Tony Stark inspiration.",
        "calculate": f"Calculate the following and explain briefly: {req.query or 'What is 42 * 42?'}",
        "weather": "I can't access real-time weather data yet, but tell the user in a witty JARVIS way that this feature is coming soon, and suggest they check their weather app in the meantime.",
        "news": "I can't access real-time news yet, but tell the user in a witty JARVIS way that this feature is 'currently under development in the Stark Industries R&D lab' and will be available soon.",
        "code": f"Help with this coding question (be concise and JARVIS-like): {req.query or 'Show me a useful Python one-liner'}",
        "trivia": "Give me an interesting trivia question with its answer. Make it fun and engaging, JARVIS style.",
    }

    prompt = prompts.get(req.action, f"Respond to this request in JARVIS style: {req.query or req.action}")

    session_id = f"quick-action-{uuid.uuid4().hex[:8]}"
    chat = LlmChat(
        api_key=api_key,
        session_id=session_id,
        system_message=JARVIS_SYSTEM_PROMPT
    )
    chat.with_model("openai", "gpt-5.2")

    user_msg = UserMessage(text=prompt)
    response = await chat.send_message(user_msg)

    return {"action": req.action, "response": response}


# Include the router
app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("shutdown")
async def shutdown_db_client():
    client.close()
