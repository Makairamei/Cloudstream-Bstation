import requests

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/',
    'Origin': 'https://www.bilibili.tv'
}

# Fresh cookies from browser export (decoded values)
fresh_cookies = {
    'SESSDATA': 'd3e2b1e9,1785599046,be897*210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'DedeUserID__ckMd5': '4568e91a427e5c0dd0403fdd96efae6f',
    'mid': '1709563281',
    'buvid3': 'f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc',
    'buvid4': '193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA==',
    'bstar-web-lang': 'id',
    'bsource': 'search_google',
    'joy_jct': 'c354fd55e047c9b7daddc250b5004972'
}

# Test VIP episode
vip_ep_id = '13768739'  # Kaisar Abadi ep 16

print("Testing with FRESH cookies from browser export:")
print("="*60)

url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={vip_ep_id}&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
r = requests.get(url, headers=headers, cookies=fresh_cookies)
d = r.json()

print(f"Code: {d.get('code')}")
print(f"Message: {d.get('message')}")

data = d.get('data', {})
video_info = data.get('video_info', {})
streams = video_info.get('stream_list', [])

print(f"DASH Streams: {len(streams)}")

if streams:
    video_url = streams[0].get('dash_video', {}).get('base_url', '')
    print(f"Has Video URL: {bool(video_url)}")
    
    if video_url:
        # Check if URL contains user ID (means authenticated)
        if 'mid=1709563281' in video_url:
            print("[OK] USER ID in URL - Authentication SUCCESS!")
        else:
            print("[WARN] No user ID in URL - might be guest access")
        
        # Test URL accessibility
        r2 = requests.head(video_url, headers={'Referer': 'https://www.bilibili.tv/'}, timeout=5)
        print(f"Video URL Access: {r2.status_code}")
        
        # Print first 100 chars of URL
        print(f"URL sample: {video_url[:100]}...")
else:
    print("No streams returned!")
    
# Compare with guest access
print("\n" + "="*60)
print("Compare with GUEST access (no cookies):")
r3 = requests.get(url, headers=headers)
d3 = r3.json()
guest_streams = d3.get('data', {}).get('video_info', {}).get('stream_list', [])
print(f"DASH Streams: {len(guest_streams)}")
if guest_streams:
    guest_url = guest_streams[0].get('dash_video', {}).get('base_url', '')
    print(f"Has Video URL: {bool(guest_url)}")
    if guest_url:
        print(f"Guest URL sample: {guest_url[:100]}...")
