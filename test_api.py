import requests
from urllib.parse import urlparse, parse_qs

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/',
    'Origin': 'https://www.bilibili.tv'
}

cookies = {
    'SESSDATA': 'd3e2b1e9,1785599046,be897*210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'DedeUserID__ckMd5': '4568e91a427e5c0dd0403fdd96efae6f',
    'mid': '1709563281',
    'buvid3': 'f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc',
    'buvid4': '193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA==',
    'joy_jct': 'c354fd55e047c9b7daddc250b5004972',
    'bstar-web-lang': 'id',
    'bsource': 'search_google'
}

def get_video_url(ep_id, name):
    play_url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={ep_id}&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
    r = requests.get(play_url, headers=headers, cookies=cookies)
    d = r.json()
    
    streams = d.get('data', {}).get('video_info', {}).get('stream_list', [])
    if streams:
        return streams[0].get('dash_video', {}).get('base_url', '')
    return None

print("COMPARING VIDEO URLs FROM DIFFERENT ANIME")
print("="*70)

# Working anime: Kaisar Abadi ep 16
url1 = get_video_url('13768739', 'Kaisar Abadi ep 16')
print(f"\n1. KAISAR ABADI EP 16 (WORKS):")
print(f"   URL: {url1[:80] if url1 else 'NONE'}...")

# Failing anime: Penggembala Dewa ep 14
url2 = get_video_url('13437679', 'Penggembala Dewa ep 14')
print(f"\n2. PENGGEMBALA DEWA EP 14 (FAILS):")
print(f"   URL: {url2[:80] if url2 else 'NONE'}...")

# Compare URL structure
print("\n" + "="*70)
print("URL STRUCTURE COMPARISON:")
print("="*70)

if url1 and url2:
    p1 = urlparse(url1)
    p2 = urlparse(url2)
    
    print(f"\n1. Kaisar Abadi:")
    print(f"   Host: {p1.netloc}")
    q1 = parse_qs(p1.query)
    print(f"   Has 'mid' param: {'mid' in q1}")
    print(f"   mid value: {q1.get('mid', ['N/A'])[0]}")
    
    print(f"\n2. Penggembala Dewa:")
    print(f"   Host: {p2.netloc}")
    q2 = parse_qs(p2.query)
    print(f"   Has 'mid' param: {'mid' in q2}")
    print(f"   mid value: {q2.get('mid', ['N/A'])[0]}")

# Test different referers for Penggembala Dewa
print("\n" + "="*70)
print("TESTING DIFFERENT REFERERS FOR PENGGEMBALA DEWA:")
print("="*70)

referers = [
    'https://www.bilibili.tv/',
    'https://www.bilibili.tv/id/play/2117053',
    'https://www.bstation.tv/',
    'https://bilibili.tv/',
    None,
]

for ref in referers:
    hdrs = {'User-Agent': headers['User-Agent']}
    if ref:
        hdrs['Referer'] = ref
    r = requests.head(url2, headers=hdrs, timeout=5)
    print(f"  Referer '{ref}': {r.status_code}")

# Test with full headers including Origin
print("\n  With Origin header:")
hdrs = {
    'User-Agent': headers['User-Agent'],
    'Referer': 'https://www.bilibili.tv/',
    'Origin': 'https://www.bilibili.tv'
}
r = requests.head(url2, headers=hdrs, timeout=5)
print(f"  With Origin: {r.status_code}")

# Test actual GET request
print("\n  Try GET with Range header:")
hdrs['Range'] = 'bytes=0-1024'
r = requests.get(url2, headers=hdrs, timeout=5)
print(f"  GET status: {r.status_code}")
