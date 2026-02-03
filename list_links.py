import requests
from bs4 import BeautifulSoup
import base64
import re

url = "https://154.26.137.28/jigokuraku-2nd-season-episode-4/"

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

print(f"Fetching {url}...")
try:
    response = requests.get(url, headers=headers, cookies=cookies, timeout=15)
    soup = BeautifulSoup(response.text, 'html.parser')
    
    print("\n[EXTRACTED LINKS]")
    print("-" * 60)
    
    # Check data-em, data-content, data-default
    elements = soup.select("[data-content], [data-default], [data-em]")
    
    found_count = 0
    for el in elements:
        # Get base64 string
        b64 = el.get('data-content') or el.get('data-default') or el.get('data-em')
        label = el.get_text(strip=True)
        
        if b64:
            try:
                decoded = base64.b64decode(b64).decode('utf-8')
                src_match = re.search(r'src="([^"]+)"', decoded)
                if src_match:
                    src = src_match.group(1)
                    if src.startswith("//"): src = "https:" + src
                    # Fix relative paths
                    if src.startswith("/"): src = "https://154.26.137.28" + src
                    
                    found_count += 1
                    print(f"[{found_count}] {label}")
                    print(f"    URL: {src}")
            except Exception as e:
                pass
                
    print("-" * 60)
    print(f"Total links found: {found_count}")

except Exception as e:
    print(f"Error: {e}")
