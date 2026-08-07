"""
Nua AI Assistant API Tests (Iteration 3)
Tests for NEW features: Weather, News, Calendar, Smart Home, Skills Marketplace
"""
import pytest
import requests
import os
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
test_calendar_event_ids = []
test_installed_skill_ids = []


class TestWeatherAPI:
    """Weather API tests (Open-Meteo integration)"""

    def test_get_weather_default_location(self):
        """Test GET /api/weather returns weather data for default location"""
        response = requests.get(f"{BASE_URL}/api/weather")
        assert response.status_code == 200
        
        data = response.json()
        
        # Check for error field first
        if "error" in data:
            pytest.skip(f"Weather API error: {data['error']}")
        
        # Verify weather data structure
        assert "temperature" in data
        assert "feels_like" in data
        assert "humidity" in data
        assert "wind_speed" in data
        assert "description" in data
        assert "icon" in data
        assert "forecast" in data
        
        # Verify temperature is a number
        assert isinstance(data["temperature"], (int, float))
        assert isinstance(data["humidity"], (int, float))
        
        # Verify forecast is a list
        assert isinstance(data["forecast"], list)
        assert len(data["forecast"]) > 0
        
        # Verify forecast structure
        forecast_day = data["forecast"][0]
        assert "date" in forecast_day
        assert "max" in forecast_day
        assert "min" in forecast_day
        assert "description" in forecast_day
        assert "icon" in forecast_day
        
        print(f"✓ Weather: {data['temperature']}°C, {data['description']}, {len(data['forecast'])} day forecast")

    def test_get_weather_custom_location(self):
        """Test GET /api/weather with custom lat/lon"""
        # London coordinates
        response = requests.get(f"{BASE_URL}/api/weather?lat=51.5074&lon=-0.1278")
        assert response.status_code == 200
        
        data = response.json()
        
        if "error" in data:
            pytest.skip(f"Weather API error: {data['error']}")
        
        assert "temperature" in data
        assert "description" in data
        print(f"✓ Weather (London): {data['temperature']}°C, {data['description']}")

    def test_search_city(self):
        """Test GET /api/weather/search returns city results"""
        response = requests.get(f"{BASE_URL}/api/weather/search?q=London")
        assert response.status_code == 200
        
        data = response.json()
        assert "results" in data
        assert isinstance(data["results"], list)
        
        if len(data["results"]) > 0:
            city = data["results"][0]
            assert "name" in city
            assert "country" in city
            assert "lat" in city
            assert "lon" in city
            print(f"✓ City search: Found {len(data['results'])} results for 'London'")
        else:
            print("⚠ City search returned no results (API may be rate-limited)")


class TestNewsAPI:
    """News API tests (RSS feeds integration)"""

    def test_get_news_default_category(self):
        """Test GET /api/news returns top news articles"""
        response = requests.get(f"{BASE_URL}/api/news")
        assert response.status_code == 200
        
        data = response.json()
        assert "articles" in data
        assert "category" in data
        
        if "error" in data:
            pytest.skip(f"News API error: {data['error']}")
        
        articles = data["articles"]
        assert isinstance(articles, list)
        
        if len(articles) > 0:
            article = articles[0]
            assert "title" in article
            assert "summary" in article
            assert "link" in article
            assert "source" in article
            print(f"✓ News (top): Retrieved {len(articles)} articles")
        else:
            print("⚠ News API returned no articles")

    def test_get_news_tech_category(self):
        """Test GET /api/news?category=tech returns tech news"""
        response = requests.get(f"{BASE_URL}/api/news?category=tech")
        assert response.status_code == 200
        
        data = response.json()
        assert "articles" in data
        assert data["category"] == "tech"
        
        if "error" not in data and len(data["articles"]) > 0:
            print(f"✓ News (tech): Retrieved {len(data['articles'])} tech articles")
        else:
            print("⚠ Tech news returned no articles")

    def test_get_news_science_category(self):
        """Test GET /api/news?category=science returns science news"""
        response = requests.get(f"{BASE_URL}/api/news?category=science")
        assert response.status_code == 200
        
        data = response.json()
        assert "articles" in data
        assert data["category"] == "science"
        print(f"✓ News (science): Retrieved {len(data.get('articles', []))} science articles")


