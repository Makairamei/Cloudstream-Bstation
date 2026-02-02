"""
Test the BILIINTL API endpoint for HLS streams
"""
import requests
import json

cookies = {
    "SESSDATA": "77adc14d%2C1784135329%2C49214%2A110091",
    "bili_jct": "b9cd1b814e7484becba8917728142c21",
    "DedeUserID": "1709563281",
    "bstar-web-lang": "id"
}

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
    "Referer": "https://www.bilibili.tv/",
    "Origin": "https://www.bilibili.tv"
}

ep_id = "13261950"

print("=" * 60)
print("Testing BILIINTL.COM API Endpoint")
print("=" * 60)

# Test the biliintl.com endpoint
urls = [
    f"https://api.biliintl.com/intl/gateway/web/playurl?ep_id={ep_id}&s_locale=id_ID&device=wap&platform=web&qn=64&tf=0&type=0",
    f"https://api.biliintl.com/intl/gateway/web/playurl?ep_id={ep_id}&s_locale=id_ID&platform=web&qn=64",
    f"https://api.biliintl.com/intl/gateway/web/playurl?ep_id={ep_id}&s_locale=id_ID&platform=android&qn=64",
]

for url in urls:
    print(f"\n--- Testing: {url[:80]}... ---")
    
    try:
        resp = requests.get(url, cookies=cookies, headers=headers, timeout=10)
        data = resp.json()
        
        code = data.get('code')
        msg = data.get('message', 'N/A')
        print(f"Code: {code}, Message: {msg}")
        
        if code == 0:
            result = data.get('data', {})
            
            # Check playurl field
            playurl = result.get('playurl', {})
            print(f"Playurl keys: {playurl.keys() if playurl else 'None'}")
            
            # Check for video list
            video = playurl.get('video', [])
            audio = playurl.get('audio_resource', [])
            print(f"Video streams: {len(video)}, Audio streams: {len(audio)}")
            
            # Check for HLS/m3u8
            if video:
                for v in video[:2]:
                    url_str = v.get('video_resource', {}).get('url', '')
                    print(f"  Video URL: {url_str[:100]}...")
                    if '.m3u8' in url_str:
                        print("  *** THIS IS M3U8/HLS! ***")
            
            # Print raw for debugging
            raw = json.dumps(result, ensure_ascii=False)
            if '.m3u8' in raw:
                print("*** FOUND .m3u8 IN RESPONSE ***")
            
            # Save full response
            with open("biliintl_response.json", "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            print("Full response saved to biliintl_response.json")
            
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "=" * 60)
print("Done")
