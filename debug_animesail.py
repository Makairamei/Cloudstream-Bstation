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
    print(f"Status Code: {response.status_code}")
    
    if response.status_code != 200:
        print("Failed to load page. Cloudflare might be blocking.")
        print(response.text[:500])
        exit()

    soup = BeautifulSoup(response.text, 'html.parser')
    
    print("\n--- 1. IFRAMES ---")
    iframes = soup.find_all('iframe')
    for iframe in iframes:
        src = iframe.get('src')
        if src:
            print(f"Found Iframe: {src}")
            
    print("\n--- 2. MIRROR LIST ---")
    # Check common mirror structures
    mirrors = soup.select("ul#mir-list li, .mirror option, .mirror-item")
    for mirror in mirrors:
        val = mirror.get('value') or mirror.get('data-src')
        if val:
            print(f"Found Mirror raw: {val}")
            # Try decode if base64
            if not val.startswith("http"):
                try:
                    decoded = base64.b64decode(val).decode('utf-8')
                    print(f"  -> Decoded: {decoded}")
                except:
                    pass

    print("\n--- 3. BASE64 CONTENT ---")
    # Check for encoded content often used in older themes
    content = response.text
    # Updated regex to match data-content OR data-default
    base64_regex = r"(?<=data-(?:content|default)=\")[^\"]+"
    matches = re.finditer(base64_regex, content)
    for match in matches:
        val = match.group(0)
        try:
            decoded = base64.b64decode(val).decode('utf-8')
            print(f"Found Encoded Content: {decoded[:100]}...")
            # Look for src inside
            src_match = re.search(r'src="([^"]+)"', decoded)
            if src_match:
                print(f"  -> Extracted Src: {src_match.group(1)}")
        except:
            pass

except Exception as e:
    print(f"Error: {e}")
