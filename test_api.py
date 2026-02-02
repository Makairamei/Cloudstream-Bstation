import requests
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.bilibili.tv/',
    'Origin': 'https://www.bilibili.tv'
}

cookies = {
    'SESSDATA': 'd3e2b1e9,1785599046,be897*210091',
    'bili_jct': 'c354fd55e047c9b7daddc250b5004972',
    'DedeUserID': '1709563281',
    'bstar-web-lang': 'en'
}

season_id = '2129061'

# Get season info with all episodes
season_url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/view/app/season?season_id={season_id}&platform=web&s_locale=id_ID'
print(f"Fetching season {season_id}...")

r = requests.get(season_url, headers=headers, cookies=cookies)
d = r.json()

print(f"Code: {d.get('code')}")

if d.get('code') == 0:
    result = d.get('result', {})
    print(f"Title: {result.get('title')}")
    
    # Check direct episodes
    episodes = result.get('episodes', [])
    print(f"\nDirect episodes: {len(episodes)}")
    
    # Check modules
    modules = result.get('modules', [])
    print(f"Modules: {len(modules)}")
    
    for i, mod in enumerate(modules):
        mod_data = mod.get('data', {})
        mod_eps = mod_data.get('episodes', [])
        print(f"\n  Module {i+1}: {len(mod_eps)} episodes")
        
        for ep in mod_eps[:3]:
            ep_id = ep.get('id')
            index = ep.get('index_show', '?')
            title = ep.get('title', '')
            print(f"    EP {index}: ID={ep_id}, Title={title}")
        
        if len(mod_eps) > 3:
            print(f"    ... ({len(mod_eps) - 3} more)")
            # Show last episode
            last_ep = mod_eps[-1]
            print(f"    EP {last_ep.get('index_show')}: ID={last_ep.get('id')}, Title={last_ep.get('title')}")
    
    # Test VIP episode (ep 16)
    if modules:
        last_mod_eps = modules[-1].get('data', {}).get('episodes', [])
        if last_mod_eps:
            vip_ep = last_mod_eps[-1]  # Last episode
            vip_ep_id = vip_ep.get('id')
            print(f"\n\nTesting VIP episode: {vip_ep.get('index_show')} (ID: {vip_ep_id})")
            
            # Test playurl
            play_url = f'https://api.bilibili.tv/intl/gateway/v2/ogv/playurl?ep_id={vip_ep_id}&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID'
            r2 = requests.get(play_url, headers=headers, cookies=cookies)
            d2 = r2.json()
            
            print(f"Playurl Code: {d2.get('code')}")
            print(f"Message: {d2.get('message')}")
            
            data = d2.get('data', {})
            video_info = data.get('video_info', {})
            stream_list = video_info.get('stream_list', [])
            print(f"Streams: {len(stream_list)}")
            
            if stream_list:
                first_stream = stream_list[0].get('dash_video', {}).get('base_url', '')
                if first_stream:
                    print(f"Video URL (first 80 chars): {first_stream[:80]}")
else:
    print(f"Failed: {d}")
