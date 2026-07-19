import json
import logging
from typing import Optional, Dict, Any, List
import requests

logger = logging.getLogger(__name__)

class SupabaseAuthError(Exception):
    pass

class SupabaseRequestError(Exception):
    pass

class SupabaseClient:
    URL = "https://tuqrlggkahmsoqfppuyh.supabase.co"
    ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InR1cXJsZ2drYWhtc29xZnBwdXloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQzOTUwOTAsImV4cCI6MjA5OTk3MTA5MH0.1qy4pwEmFX94DTXLMHKMJts0ZwjtH1UKl2Kc9XzTdwE"

    def __init__(self):
        self._jwt: Optional[str] = None
        self._user_id: Optional[str] = None

    def login(self, email: str, password: str) -> None:
        url = f"{self.URL}/auth/v1/token?grant_type=password"
        headers = {
            "apikey": self.ANON_KEY,
            "Content-Type": "application/json"
        }
        data = {
            "email": email,
            "password": password
        }
        response = requests.post(url, headers=headers, json=data)
        if not response.ok:
            try:
                err_msg = response.json().get("error_description", response.text)
            except Exception:
                err_msg = response.text
            raise SupabaseAuthError(f"Login failed: {err_msg}")
        
        resp_data = response.json()
        self._jwt = resp_data.get("access_token")
        self._user_id = resp_data.get("user", {}).get("id")

    @property
    def is_authenticated(self) -> bool:
        return self._jwt is not None

    def _get_auth_headers(self) -> Dict[str, str]:
        if not self._jwt:
            raise SupabaseAuthError("Not authenticated")
        return {
            "apikey": self.ANON_KEY,
            "Authorization": f"Bearer {self._jwt}"
        }

    def fetch_missions(self) -> List[Dict[str, Any]]:
        url = f"{self.URL}/rest/v1/missions?select=id,version"
        headers = {
            "apikey": self.ANON_KEY,
            "Authorization": f"Bearer {self.ANON_KEY}"
        }
        # If logged in, we can use user JWT, but public read is fine with ANON_KEY
        if self._jwt:
            headers["Authorization"] = f"Bearer {self._jwt}"

        response = requests.get(url, headers=headers)
        if not response.ok:
            raise SupabaseRequestError(f"Failed to fetch missions: {response.text}")
        return response.json()

    def fetch_mission_detail(self, mission_id: str) -> Dict[str, Any]:
        url = f"{self.URL}/rest/v1/missions?id=eq.{mission_id}&select=id,version,json_data"
        headers = {
            "apikey": self.ANON_KEY,
            "Authorization": f"Bearer {self.ANON_KEY}"
        }
        if self._jwt:
            headers["Authorization"] = f"Bearer {self._jwt}"

        response = requests.get(url, headers=headers)
        if not response.ok:
            raise SupabaseRequestError(f"Failed to fetch mission {mission_id}: {response.text}")
        
        data = response.json()
        if not data:
            raise SupabaseRequestError(f"Mission '{mission_id}' not found.")
        return data[0]

    def download_image(self, mission_id: str, image_name: str, target_path: str) -> None:
        url = f"{self.URL}/storage/v1/object/public/mission-images/{mission_id}/{image_name}"
        headers = {
            "apikey": self.ANON_KEY,
            "Authorization": f"Bearer {self.ANON_KEY}"
        }
        if self._jwt:
            headers["Authorization"] = f"Bearer {self._jwt}"

        response = requests.get(url, headers=headers, stream=True)
        if not response.ok:
            raise SupabaseRequestError(f"Failed to download image {image_name}: {response.text}")

        with open(target_path, "wb") as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)

    def upload_image(self, mission_id: str, image_name: str, file_path: str) -> None:
        url = f"{self.URL}/storage/v1/object/mission-images/{mission_id}/{image_name}"
        headers = self._get_auth_headers()
        
        # Check if exists to decide between POST and PUT? Supabase storage upsert?
        # Supabase storage API uses POST for new, PUT for replace, or we can use POST with upsert header
        # Let's use POST with x-upsert header
        headers["x-upsert"] = "true"
        
        # Determine content type simply
        content_type = "image/png" if file_path.lower().endswith(".png") else "image/jpeg"
        headers["Content-Type"] = content_type

        with open(file_path, "rb") as f:
            response = requests.post(url, headers=headers, data=f)

        if not response.ok:
            raise SupabaseRequestError(f"Failed to upload image: {response.text}")

    def publish_mission(self, mission_id: str, calibration_json: str, image_hash: Optional[str] = None) -> None:
        check_url = f"{self.URL}/rest/v1/missions?id=eq.{mission_id}&select=id,version"
        headers = self._get_auth_headers()
        
        resp = requests.get(check_url, headers=headers)
        if not resp.ok:
            raise SupabaseRequestError(f"Failed to check mission existence ({check_url}): {resp.text}")
            
        data = resp.json()
        
        json_data_dict = json.loads(calibration_json)
        if image_hash:
            json_data_dict["image_hash"] = image_hash

        payload = {
            "json_data": json_data_dict
        }
        if self._user_id:
            payload["owner_id"] = self._user_id

        headers["Content-Type"] = "application/json"
        headers["Prefer"] = "return=representation"

        if data:
            current_version = data[0].get("version", 0)
            payload["version"] = current_version + 1
            
            patch_url = f"{self.URL}/rest/v1/missions?id=eq.{mission_id}"
            
            response = requests.patch(patch_url, headers=headers, json=payload)
            if not response.ok:
                raise SupabaseRequestError(f"Failed to update existing mission ({patch_url}): {response.text}")
            
            if not response.json():
                raise SupabaseRequestError("You do not have permission to overwrite this mission.")
        else:
            payload["id"] = mission_id
            payload["version"] = 1
            
            post_url = f"{self.URL}/rest/v1/missions"
            
            response = requests.post(post_url, headers=headers, json=payload)
            if not response.ok:
                raise SupabaseRequestError(f"Failed to publish new mission ({post_url}): {response.text}")
