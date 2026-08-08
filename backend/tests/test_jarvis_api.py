"""
JARVIS AI Assistant API Tests
Tests for: health, chat, conversations CRUD, search, quick actions, TTS
"""
import pytest
import requests
import os
import time
from pathlib import Path
from dotenv import load_dotenv

# Load frontend .env to get the public URL
frontend_env = Path(__file__).parent.parent.parent / 'frontend' / '.env'
load_dotenv(frontend_env)

BASE_URL = os.environ.get('EXPO_PUBLIC_BACKEND_URL')
if not BASE_URL:
    raise ValueError("EXPO_PUBLIC_BACKEND_URL not found in environment")
BASE_URL = BASE_URL.rstrip('/')

class TestHealthEndpoint:
    """Health check endpoint tests"""

    def test_health_check(self):
        """Test GET /api/health returns operational status"""
        response = requests.get(f"{BASE_URL}/api/health")
        assert response.status_code == 200
        
        data = response.json()
        assert "status" in data
        assert data["status"] == "operational"
        assert "system" in data
        assert data["system"] == "J.A.R.V.I.S."
        print("✓ Health check passed")


class TestChatEndpoint:
    """Chat endpoint tests"""

    def test_chat_new_conversation(self):
        """Test POST /api/chat creates new conversation and returns response"""
        response = requests.post(
            f"{BASE_URL}/api/chat",
            json={"content": "Hello JARVIS"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "conversation_id" in data
        assert "message" in data
        assert data["message"]["role"] == "assistant"
        assert len(data["message"]["content"]) > 0
        
        # Store for next test
        pytest.conversation_id = data["conversation_id"]
        print(f"✓ New conversation created: {pytest.conversation_id}")
        print(f"  JARVIS response: {data['message']['content'][:100]}...")

    def test_chat_existing_conversation(self):
        """Test POST /api/chat with existing conversation_id maintains context"""
        if not hasattr(pytest, 'conversation_id'):
            pytest.skip("Requires conversation_id from previous test")
        
        # Wait a bit for AI processing
        time.sleep(1)
        
        response = requests.post(
            f"{BASE_URL}/api/chat",
            json={
                "content": "Tell me more",
                "conversation_id": pytest.conversation_id
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["conversation_id"] == pytest.conversation_id
        assert "message" in data
        assert data["message"]["role"] == "assistant"
        print(f"✓ Context maintained in conversation {pytest.conversation_id}")
        print(f"  JARVIS response: {data['message']['content'][:100]}...")


class TestConversationsEndpoint:
    """Conversations CRUD tests"""

    def test_list_conversations(self):
        """Test GET /api/conversations returns list"""
        response = requests.get(f"{BASE_URL}/api/conversations")
        assert response.status_code == 200
        
        data = response.json()
        assert "conversations" in data
        assert isinstance(data["conversations"], list)
        
        if len(data["conversations"]) > 0:
            conv = data["conversations"][0]
            assert "id" in conv
            assert "title" in conv
            assert "created_at" in conv
            assert "message_count" in conv
            print(f"✓ Found {len(data['conversations'])} conversations")
        else:
            print("✓ Conversations list is empty (expected for fresh DB)")

    def test_get_conversation_detail(self):
        """Test GET /api/conversations/<id> returns conversation with messages"""
        if not hasattr(pytest, 'conversation_id'):
            pytest.skip("Requires conversation_id from chat test")
        
        response = requests.get(f"{BASE_URL}/api/conversations/{pytest.conversation_id}")
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert data["id"] == pytest.conversation_id
        assert "messages" in data
        assert isinstance(data["messages"], list)
        assert len(data["messages"]) >= 2  # At least user + assistant
        
        # Verify message structure
        msg = data["messages"][0]
        assert "id" in msg
        assert "role" in msg
        assert "content" in msg
        assert "created_at" in msg
        print(f"✓ Conversation detail retrieved with {len(data['messages'])} messages")

    def test_search_conversations(self):
        """Test GET /api/conversations/search?q=hello finds matching conversations"""
        response = requests.get(f"{BASE_URL}/api/conversations/search?q=hello")
        assert response.status_code == 200
        
        data = response.json()
        assert "conversations" in data
        assert "query" in data
        assert data["query"] == "hello"
        print(f"✓ Search returned {len(data['conversations'])} results for 'hello'")

    def test_delete_conversation(self):
        """Test DELETE /api/conversations/<id> deletes conversation"""
        # Create a test conversation first
        response = requests.post(
            f"{BASE_URL}/api/chat",
            json={"content": "TEST_DELETE_ME"}
        )
        assert response.status_code == 200
        conv_id = response.json()["conversation_id"]
        
        # Delete it
        delete_response = requests.delete(f"{BASE_URL}/api/conversations/{conv_id}")
        assert delete_response.status_code == 200
        
        delete_data = delete_response.json()
        assert "deleted" in delete_data
        assert delete_data["deleted"] is True
        
        # Verify it's gone
        get_response = requests.get(f"{BASE_URL}/api/conversations/{conv_id}")
        get_data = get_response.json()
        assert "error" in get_data or get_data.get("messages") is None
        print(f"✓ Conversation {conv_id} deleted successfully")


class TestQuickActions:
    """Quick action endpoint tests"""

    def test_quick_action_joke(self):
        """Test POST /api/quick-action with action='joke' returns witty response"""
        response = requests.post(
            f"{BASE_URL}/api/quick-action",
            json={"action": "joke"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "action" in data
        assert data["action"] == "joke"
        assert "response" in data
        assert len(data["response"]) > 0
        print(f"✓ Joke action: {data['response'][:100]}...")

    def test_quick_action_fact(self):
        """Test POST /api/quick-action with action='fact' returns fun fact"""
        response = requests.post(
            f"{BASE_URL}/api/quick-action",
            json={"action": "fact"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "action" in data
        assert data["action"] == "fact"
        assert "response" in data
        assert len(data["response"]) > 0
        print(f"✓ Fact action: {data['response'][:100]}...")


class TestTTSEndpoint:
    """Text-to-Speech endpoint tests"""

    def test_tts_generation(self):
        """Test POST /api/tts returns audio_base64"""
        response = requests.post(
            f"{BASE_URL}/api/tts",
            json={"text": "Hello world", "voice": "onyx"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "audio_base64" in data
        assert "format" in data
        assert data["format"] == "mp3"
        assert len(data["audio_base64"]) > 0
        # Base64 encoded audio should be substantial
        assert len(data["audio_base64"]) > 100
        print(f"✓ TTS generated {len(data['audio_base64'])} bytes of base64 audio")
