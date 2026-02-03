import requests
from bs4 import BeautifulSoup

url = "https://154.26.137.28/bai-lian-cheng-shen-3-episode-10/"

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://154.26.137.28/",
    "Origin": "https://154.26.137.28"
}

cookies = {
    "_as_turnstile": "7ca75f267f4baab5b0a7634d930503312176f854567a9d870f3c371af04eefe4",
    "_as_ipin_ct": "ID",
    "_as_ipin_tz": "Asia/Jakarta",
    "_as_ipin_lc": "en-US"
}

try:
    print("Fetching page content...")
    response = requests.get(url, headers=headers, cookies=cookies, timeout=15)
    
    print(f"TITLE: {BeautifulSoup(response.text, 'html.parser').title.string}")
    
    print("\n--- HTML PREVIEW (First 2000 chars) ---")
    print(response.text[:2000])
    
    print("\n--- SEARCHING FOR 'PLAYER' or 'VIDEO' ---")
    if "player" in response.text.lower():
        print("Found context 'player':")
        start = response.text.lower().find("player")
        print(response.text[start:start+500])
        
    if "iframe" in response.text.lower():
        print("Found context 'iframe':")
        start = response.text.lower().find("iframe")
        print(response.text[start:start+500])

except Exception as e:
    print(f"Error: {e}")
