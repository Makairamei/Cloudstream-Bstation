import requests
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/',
    'Origin': 'https://www.bilibili.tv'
}

# NEW cookies from user
cookies = {
    'SESSDATA': 'd3e2b1e9%2C1785599046%2Cbe897%2A210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'buvid3': 'f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc',
    'buvid4': '193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA%3D%3D',
    'bstar-web-lang': 'en'
}

ep_id = '24739195'
url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={ep_id}&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
r = requests.get(url, headers=headers, cookies=cookies)

d = r.json()
data = d.get('data', {})
video_info = data.get('video_info', {})
stream_list = video_info.get('stream_list', [])

print(f"Code: {d.get('code')}")
print(f"Number of streams: {len(stream_list)}")
print()

for i, stream in enumerate(stream_list[:3]):
    print(f"Stream {i+1}:")
    stream_info = stream.get('stream_info', {})
    dash_video = stream.get('dash_video', {})
    print(f"  Quality: {stream_info.get('display_desc')}")
    print(f"  base_url exists: {bool(stream.get('base_url'))}")
    print(f"  dash_video base_url exists: {bool(dash_video.get('base_url'))}")
    if dash_video.get('base_url'):
        print(f"  URL preview: {dash_video.get('base_url')[:80]}...")
    print()

# Check dash_audio
dash_audio = video_info.get('dash_audio', [])
print(f"Audio tracks: {len(dash_audio)}")
if dash_audio:
    print(f"  Audio URL exists: {bool(dash_audio[0].get('base_url'))}")
