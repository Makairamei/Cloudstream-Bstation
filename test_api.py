import requests
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/'
}
cookies = {
    'SESSDATA': 'a97adc61%2C1785509852%2Cdd028%2A210091',
    'bstar-web-lang': 'id'
}

# APIs to test - from various Bstation extensions
endpoints = [
    ('Timeline', 'https://api.bilibili.tv/intl/gateway/web/v2/ogv/timeline?s_locale=id_ID&platform=web'),
    ('Search anime', 'https://api.bilibili.tv/intl/gateway/web/v2/search_result?keyword=anime&s_locale=id_ID&limit=20'),
    ('Filter all', 'https://api.bilibili.tv/intl/gateway/web/v2/ogv/filter/index?s_locale=id_ID&platform=web'),
    ('Index conditions', 'https://api.bilibili.tv/intl/gateway/web/v2/ogv/index/conditions?s_locale=id_ID&platform=web'),
    ('Season ranking', 'https://api.bilibili.tv/intl/gateway/web/v2/ogv/ranking/season?s_locale=id_ID'),
    ('Season tabs', 'https://api.bilibili.tv/intl/gateway/web/v2/ogv/season/tabs?s_locale=id_ID'),
]

for name, url in endpoints:
    try:
        r = requests.get(url, headers=headers, cookies=cookies)
        d = r.json()
        code = d.get('code')
        data = d.get('data', {})
        if code == 0:
            print(f"SUCCESS: {name}")
            if isinstance(data, dict):
                keys = list(data.keys())[:5]
                print(f"  Keys: {keys}")
                # Try to find items
                if 'items' in data:
                    print(f"  Items: {len(data['items'])} items")
                elif 'list' in data:
                    print(f"  List: {len(data['list'])} items")
                elif 'modules' in data:
                    print(f"  Modules: {len(data['modules'])} modules")
            elif isinstance(data, list):
                print(f"  Array with {len(data)} items")
        else:
            print(f"FAIL: {name} - code={code}")
    except Exception as e:
        print(f"ERROR: {name} - {e}")
    print()
