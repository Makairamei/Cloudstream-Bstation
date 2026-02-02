import requests
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://www.bilibili.tv/'
}

# New premium cookies from user
cookies = {
    'SESSDATA': 'd3e2b1e9%2C1785599046%2Cbe897%2A210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'buvid3': 'f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc',
    'buvid4': '193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA%3D%3D',
    'bstar-web-lang': 'en'
}

# Test Search API structure to understand poster issue
print("Testing Search API structure...")
url = 'https://api.bilibili.tv/intl/gateway/web/v2/search_result?keyword=anime&s_locale=id_ID&limit=5'
r = requests.get(url, headers=headers, cookies=cookies)
print(f"Status: {r.status_code}")

try:
    d = r.json()
    print(f"Code: {d.get('code')}")
    modules = d.get('data', {}).get('modules', [])
    print(f"Modules: {len(modules)}")
    
    if modules:
        first_module = modules[0]
        print(f"\nFirst module keys: {list(first_module.keys())}")
        
        items = first_module.get('data', {}).get('items', [])
        if items:
            first_item = items[0]
            print(f"\nFirst item keys: {list(first_item.keys())}")
            print(f"Title: {first_item.get('title')}")
            print(f"Cover: {first_item.get('cover')}")
            print(f"Poster: {first_item.get('poster')}")
            print(f"Image: {first_item.get('image')}")
            print(f"Season ID: {first_item.get('seasonId')}")
            print(f"season_id: {first_item.get('season_id')}")
except Exception as e:
    print(f"Error: {e}")
