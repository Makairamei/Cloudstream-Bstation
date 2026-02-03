import requests

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/',
    'Origin': 'https://www.bilibili.tv'
}

cookies = {
    'SESSDATA': 'd3e2b1e9,1785599046,be897*210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'buvid3': 'f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc',
    'buvid4': '193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA==',
    'bstar-web-lang': 'en'
}

# Test episodes
# 1. Free episode - Kaisar Abadi ep 1
# 2. VIP episode - should work with VIP cookies
# 3. VIP episode that user tested (Kaisar Abadi ep 16)

episodes = [
    ('13765998', 'Kaisar Abadi Ep 1 (FREE)'),
    ('13768739', 'Kaisar Abadi Ep 16 (VIP)'),
    ('24739195', 'Jujutsu Kaisen Ep 48 (VIP)'),
]

print("="*60)
print("COMPARING FREE vs VIP VIDEO URL ACCESS")
print("="*60)

for ep_id, name in episodes:
    print(f"\n--- {name} ---")
    
    url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={ep_id}&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
    r = requests.get(url, headers=headers, cookies=cookies)
    d = r.json()
    
    data = d.get('data', {})
    video_info = data.get('video_info', {})
    streams = video_info.get('stream_list', [])
    
    if streams:
        video_url = streams[0].get('dash_video', {}).get('base_url', '')
        if video_url:
            print(f"  Video URL found")
            print(f"  URL starts with: {video_url[:50]}...")
            
            # Test different referers
            referers = [
                'https://www.bilibili.tv/',
                'https://www.bilibili.com/',
                None,  # No referer
            ]
            
            for ref in referers:
                hdrs = {'User-Agent': headers['User-Agent']}
                if ref:
                    hdrs['Referer'] = ref
                r2 = requests.head(video_url, headers=hdrs, timeout=5)
                print(f"  Referer '{ref}': {r2.status_code}")
        else:
            print("  Video URL is EMPTY!")
    else:
        print("  No streams returned!")

print("\n" + "="*60)
print("CHECKING IF VIP VIDEO URL HAS EXPIRATION OR TOKEN")
print("="*60)

# Get fresh video URL and check its format
ep_id = '13768739'  # Kaisar Abadi ep 16
url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={ep_id}&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
r = requests.get(url, headers=headers, cookies=cookies)
d = r.json()

video_url = d['data']['video_info']['stream_list'][0]['dash_video']['base_url']
print(f"\nFull Video URL:\n{video_url}")

# Parse URL to check for tokens/expiration
from urllib.parse import urlparse, parse_qs
parsed = urlparse(video_url)
print(f"\nHost: {parsed.netloc}")
print(f"Path: {parsed.path}")
print(f"Query params: {parse_qs(parsed.query)}")