class TestCalendarAPI:
    """Calendar API tests (Local MongoDB-based calendar)"""

    def test_get_calendar_events(self):
        """Test GET /api/calendar/events returns events list"""
        response = requests.get(f"{BASE_URL}/api/calendar/events")
        assert response.status_code == 200
        
        data = response.json()
        assert "events" in data
        assert isinstance(data["events"], list)
        print(f"✓ Calendar: Retrieved {len(data['events'])} events")

    def test_create_calendar_event(self):
        """Test POST /api/calendar/events creates an event"""
        response = requests.post(
            f"{BASE_URL}/api/calendar/events",
            json={
                "title": "TEST_Team Meeting",
                "description": "Weekly sync with the team",
                "start_time": "2026-08-10T10:00:00Z",
                "end_time": "2026-08-10T11:00:00Z",
                "location": "Conference Room A",
                "color": "#FFB800"
            }
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "id" in data
        assert "message" in data
        test_calendar_event_ids.append(data["id"])
        
        # Verify creation by fetching all events
        get_response = requests.get(f"{BASE_URL}/api/calendar/events")
        events = get_response.json()["events"]
        created = [e for e in events if e["id"] == data["id"]]
        assert len(created) == 1
        assert created[0]["title"] == "TEST_Team Meeting"
        assert created[0]["location"] == "Conference Room A"
        print(f"✓ Calendar event created: {data['id']}")

    def test_delete_calendar_event(self):
        """Test DELETE /api/calendar/events/{id} deletes an event"""
        # Create an event first
        create_response = requests.post(
            f"{BASE_URL}/api/calendar/events",
            json={
                "title": "TEST_Delete Event",
                "description": "Temporary event",
                "start_time": "2026-08-15T14:00:00Z",
                "end_time": "2026-08-15T15:00:00Z"
            }
        )
        event_id = create_response.json()["id"]
        
        # Delete it
        delete_response = requests.delete(f"{BASE_URL}/api/calendar/events/{event_id}")
        assert delete_response.status_code == 200
        
        data = delete_response.json()
        assert "deleted" in data
        assert data["deleted"] is True
        print(f"✓ Calendar event {event_id} deleted successfully")

    def test_calendar_status(self):
        """Test GET /api/calendar/status returns calendar connection info"""
        response = requests.get(f"{BASE_URL}/api/calendar/status")
        assert response.status_code == 200
        
        data = response.json()
        assert "local_events" in data
        assert "google_connected" in data
        assert data["google_connected"] is False
        assert "google_instructions" in data
        print(f"✓ Calendar status: {data['local_events']} local events, Google not connected")


class TestSmartHomeAPI:
    """Smart Home API tests (Mock simulation)"""

    def test_get_smart_home_devices(self):
        """Test GET /api/smart-home/devices returns 8 mock devices"""
        response = requests.get(f"{BASE_URL}/api/smart-home/devices")
        assert response.status_code == 200
        
        data = response.json()
        assert "devices" in data
        assert "is_simulation" in data
        assert data["is_simulation"] is True
        
        devices = data["devices"]
        assert isinstance(devices, list)
        assert len(devices) == 8
        
        # Verify device structure
        device = devices[0]
        assert "id" in device
        assert "name" in device
        assert "type" in device
        assert "room" in device
        assert "status" in device
        
        # Count device types
        device_types = {}
        for d in devices:
            device_types[d["type"]] = device_types.get(d["type"], 0) + 1
        
        print(f"✓ Smart Home: {len(devices)} devices ({', '.join([f'{v} {k}' for k, v in device_types.items()])})")

    def test_control_device_toggle(self):
        """Test POST /api/smart-home/control toggles device status"""
        # First, get a device
        get_response = requests.get(f"{BASE_URL}/api/smart-home/devices")
        devices = get_response.json()["devices"]
        light_device = [d for d in devices if d["type"] == "light"][0]
        device_id = light_device["id"]
        initial_status = light_device["status"]
        
        # Toggle the device
        control_response = requests.post(
            f"{BASE_URL}/api/smart-home/control",
            json={"device_id": device_id, "action": "toggle"}
        )
        assert control_response.status_code == 200
        
        data = control_response.json()
        assert "device" in data
        assert "message" in data
        
        # Verify status changed
        new_status = data["device"]["status"]
        assert new_status != initial_status
        print(f"✓ Device {device_id} toggled: {initial_status} → {new_status}")

    def test_activate_scene_morning(self):
        """Test POST /api/smart-home/scene activates morning scene"""
        response = requests.post(
            f"{BASE_URL}/api/smart-home/scene",
            json={"scene": "morning"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "scene" in data
        assert data["scene"] == "morning"
        assert "applied" in data
        assert data["applied"] is True
        assert "devices_affected" in data
        print(f"✓ Morning scene activated: {data['devices_affected']} devices affected")

    def test_activate_scene_movie(self):
        """Test POST /api/smart-home/scene activates movie scene"""
        response = requests.post(
            f"{BASE_URL}/api/smart-home/scene",
            json={"scene": "movie"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["scene"] == "movie"
        assert data["applied"] is True
        print(f"✓ Movie scene activated: {data['devices_affected']} devices affected")

    def test_activate_scene_night(self):
        """Test POST /api/smart-home/scene activates night scene"""
        response = requests.post(
            f"{BASE_URL}/api/smart-home/scene",
            json={"scene": "night"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["scene"] == "night"
        print(f"✓ Night scene activated")

    def test_activate_scene_focus(self):
        """Test POST /api/smart-home/scene activates focus scene"""
        response = requests.post(
            f"{BASE_URL}/api/smart-home/scene",
            json={"scene": "focus"}
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["scene"] == "focus"
        print(f"✓ Focus scene activated")


class TestSkillsMarketplaceAPI:
    """Skills Marketplace API tests"""

    def test_get_marketplace(self):
        """Test GET /api/skills/marketplace returns 10 skills with categories"""
        response = requests.get(f"{BASE_URL}/api/skills/marketplace")
        assert response.status_code == 200
        
        data = response.json()
        assert "skills" in data
        assert "categories" in data
        
        skills = data["skills"]
        assert isinstance(skills, list)
        assert len(skills) == 10
        
        # Verify skill structure
        skill = skills[0]
        assert "id" in skill
        assert "name" in skill
        assert "description" in skill
        assert "category" in skill
        assert "icon" in skill
        assert "rating" in skill
        assert "downloads" in skill
        assert "installed" in skill
        assert "free" in skill
        
        # Verify categories
        categories = data["categories"]
        assert isinstance(categories, list)
        assert len(categories) > 0
        
        # Count installed skills
        installed_count = sum(1 for s in skills if s["installed"])
        
        print(f"✓ Marketplace: {len(skills)} skills, {len(categories)} categories, {installed_count} installed")

    def test_install_skill(self):
        """Test POST /api/skills/install/{skill_id} installs a skill"""
        skill_id = "weather"
        
        response = requests.post(f"{BASE_URL}/api/skills/install/{skill_id}")
        assert response.status_code == 200
        
        data = response.json()
        assert "installed" in data
        assert data["installed"] is True
        assert "skill_id" in data
        assert data["skill_id"] == skill_id
        
        test_installed_skill_ids.append(skill_id)
        
        # Verify installation by checking marketplace
        get_response = requests.get(f"{BASE_URL}/api/skills/marketplace")
        skills = get_response.json()["skills"]
        weather_skill = [s for s in skills if s["id"] == skill_id][0]
        assert weather_skill["installed"] is True
        
        print(f"✓ Skill '{skill_id}' installed successfully")

    def test_uninstall_skill(self):
        """Test DELETE /api/skills/install/{skill_id} uninstalls a skill"""
        skill_id = "news"
        
        # Install first
        requests.post(f"{BASE_URL}/api/skills/install/{skill_id}")
        
        # Uninstall
        response = requests.delete(f"{BASE_URL}/api/skills/install/{skill_id}")
        assert response.status_code == 200
        
        data = response.json()
        assert "uninstalled" in data
        assert data["uninstalled"] is True
        assert data["skill_id"] == skill_id
        
        # Verify uninstallation
        get_response = requests.get(f"{BASE_URL}/api/skills/marketplace")
        skills = get_response.json()["skills"]
        news_skill = [s for s in skills if s["id"] == skill_id][0]
        assert news_skill["installed"] is False
        
        print(f"✓ Skill '{skill_id}' uninstalled successfully")

    def test_install_multiple_skills(self):
        """Test installing multiple skills"""
        skill_ids = ["smart_home", "calendar"]
        
        for skill_id in skill_ids:
            response = requests.post(f"{BASE_URL}/api/skills/install/{skill_id}")
            assert response.status_code == 200
            test_installed_skill_ids.append(skill_id)
        
        # Verify all installed
        get_response = requests.get(f"{BASE_URL}/api/skills/marketplace")
        skills = get_response.json()["skills"]
        
        for skill_id in skill_ids:
            skill = [s for s in skills if s["id"] == skill_id][0]
            assert skill["installed"] is True
        
        print(f"✓ Multiple skills installed: {', '.join(skill_ids)}")


# Cleanup fixture
@pytest.fixture(scope="session", autouse=True)
def cleanup_test_data():
    """Cleanup test data after all tests complete"""
    yield
    
    print("\n🧹 Cleaning up iteration 3 test data...")
    
    # Clean up test calendar events
    for event_id in test_calendar_event_ids:
        try:
            requests.delete(f"{BASE_URL}/api/calendar/events/{event_id}")
        except:
            pass
    
    # Clean up installed skills
    for skill_id in test_installed_skill_ids:
        try:
            requests.delete(f"{BASE_URL}/api/skills/install/{skill_id}")
        except:
            pass
    
    print(f"✓ Cleaned up {len(test_calendar_event_ids)} calendar events, {len(test_installed_skill_ids)} installed skills")
