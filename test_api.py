import requests
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/',
}

cookies = {
    'SESSDATA': 'a97adc61%2C1785509852%2Cdd028%2A210091',
    'bili_jct': 'bca5203c3f1cda514530500a8ca0fc10',
    'DedeUserID': '1709563281',
    'bstar-web-lang': 'en'
}

# Get playurl for anime 2289171 episode 1
api_url = 'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id=24657572&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
r = requests.get(api_url, headers=headers, cookies=cookies)
d = r.json()

print("Full response structure:")
data = d.get('data', {})

# Check for DURL (muxed video+audio)
durl = data.get('durl', [])
print(f"DURL (muxed): {len(durl)} streams")

# Check video_info
video_info = data.get('video_info', {})
print(f"\nvideo_info keys: {list(video_info.keys())}")

# Check stream_list (DASH video only)
stream_list = video_info.get('stream_list', [])
print(f"stream_list: {len(stream_list)} streams")

if stream_list:
    print("\nFirst stream details:")
    first = stream_list[0]
    print(f"  stream_info: {first.get('stream_info', {}).get('display_desc')}")
    dash_video = first.get('dash_video', {})
    print(f"  dash_video.base_url exists: {bool(dash_video.get('base_url'))}")

# Check dash_audio (separate audio for DASH)
dash_audio = video_info.get('dash_audio', [])
print(f"\ndash_audio: {len(dash_audio)} tracks")
if dash_audio:
    print(f"  First audio URL exists: {bool(dash_audio[0].get('base_url'))}")
    print(f"  Audio URL: {dash_audio[0].get('base_url', '')[:80]}...")
else:
    print("  NO AUDIO TRACKS! This may be pre-release or needs fallback API")

# Compare with working anime (Kaisar Abadi ep 1)
print("\n" + "="*60)
print("Comparing with working anime (Kaisar Abadi ep 1):")
api_url2 = 'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id=13765998&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
r2 = requests.get(api_url2, headers=headers, cookies=cookies)
d2 = r2.json()
data2 = d2.get('data', {})
video_info2 = data2.get('video_info', {})
dash_audio2 = video_info2.get('dash_audio', [])
durl2 = data2.get('durl', [])
print(f"DURL: {len(durl2)}, DASH Audio: {len(dash_audio2)}")
