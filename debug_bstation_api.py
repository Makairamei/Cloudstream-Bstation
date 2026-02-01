"""
Debug Bilibili TV API - Full structure dump
"""
import requests
import json

cookies = {
    "SESSDATA": "77adc14d%2C1784135329%2C49214%2A110091",
    "bili_jct": "b9cd1b814e7484becba8917728142c21",
    "DedeUserID": "1709563281",
    "buvid3": "1d09ce3a-0767-40d7-b74a-cb7be2294d8064620infoc",
    "buvid4": "EDD5D20E-2881-5FC4-ACF3-38407A33613880170-026011701-uQai4h5eTsQ9YIdcmk0IhA%3D%3D",
    "bstar-web-lang": "id"
}

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.tv/",
    "Origin": "https://www.bilibili.tv"
}

ep_id = "13261950"

url = f"https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={ep_id}&platform=web&qn=64&s_locale=id_ID"

resp = requests.get(url, cookies=cookies, headers=headers, timeout=10)
data = resp.json()

# Save full response to file
with open("debug_response.json", "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print("Full response saved to debug_response.json")
print(f"Code: {data.get('code')}")

result = data.get('result') or data.get('data') or {}

# Check video_info structure
video_info = result.get('video_info', {})
print(f"\nvideo_info keys: {video_info.keys()}")

# Check if there's audio in video_info
dash_audio = video_info.get('dash_audio', [])
print(f"video_info.dash_audio: {len(dash_audio)} items")

# Check first stream structure
stream_list = video_info.get('stream_list', [])
if stream_list:
    first = stream_list[0]
    print(f"\nFirst stream structure:")
    print(f"  Keys: {first.keys()}")
    
    # Check stream_info
    si = first.get('stream_info', {})
    print(f"  stream_info: {si}")
    
    # Check dash_video
    dv = first.get('dash_video', {})
    print(f"  dash_video keys: {dv.keys()}")
    print(f"  dash_video.base_url: {dv.get('base_url', 'N/A')[:100]}...")

# Print dash_audio details if exists
if dash_audio:
    print(f"\nDash Audio Details:")
    for i, a in enumerate(dash_audio):
        print(f"  [{i}] base_url: {a.get('base_url', 'N/A')[:100]}...")

# Also check result keys
print(f"\nResult top-level keys: {result.keys()}")
