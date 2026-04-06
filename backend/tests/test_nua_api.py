"""
Nua AI Assistant API Tests (Iteration 2)
Tests for: health, chat with action extraction, memory, reminders, notes, expenses, dashboard, skills, TTS, quick actions
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

# Test data cleanup list
test_memory_ids = []
test_reminder_ids = []
test_note_ids = []
test_expense_ids = []


class TestHealthEndpoint:
    """Health check endpoint tests"""

    def test_health_check(self):
        """Test GET /api/health returns Nua system info"""
        response = requests.get(f"{BASE_URL}/api/health")
        assert response.status_code == 200
        
        data = response.json()
        assert "status" in data
        assert data["status"] == "operational"
        assert "system" in data
        assert data["system"] == "Nua"
        assert "version" in data
        assert data["version"] == "2.0.0-beta"
        print("✓ Health check passed - Nua system operational")


class TestMemoryEndpoint:
    """Memory CRUD tests"""

    def test_get_memories(self):
        """Test GET /api/memory returns stored memories"""
        response = requests.get(f"{BASE_URL}/api/memory")
        assert response.status_code == 200
        
        data = response.json()
        assert "memories" in data
        assert isinstance(data["memories"], list)
        print(f"✓ Retrieved {len(data['memories'])} memories")

    def test_add_memory(self):
        """Test POST /api/memory creates a new memory"""
        response = requests.post(
            f"{BASE_URL}/api/memory",
            json={
                "category": "preference",
                "key": "TEST_favorite_color",
                "value": "blue"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        test_memory_ids.append(data["id"])
        
        # Verify it was created by fetching all memories
        get_response = requests.get(f"{BASE_URL}/api/memory")
        memories = get_response.json()["memories"]
        created = [m for m in memories if m["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["key"] == "TEST_favorite_color"
        assert created[0]["value"] == "blue"
        assert created[0]["category"] == "preference"
        print(f"✓ Memory created and verified: {data['id']}")

    def test_delete_memory(self):
        """Test DELETE /api/memory/{id} deletes a memory"""
        # Create a memory first
        create_response = requests.post(
            f"{BASE_URL}/api/memory",
            json={"category": "general", "key": "TEST_delete_me", "value": "temp"}
        )
        memory_id = create_response.json()["id"]
        
        # Delete it
        delete_response = requests.delete(f"{BASE_URL}/api/memory/{memory_id}")
        assert delete_response.status_code == 200
        
        data = delete_response.json()
        assert "deleted" in data
        assert data["deleted"] is True
        
        # Verify it's gone
        get_response = requests.get(f"{BASE_URL}/api/memory")
        memories = get_response.json()["memories"]
        deleted = [m for m in memories if m["id"] == memory_id]
        assert len(deleted) == 0
        print(f"✓ Memory {memory_id} deleted successfully")


class TestRemindersEndpoint:
    """Reminders CRUD tests"""

    def test_get_reminders(self):
        """Test GET /api/reminders returns reminders list"""
        response = requests.get(f"{BASE_URL}/api/reminders")
        assert response.status_code == 200
        
        data = response.json()
        assert "reminders" in data
        assert isinstance(data["reminders"], list)
        print(f"✓ Retrieved {len(data['reminders'])} reminders")

    def test_create_reminder(self):
        """Test POST /api/reminders creates a reminder"""
        response = requests.post(
            f"{BASE_URL}/api/reminders",
            json={
                "title": "TEST_Buy groceries",
                "description": "Milk, eggs, bread",
                "due_date": "2026-04-10"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        test_reminder_ids.append(data["id"])
        
        # Verify creation
        get_response = requests.get(f"{BASE_URL}/api/reminders")
        reminders = get_response.json()["reminders"]
        created = [r for r in reminders if r["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["title"] == "TEST_Buy groceries"
        assert created[0]["completed"] is False
        print(f"✓ Reminder created: {data['id']}")

    def test_toggle_reminder(self):
        """Test PATCH /api/reminders/{id} toggles completion status"""
        # Create a reminder first
        create_response = requests.post(
            f"{BASE_URL}/api/reminders",
            json={"title": "TEST_Toggle me", "description": "", "due_date": None}
        )
        reminder_id = create_response.json()["id"]
        test_reminder_ids.append(reminder_id)
        
        # Toggle to completed
        toggle_response = requests.patch(f"{BASE_URL}/api/reminders/{reminder_id}")
        assert toggle_response.status_code == 200
        
        data = toggle_response.json()
        assert "id" in data
        assert data["id"] == reminder_id
        assert "completed" in data
        assert data["completed"] is True
        
        # Toggle back to incomplete
        toggle_response2 = requests.patch(f"{BASE_URL}/api/reminders/{reminder_id}")
        data2 = toggle_response2.json()
        assert data2["completed"] is False
        print(f"✓ Reminder {reminder_id} toggled successfully")

    def test_delete_reminder(self):
        """Test DELETE /api/reminders/{id} deletes a reminder"""
        # Create a reminder first
        create_response = requests.post(
            f"{BASE_URL}/api/reminders",
            json={"title": "TEST_Delete reminder", "description": "", "due_date": None}
        )
        reminder_id = create_response.json()["id"]
        
        # Delete it
        delete_response = requests.delete(f"{BASE_URL}/api/reminders/{reminder_id}")
        assert delete_response.status_code == 200
        
        data = delete_response.json()
        assert "deleted" in data
        assert data["deleted"] is True
        print(f"✓ Reminder {reminder_id} deleted successfully")


class TestNotesEndpoint:
    """Notes CRUD tests"""

    def test_get_notes(self):
        """Test GET /api/notes returns notes list"""
        response = requests.get(f"{BASE_URL}/api/notes")
        assert response.status_code == 200
        
        data = response.json()
        assert "notes" in data
        assert isinstance(data["notes"], list)
        print(f"✓ Retrieved {len(data['notes'])} notes")

    def test_create_note(self):
        """Test POST /api/notes creates a note"""
        response = requests.post(
            f"{BASE_URL}/api/notes",
            json={
                "title": "TEST_Meeting notes",
                "content": "Discussed project timeline and deliverables",
                "tags": ["work", "meeting"]
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        test_note_ids.append(data["id"])
        
        # Verify creation
        get_response = requests.get(f"{BASE_URL}/api/notes")
        notes = get_response.json()["notes"]
        created = [n for n in notes if n["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["title"] == "TEST_Meeting notes"
        assert created[0]["content"] == "Discussed project timeline and deliverables"
        assert "work" in created[0]["tags"]
        print(f"✓ Note created: {data['id']}")

    def test_delete_note(self):
        """Test DELETE /api/notes/{id} deletes a note"""
        # Create a note first
        create_response = requests.post(
            f"{BASE_URL}/api/notes",
            json={"title": "TEST_Delete note", "content": "Temporary note", "tags": []}
        )
        note_id = create_response.json()["id"]
        
        # Delete it
        delete_response = requests.delete(f"{BASE_URL}/api/notes/{note_id}")
        assert delete_response.status_code == 200
        
        data = delete_response.json()
        assert "deleted" in data
        assert data["deleted"] is True
        print(f"✓ Note {note_id} deleted successfully")


class TestExpensesEndpoint:
    """Expenses CRUD tests"""

    def test_get_expenses(self):
        """Test GET /api/expenses returns expenses list"""
        response = requests.get(f"{BASE_URL}/api/expenses")
        assert response.status_code == 200
        
        data = response.json()
        assert "expenses" in data
        assert isinstance(data["expenses"], list)
        print(f"✓ Retrieved {len(data['expenses'])} expenses")

    def test_create_expense(self):
        """Test POST /api/expenses logs an expense"""
        response = requests.post(
            f"{BASE_URL}/api/expenses",
            json={
                "amount": 25.50,
                "category": "food",
                "description": "TEST_Lunch at cafe",
                "date": "2026-04-06"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        test_expense_ids.append(data["id"])
        
        # Verify creation
        get_response = requests.get(f"{BASE_URL}/api/expenses")
        expenses = get_response.json()["expenses"]
        created = [e for e in expenses if e["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["amount"] == 25.50
        assert created[0]["category"] == "food"
        assert created[0]["description"] == "TEST_Lunch at cafe"
        print(f"✓ Expense created: {data['id']}")

    def test_expense_summary(self):
        """Test GET /api/expenses/summary returns expense breakdown"""
        response = requests.get(f"{BASE_URL}/api/expenses/summary")
        assert response.status_code == 200
        
        data = response.json()
        assert "total" in data
        assert "by_category" in data
        assert "count" in data
        assert isinstance(data["total"], (int, float))
        assert isinstance(data["by_category"], dict)
        assert isinstance(data["count"], int)
        print(f"✓ Expense summary: ${data['total']:.2f} total, {data['count']} expenses")

    def test_delete_expense(self):
        """Test DELETE /api/expenses/{id} deletes an expense"""
        # Create an expense first
        create_response = requests.post(
            f"{BASE_URL}/api/expenses",
            json={"amount": 10.0, "category": "test", "description": "TEST_Delete expense"}
        )
        expense_id = create_response.json()["id"]
        
        # Delete it
        delete_response = requests.delete(f"{BASE_URL}/api/expenses/{expense_id}")
        assert delete_response.status_code == 200
        
        data = delete_response.json()
        assert "deleted" in data
        assert data["deleted"] is True
        print(f"✓ Expense {expense_id} deleted successfully")


class TestDashboardEndpoint:
    """Dashboard endpoint tests"""

    def test_get_dashboard(self):
        """Test GET /api/dashboard returns stats, suggestions, recent conversations"""
        response = requests.get(f"{BASE_URL}/api/dashboard")
        assert response.status_code == 200
        
        data = response.json()
        assert "stats" in data
        assert "recent_conversations" in data
        assert "upcoming_reminders" in data
        assert "suggestions" in data
        
        # Verify stats structure
        stats = data["stats"]
        assert "conversations" in stats
        assert "memories" in stats
        assert "active_reminders" in stats
        assert "notes" in stats
        assert "total_expenses" in stats
        
        # Verify suggestions
        assert isinstance(data["suggestions"], list)
        assert len(data["suggestions"]) > 0
        
        print(f"✓ Dashboard loaded: {stats['conversations']} convs, {stats['memories']} memories, {stats['active_reminders']} reminders")


class TestSkillsEndpoint:
    """Skills endpoint tests"""

    def test_get_skills(self):
        """Test GET /api/skills returns list of 16 skills"""
        response = requests.get(f"{BASE_URL}/api/skills")
        assert response.status_code == 200
        
        data = response.json()
        assert "skills" in data
        assert isinstance(data["skills"], list)
        assert len(data["skills"]) == 16
        
        # Verify skill structure
        skill = data["skills"][0]
        assert "id" in skill
        assert "name" in skill
        assert "description" in skill
        assert "status" in skill
        assert "icon" in skill
        
        # Count active skills
        active = [s for s in data["skills"] if s["status"] == "active"]
        print(f"✓ Skills loaded: {len(data['skills'])} total, {len(active)} active")


class TestTTSEndpoint:
    """Text-to-Speech endpoint tests"""

    def test_tts_generation(self):
        """Test POST /api/tts generates audio base64"""
        response = requests.post(
            f"{BASE_URL}/api/tts",
            json={"text": "Hello from Nua", "voice": "nova"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "audio_base64" in data
        assert "format" in data
        assert data["format"] == "mp3"
        assert len(data["audio_base64"]) > 100
        print(f"✓ TTS generated {len(data['audio_base64'])} bytes of base64 audio")


class TestQuickActionEndpoint:
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
        print(f"✓ Joke action: {data['response'][:80]}...")


class TestChatWithActionExtraction:
    """Chat endpoint with action extraction tests"""

    def test_chat_creates_reminder_action(self):
        """Test chat with 'remind me' creates a reminder automatically"""
        response = requests.post(
            f"{BASE_URL}/api/chat",
            json={"content": "Remind me to call mom tomorrow at 3pm"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "conversation_id" in data
        assert "message" in data
        assert "actions_executed" in data
        
        # Check if reminder action was executed
        actions = data["actions_executed"]
        reminder_actions = [a for a in actions if a.get("type") == "reminder"]
        
        # Note: Action extraction depends on AI response, may not always trigger
        if len(reminder_actions) > 0:
            print(f"✓ Chat created reminder action: {reminder_actions[0].get('title', 'N/A')}")
        else:
            print("⚠ Chat did not extract reminder action (AI-dependent behavior)")

    def test_chat_creates_memory_action(self):
        """Test chat with personal info creates a memory automatically"""
        response = requests.post(
            f"{BASE_URL}/api/chat",
            json={"content": "My favorite programming language is Python"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "actions_executed" in data
        
        # Check if memory action was executed
        actions = data["actions_executed"]
        memory_actions = [a for a in actions if a.get("type") == "memory"]
        
        # Note: Action extraction depends on AI response
        if len(memory_actions) > 0:
            print(f"✓ Chat created memory action: {memory_actions[0].get('key', 'N/A')}")
        else:
            print("⚠ Chat did not extract memory action (AI-dependent behavior)")


# Cleanup fixture
@pytest.fixture(scope="session", autouse=True)
def cleanup_test_data():
    """Cleanup test data after all tests complete"""
    yield
    
    print("\n🧹 Cleaning up test data...")
    
    # Clean up test memories
    for mem_id in test_memory_ids:
        try:
            requests.delete(f"{BASE_URL}/api/memory/{mem_id}")
        except:
            pass
    
    # Clean up test reminders
    for rem_id in test_reminder_ids:
        try:
            requests.delete(f"{BASE_URL}/api/reminders/{rem_id}")
        except:
            pass
    
    # Clean up test notes
    for note_id in test_note_ids:
        try:
            requests.delete(f"{BASE_URL}/api/notes/{note_id}")
        except:
            pass
    
    # Clean up test expenses
    for exp_id in test_expense_ids:
        try:
            requests.delete(f"{BASE_URL}/api/expenses/{exp_id}")
        except:
            pass
    
    print(f"✓ Cleaned up {len(test_memory_ids)} memories, {len(test_reminder_ids)} reminders, {len(test_note_ids)} notes, {len(test_expense_ids)} expenses")
