import http.server
import socketserver
import threading
import time
import json
import os
import random
import webbrowser

PORT = 8080
WEB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app1_admin", "web_dashboard")

# In-memory mock database for instant offline testing without even needing Firebase setup first!
mock_db = {
    "devices": {
        "target_phone_01": {
            "info": {
                "id": "target_phone_01",
                "brand": "Samsung",
                "model": "Galaxy S23 Ultra",
                "androidVersion": "Android 14 (API 34)",
                "isOnline": True
            },
            "status": {
                "battery": 82,
                "isCharging": True,
                "network": "Wi-Fi (5 GHz)",
                "storageFree": "64.5 GB",
                "storageTotal": "256 GB",
                "lastSeen": int(time.time() * 1000)
            },
            "location": {
                "latitude": 28.6139,
                "longitude": 77.2090,
                "accuracy": 8.0,
                "timestamp": int(time.time() * 1000)
            },
            "commands": {
                "ring": False,
                "alertMessage": "",
                "forceSync": False
            }
        },
        "target_phone_02": {
            "info": {
                "id": "target_phone_02",
                "brand": "Xiaomi",
                "model": "Redmi Note 13 Pro",
                "androidVersion": "Android 13 (API 33)",
                "isOnline": True
            },
            "status": {
                "battery": 46,
                "isCharging": False,
                "network": "Mobile Data (5G)",
                "storageFree": "28.1 GB",
                "storageTotal": "128 GB",
                "lastSeen": int(time.time() * 1000)
            },
            "location": {
                "latitude": 19.0760,
                "longitude": 72.8777,
                "accuracy": 12.0,
                "timestamp": int(time.time() * 1000)
            },
            "commands": {
                "ring": False,
                "alertMessage": "",
                "forceSync": False
            }
        }
    }
}

class MockServerHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=WEB_DIR, **kwargs)

    def do_GET(self):
        # Handle Firebase-like REST endpoint for testing
        if self.path.startswith("/devices.json"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(mock_db["devices"]).encode("utf-8"))
            return
        super().do_GET()

    def do_PUT(self):
        # Handle command triggers (ring, alert, etc.)
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        try:
            val = json.loads(body)
        except:
            val = body

        if "/commands/ring.json" in self.path:
            dev_id = self.path.split("/devices/")[1].split("/commands")[0]
            if dev_id in mock_db["devices"]:
                mock_db["devices"][dev_id]["commands"]["ring"] = bool(val)
                print(f"[ACTION] 🚨 Ring Siren command toggled to: {val} for device: {dev_id}")

        elif "/commands/alertMessage.json" in self.path:
            dev_id = self.path.split("/devices/")[1].split("/commands")[0]
            if dev_id in mock_db["devices"]:
                mock_db["devices"][dev_id]["commands"]["alertMessage"] = str(val)
                print(f"[ACTION] 📢 Alert message sent to {dev_id}: '{val}'")

        elif "/commands/forceSync.json" in self.path:
            dev_id = self.path.split("/devices/")[1].split("/commands")[0]
            print(f"[ACTION] 🔄 Force sync requested for {dev_id}!")

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-HTTP-Method-Override")
        self.end_headers()

def background_device_simulator():
    """Simulates real target devices moving slightly and updating battery every 6 seconds"""
    while True:
        time.sleep(6)
        now = int(time.time() * 1000)
        for dev_id, dev_data in mock_db["devices"].items():
            dev_data["status"]["lastSeen"] = now
            # Small random walk in GPS coordinates to simulate live movement
            dev_data["location"]["latitude"] += (random.random() - 0.5) * 0.0004
            dev_data["location"]["longitude"] += (random.random() - 0.5) * 0.0004
            dev_data["location"]["timestamp"] = now

            # If ringing, log simulated sound
            if dev_data["commands"]["ring"]:
                print(f"[SIMULATED PHONE {dev_id}] 🔔 BEEP BEEP BEEP! Loud Siren Alarm is sounding!")

if __name__ == "__main__":
    t = threading.Thread(target=background_device_simulator, daemon=True)
    t.start()

    print(f"=======================================================")
    print(f"🚀 Admin Dashboard & Simulator Running at: http://localhost:{PORT}")
    print(f"📱 Simulating 2 active target phones (App 2) with Live GPS & Battery")
    print(f"=======================================================")

    with socketserver.TCPServer(("", PORT), MockServerHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nStopping server...")
