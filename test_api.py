import requests

headers_base = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
}

cookies = {
    'SESSDATA': 'd3e2b1e9,1785599046,be897*210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'bstar-web-lang': 'en'
}

# Get video URL first
api_url = 'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id=13436691&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
r = requests.get(api_url, headers={**headers_base, 'Referer': 'https://www.bilibili.tv/'}, cookies=cookies)
d = r.json()

video_url = d['data']['video_info']['stream_list'][0]['dash_video']['base_url']
print(f"Video URL: {video_url[:80]}...")
print()

# Test different Referers
referers = [
    'https://www.bilibili.tv/',
    'https://bilibili.tv/',
    'https://www.bstation.tv/',
    'https://bstation.tv/',
    None,  # No referer
    '',    # Empty referer
]

for ref in referers:
    print(f"Testing Referer: {ref!r}")
    hdrs = headers_base.copy()
    if ref is not None:
        hdrs['Referer'] = ref
    try:
        r2 = requests.head(video_url, headers=hdrs, timeout=5)
        print(f"  Status: {r2.status_code}")
    except Exception as e:
        print(f"  Error: {e}")
