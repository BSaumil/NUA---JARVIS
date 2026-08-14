"""
Nua AI Assistant API Tests (Iteration 4)
Tests for 7 Intelligence Layers:
- Layer 2: Memory OS (6 types)
- Layer 3: Trust Engine
- Layer 4: Autonomous Action Engine (T0-T5)
- Layer 5: Goals with sub-tasks
- Layer 6: Dreams 2.0
- Layer 7: Enhanced system prompt (tested via chat)
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

# Test data cleanup lists
test_memory_ids = []
test_goal_ids = []
test_dream_ids = []


class TestMemoryOS:
    """Layer 2: Memory OS - 6 Intelligence Types"""

    def test_get_memory_os_structure(self):
        """Test GET /api/memory/os returns memories grouped by 6 types with stats"""
        response = requests.get(f"{BASE_URL}/api/memory/os")
        assert response.status_code == 200
        
        data = response.json()
        
        # Verify structure
        assert "memories" in data
        assert "stats" in data
        assert "types" in data
        
        # Verify 6 memory types
        types = data["types"]
        assert len(types) == 6
        expected_types = ["identity", "episodic", "semantic", "behavioral", "emotional", "relationship"]
        for t in expected_types:
            assert t in types
        
        # Verify memories grouped by type
        memories = data["memories"]
        for t in expected_types:
            assert t in memories
            assert isinstance(memories[t], list)
        
        # Verify stats
        stats = data["stats"]
        assert "total" in stats
        for t in expected_types:
            assert t in stats
        
        print(f"✓ Memory OS: {stats['total']} total memories across 6 types")
        print(f"  - Identity: {stats['identity']}, Episodic: {stats['episodic']}, Semantic: {stats['semantic']}")
        print(f"  - Behavioral: {stats['behavioral']}, Emotional: {stats['emotional']}, Relationship: {stats['relationship']}")

    def test_create_advanced_memory_identity(self):
        """Test POST /api/memory/advanced creates identity memory with confidence and why"""
        response = requests.post(
            f"{BASE_URL}/api/memory/advanced",
            json={
                "mem_type": "identity",
                "category": "name",
                "key": "TEST_User Name",
                "value": "Alex Johnson",
                "confidence": 0.95,
                "why": "User explicitly stated their name in conversation"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        assert "identity" in data["message"]
        
        test_memory_ids.append(data["id"])
        
        # Verify creation by fetching memory OS
        get_response = requests.get(f"{BASE_URL}/api/memory/os")
        memories = get_response.json()["memories"]["identity"]
        created = [m for m in memories if m["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["key"] == "TEST_User Name"
        assert created[0]["value"] == "Alex Johnson"
        assert created[0]["confidence"] == 0.95
        assert created[0]["why"] == "User explicitly stated their name in conversation"
        
        print(f"✓ Identity memory created: {data['id']}")

    def test_create_advanced_memory_behavioral(self):
        """Test POST /api/memory/advanced creates behavioral memory"""
        response = requests.post(
            f"{BASE_URL}/api/memory/advanced",
            json={
                "mem_type": "behavioral",
                "category": "habit",
                "key": "TEST_Morning Routine",
                "value": "Wakes up at 6 AM, exercises for 30 minutes",
                "confidence": 0.85,
                "why": "User mentioned this routine 3 times in past week"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        test_memory_ids.append(data["id"])
        print(f"✓ Behavioral memory created: {data['id']}")

    def test_create_advanced_memory_emotional(self):
        """Test POST /api/memory/advanced creates emotional memory"""
        response = requests.post(
            f"{BASE_URL}/api/memory/advanced",
            json={
                "mem_type": "emotional",
                "category": "feeling",
                "key": "TEST_Stress Trigger",
                "value": "Gets stressed about deadlines",
                "confidence": 0.80,
                "why": "Detected stress patterns in conversations about work deadlines"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        test_memory_ids.append(data["id"])
        print(f"✓ Emotional memory created: {data['id']}")

    def test_explain_memory(self):
        """Test GET /api/memory/explain/{id} returns why NUA remembers something"""
        # Create a memory first
        create_response = requests.post(
            f"{BASE_URL}/api/memory/advanced",
            json={
                "mem_type": "semantic",
                "category": "preference",
                "key": "TEST_Favorite Food",
                "value": "Italian cuisine",
                "confidence": 0.90,
                "why": "User ordered Italian food 5 times this month"
            }
        )
        memory_id = create_response.json()["id"]
        test_memory_ids.append(memory_id)
        
        # Explain the memory
        response = requests.get(f"{BASE_URL}/api/memory/explain/{memory_id}")
        assert response.status_code == 200
        
        data = response.json()
        assert "memory" in data
        assert "explanation" in data
        
        explanation = data["explanation"]
        assert "why" in explanation
        assert "source" in explanation
        assert "confidence" in explanation
        assert "first_learned" in explanation
        assert "last_used" in explanation
        assert "times_used" in explanation
        
        assert explanation["why"] == "User ordered Italian food 5 times this month"
        assert explanation["confidence"] == 0.90
        
        print(f"✓ Memory explanation retrieved: {explanation['why']}")

    def test_forget_memory_with_trust_logging(self):
        """Test POST /api/memory/forget/{id} deletes memory and logs to trust ledger"""
        # Create a memory first
        create_response = requests.post(
            f"{BASE_URL}/api/memory/advanced",
            json={
                "mem_type": "semantic",
                "category": "general",
                "key": "TEST_To Forget",
                "value": "This will be forgotten",
                "confidence": 0.70,
                "why": "Test memory for deletion"
            }
        )
        memory_id = create_response.json()["id"]
        
        # Get trust ledger count before
        ledger_before = requests.get(f"{BASE_URL}/api/trust/ledger").json()
        events_before = len(ledger_before["events"])
        
        # Forget the memory
        response = requests.post(f"{BASE_URL}/api/memory/forget/{memory_id}")
        assert response.status_code == 200
        
        data = response.json()
        assert "forgotten" in data
        assert data["forgotten"] is True
        assert data["memory_id"] == memory_id
        
        # Verify memory is deleted
        get_response = requests.get(f"{BASE_URL}/api/memory/os")
        all_memories = []
        for mem_type in get_response.json()["memories"].values():
            all_memories.extend(mem_type)
        
        deleted = [m for m in all_memories if m["id"] == memory_id]
        assert len(deleted) == 0
        
        # Verify trust ledger event was created
        ledger_after = requests.get(f"{BASE_URL}/api/trust/ledger").json()
        events_after = len(ledger_after["events"])
        assert events_after > events_before
        
        # Check for correction event
        recent_event = ledger_after["events"][0]
        assert recent_event["event_type"] == "correction"
        assert "forget" in recent_event["description"].lower()
        
        print(f"✓ Memory {memory_id} forgotten and logged to trust ledger")


class TestTrustEngine:
    """Layer 3: Trust Engine"""

    def test_get_trust_score(self):
        """Test GET /api/trust returns trust score, level, breakdown, recent events"""
        response = requests.get(f"{BASE_URL}/api/trust")
        assert response.status_code == 200
        
        data = response.json()
        
        # Verify structure
        assert "score" in data
        assert "level" in data
        assert "total_events" in data
        assert "breakdown" in data
        assert "recent_events" in data
        
        # Verify score is 0-100
        assert 0 <= data["score"] <= 100
        
        # Verify level is one of the expected values
        valid_levels = ["New", "Learning", "Building Trust", "Trusted", "Highly Trusted", "Autonomous"]
        assert data["level"] in valid_levels
        
        # Verify breakdown has event types
        breakdown = data["breakdown"]
        expected_types = ["success", "failure", "correction", "rejection", "confirmation", "prediction"]
        for t in expected_types:
            assert t in breakdown
        
        # Verify recent events is a list
        assert isinstance(data["recent_events"], list)
        
        print(f"✓ Trust Score: {data['score']}% ({data['level']})")
        print(f"  - Total events: {data['total_events']}")
        print(f"  - Breakdown: {breakdown}")

    def test_get_trust_ledger(self):
        """Test GET /api/trust/ledger returns trust event history"""
        response = requests.get(f"{BASE_URL}/api/trust/ledger")
        assert response.status_code == 200
        
        data = response.json()
        assert "events" in data
        assert isinstance(data["events"], list)
        
        # If there are events, verify structure
        if len(data["events"]) > 0:
            event = data["events"][0]
            assert "id" in event
            assert "event_type" in event
            assert "description" in event
            assert "score_change" in event
            assert "created_at" in event
            
            print(f"✓ Trust Ledger: {len(data['events'])} events")
            print(f"  - Latest: {event['event_type']} - {event['description']}")
        else:
            print(f"✓ Trust Ledger: 0 events (new system)")


class TestAutonomousActionEngine:
    """Layer 4: Autonomous Action Engine (T0-T5)"""

    def test_get_action_tiers(self):
        """Test GET /api/actions/tiers returns T0-T5 tier definitions"""
        response = requests.get(f"{BASE_URL}/api/actions/tiers")
        assert response.status_code == 200
        
        data = response.json()
        assert "tiers" in data
        
        tiers = data["tiers"]
        expected_tiers = ["T0", "T1", "T2", "T3", "T4", "T5"]
        
        for tier in expected_tiers:
            assert tier in tiers
            tier_data = tiers[tier]
            assert "name" in tier_data
            assert "description" in tier_data
            assert "auto" in tier_data
        
        # Verify T0-T2 are auto, T3-T5 are not
        assert tiers["T0"]["auto"] is True
        assert tiers["T1"]["auto"] is True
        assert tiers["T2"]["auto"] is True
        assert tiers["T3"]["auto"] is False
        assert tiers["T4"]["auto"] is False
        assert tiers["T5"]["auto"] is False
        
        print(f"✓ Action Tiers: 6 tiers defined (T0-T5)")
        for tier in expected_tiers:
            print(f"  - {tier}: {tiers[tier]['name']} - {tiers[tier]['description']}")

    def test_get_action_log(self):
        """Test GET /api/actions/log returns action history"""
        response = requests.get(f"{BASE_URL}/api/actions/log")
        assert response.status_code == 200
        
        data = response.json()
        assert "actions" in data
        assert isinstance(data["actions"], list)
        
        # If there are actions, verify structure
        if len(data["actions"]) > 0:
            action = data["actions"][0]
            assert "id" in action
            assert "action" in action
            assert "tier" in action
            assert "intent" in action
            assert "result" in action
            assert "risk_level" in action
            assert "verified" in action
            assert "created_at" in action
            
            print(f"✓ Action Log: {len(data['actions'])} actions")
            print(f"  - Latest: {action['tier']} - {action['action']} ({action['result']})")
        else:
            print(f"✓ Action Log: 0 actions (new system)")


class TestGoals:
    """Layer 5: Goals with sub-tasks and AI analysis"""

    def test_create_goal(self):
        """Test POST /api/goals creates a goal"""
        response = requests.post(
            f"{BASE_URL}/api/goals",
            json={
                "title": "TEST_Learn Python",
                "description": "Master Python programming in 3 months"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        
        test_goal_ids.append(data["id"])
        
        # Verify creation by fetching goals
        get_response = requests.get(f"{BASE_URL}/api/goals")
        goals = get_response.json()["goals"]
        created = [g for g in goals if g["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["title"] == "TEST_Learn Python"
        assert created[0]["description"] == "Master Python programming in 3 months"
        assert created[0]["status"] == "active"
        assert created[0]["progress"] == 0
        assert created[0]["sub_tasks"] == []
        
        print(f"✓ Goal created: {data['id']}")

    def test_list_goals(self):
        """Test GET /api/goals lists goals"""
        response = requests.get(f"{BASE_URL}/api/goals")
        assert response.status_code == 200
        
        data = response.json()
        assert "goals" in data
        assert isinstance(data["goals"], list)
        
        print(f"✓ Goals list: {len(data['goals'])} goals")

    def test_add_subtask_to_goal(self):
        """Test POST /api/goals/{id}/subtask adds a sub-task"""
        # Create a goal first
        create_response = requests.post(
            f"{BASE_URL}/api/goals",
            json={
                "title": "TEST_Build Portfolio",
                "description": "Create a professional portfolio website"
            }
        )
        goal_id = create_response.json()["id"]
        test_goal_ids.append(goal_id)
        
        # Add sub-task
        response = requests.post(
            f"{BASE_URL}/api/goals/{goal_id}/subtask",
            json={"title": "Design wireframes"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "subtask" in data
        subtask = data["subtask"]
        assert "id" in subtask
        assert subtask["title"] == "Design wireframes"
        assert subtask["completed"] is False
        
        # Verify sub-task was added
        get_response = requests.get(f"{BASE_URL}/api/goals")
        goals = get_response.json()["goals"]
        goal = [g for g in goals if g["id"] == goal_id][0]
        assert len(goal["sub_tasks"]) == 1
        assert goal["sub_tasks"][0]["title"] == "Design wireframes"
        
        print(f"✓ Sub-task added to goal {goal_id}")

    def test_toggle_subtask_and_update_progress(self):
        """Test PATCH /api/goals/{id}/subtask/{sid} toggles completion and updates progress"""
        # Create a goal with 2 sub-tasks
        create_response = requests.post(
            f"{BASE_URL}/api/goals",
            json={
                "title": "TEST_Complete Project",
                "description": "Finish the project on time"
            }
        )
        goal_id = create_response.json()["id"]
        test_goal_ids.append(goal_id)
        
        # Add 2 sub-tasks
        sub1_response = requests.post(
            f"{BASE_URL}/api/goals/{goal_id}/subtask",
            json={"title": "Task 1"}
        )
        subtask1_id = sub1_response.json()["subtask"]["id"]
        
        sub2_response = requests.post(
            f"{BASE_URL}/api/goals/{goal_id}/subtask",
            json={"title": "Task 2"}
        )
        subtask2_id = sub2_response.json()["subtask"]["id"]
        
        # Toggle first sub-task to completed
        response = requests.patch(f"{BASE_URL}/api/goals/{goal_id}/subtask/{subtask1_id}")
        assert response.status_code == 200
        
        data = response.json()
        assert "progress" in data
        assert "status" in data
        assert data["progress"] == 50  # 1 out of 2 completed
        assert data["status"] == "active"
        
        # Verify progress updated
        get_response = requests.get(f"{BASE_URL}/api/goals")
        goals = get_response.json()["goals"]
        goal = [g for g in goals if g["id"] == goal_id][0]
        assert goal["progress"] == 50
        assert goal["sub_tasks"][0]["completed"] is True
        assert goal["sub_tasks"][1]["completed"] is False
        
        # Toggle second sub-task to completed
        response2 = requests.patch(f"{BASE_URL}/api/goals/{goal_id}/subtask/{subtask2_id}")
        data2 = response2.json()
        assert data2["progress"] == 100
        assert data2["status"] == "completed"
        
        print(f"✓ Sub-task toggled: progress updated to {data['progress']}% → {data2['progress']}%")

    def test_analyze_goal_with_ai(self):
        """Test POST /api/goals/{id}/analyze gets AI analysis of goal progress"""
        # Create a goal with some sub-tasks
        create_response = requests.post(
            f"{BASE_URL}/api/goals",
            json={
                "title": "TEST_Get Fit",
                "description": "Improve physical fitness and health"
            }
        )
        goal_id = create_response.json()["id"]
        test_goal_ids.append(goal_id)
        
        # Add sub-tasks
        requests.post(
            f"{BASE_URL}/api/goals/{goal_id}/subtask",
            json={"title": "Exercise 3 times a week"}
        )
        requests.post(
            f"{BASE_URL}/api/goals/{goal_id}/subtask",
            json={"title": "Eat healthy meals"}
        )
        
        # Analyze goal (this uses GPT-5.2, may take a few seconds)
        response = requests.post(f"{BASE_URL}/api/goals/{goal_id}/analyze")
        assert response.status_code == 200
        
        data = response.json()
        assert "insight" in data
        assert "goal_id" in data
        assert data["goal_id"] == goal_id
        
        # Verify insight is not empty
        insight = data["insight"]
        assert len(insight) > 0
        
        # Verify insight was stored in goal
        get_response = requests.get(f"{BASE_URL}/api/goals")
        goals = get_response.json()["goals"]
        goal = [g for g in goals if g["id"] == goal_id][0]
        assert len(goal["insights"]) > 0
        
        print(f"✓ Goal analyzed: {len(insight)} chars of AI insight generated")
        print(f"  - Insight preview: {insight[:100]}...")


class TestDreams:
    """Layer 6: Dreams 2.0 - Cross-referenced insights"""

    def test_list_dreams(self):
        """Test GET /api/dreams lists dreams"""
        response = requests.get(f"{BASE_URL}/api/dreams")
        assert response.status_code == 200
        
        data = response.json()
        assert "dreams" in data
        assert "categories" in data
        assert isinstance(data["dreams"], list)
        
        # Verify categories
        categories = data["categories"]
        expected_categories = ["opportunity", "pattern", "reminder", "concern", "optimization",
                              "relationship", "finance", "productivity", "learning", "business"]
        for cat in expected_categories:
            assert cat in categories
        
        print(f"✓ Dreams list: {len(data['dreams'])} dreams, {len(categories)} categories")

    def test_generate_dream(self):
        """Test POST /api/dreams/generate creates a cross-referenced insight dream"""
        # This test uses GPT-5.2 to generate a dream from all user data
        # It may take several seconds to complete
        
        print("  Generating dream (this may take 5-10 seconds)...")
        response = requests.post(f"{BASE_URL}/api/dreams/generate", timeout=30)
        assert response.status_code == 200
        
        data = response.json()
        assert "dream" in data
        
        dream = data["dream"]
        assert "id" in dream
        assert "category" in dream
        assert "title" in dream
        assert "content" in dream
        assert "impact" in dream
        assert "acted_on" in dream
        assert "source_data" in dream
        assert "created_at" in dream
        
        test_dream_ids.append(dream["id"])
        
        # Verify category is valid
        valid_categories = ["opportunity", "pattern", "reminder", "concern", "optimization",
                           "relationship", "finance", "productivity", "learning", "business"]
        assert dream["category"] in valid_categories
        
        # Verify impact is valid
        assert dream["impact"] in ["low", "medium", "high"]
        
        # Verify content is not empty
        assert len(dream["content"]) > 0
        
        print(f"✓ Dream generated: {dream['id']}")
        print(f"  - Category: {dream['category']}, Impact: {dream['impact']}")
        print(f"  - Title: {dream['title']}")
        print(f"  - Content: {dream['content'][:100]}...")


class TestEnhancedChat:
    """Layer 7: Enhanced system prompt with memory-aware chat and trust tracking"""

    def test_chat_with_memory_awareness(self):
        """Test POST /api/chat with memory-aware prompt sends message and gets response"""
        # First, create some memories
        requests.post(
            f"{BASE_URL}/api/memory/advanced",
            json={
                "mem_type": "identity",
                "category": "name",
                "key": "TEST_Chat User",
                "value": "Sarah",
                "confidence": 0.95,
                "why": "Test memory for chat"
            }
        )
        
        # Send a chat message
        response = requests.post(
            f"{BASE_URL}/api/chat",
            json={"content": "Hello, what's my name?"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "conversation_id" in data
        assert "message" in data
        assert "actions_executed" in data
        
        message = data["message"]
        assert "id" in message
        assert "role" in message
        assert message["role"] == "assistant"
        assert "content" in message
        assert "created_at" in message
        
        # Verify response is not empty
        assert len(message["content"]) > 0
        
        print(f"✓ Chat with memory awareness: {len(message['content'])} chars response")
        print(f"  - Response preview: {message['content'][:100]}...")


# Cleanup fixture
@pytest.fixture(scope="session", autouse=True)
def cleanup_test_data():
    """Cleanup test data after all tests complete"""
    yield
    
    print("\n🧹 Cleaning up iteration 4 test data...")
    
    # Clean up test memories
    for memory_id in test_memory_ids:
        try:
            requests.post(f"{BASE_URL}/api/memory/forget/{memory_id}")
        except:
            pass
    
    # Clean up test goals
    for goal_id in test_goal_ids:
        try:
            requests.delete(f"{BASE_URL}/api/goals/{goal_id}")
        except:
            pass
    
    # Note: Dreams don't have a delete endpoint, they accumulate
    # Trust ledger events are permanent (by design)
    
    print(f"✓ Cleaned up {len(test_memory_ids)} memories, {len(test_goal_ids)} goals")
    print(f"  Note: {len(test_dream_ids)} dreams and trust ledger events are permanent")
