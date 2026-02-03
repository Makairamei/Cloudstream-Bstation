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

def analyze_player(player_url):
    print(f"\n[ANALYZING PLAYER] {player_url}")
    if player_url.startswith("/"):
        player_url = "https://154.26.137.28" + player_url
        
    try:
        r = requests.get(player_url, headers=headers, cookies=cookies, timeout=10)
        print(f"Status: {r.status_code}")
        
        # Look for .m3u8 or .mp4
        sources = re.findall(r'(https?://[^"\']+\.(?:m3u8|mp4))', r.text)
        if sources:
            print(f"Direct Sources Found ({len(sources)}):")
            for s in sources:
                print(f" - {s}")
        else:
            print("No direct m3u8/mp4 found in player HTML.")
            # print(r.text[:500]) # Preview if needed
            
        # Check for quality labels
        if "1080p" in r.text: print("Quality: 1080p detected")
        if "720p" in r.text: print("Quality: 720p detected")
        if "480p" in r.text: print("Quality: 480p detected")
        if "360p" in r.text: print("Quality: 360p detected")
        
    except Exception as e:
        print(f"Error accessing player: {e}")

print(f"Fetching Episode Page: {url}...")
try:
    response = requests.get(url, headers=headers, cookies=cookies, timeout=15)
    content = response.text
    
    links_found = []
    
    # 1. Base64 Scan (data-default/content)
    base64_regex = r"(?<=data-(?:content|default)=\")[^\"]+"
    matches = re.finditer(base64_regex, content)
    for match in matches:
        try:
            decoded = base64.b64decode(match.group(0)).decode('utf-8')
            src_match = re.search(r'src="([^"]+)"', decoded)
            if src_match:
                src = src_match.group(1)
                print(f"Found Link (Base64): {src}")
                links_found.append(src)
        except: pass
        
    # 2. Iframes
    soup = BeautifulSoup(content, 'html.parser')
    for iframe in soup.find_all('iframe'):
        src = iframe.get('src')
        if src:
            print(f"Found Link (Iframe): {src}")
            links_found.append(src)
            
    # 3. Mirrors
    mirrors = soup.select("ul#mir-list li, .mirror option, .mirror-item")
    for m in mirrors:
        val = m.get('value') or m.get('data-src')
        if val:
             # Try decode
             if not val.startswith("http"):
                 try: val = base64.b64decode(val).decode('utf-8')
                 except: pass
             print(f"Found Link (Mirror): {val}")
             links_found.append(val)
             
    unique_links = list(set(links_found))
    print(f"\nTotal Unique Links: {len(unique_links)}")
    
    # Analyze the first working link deep
    for link in unique_links:
        if "pomf" in link or "gideo" in link:
            analyze_player(link)
            break # Just analyze one for sample

except Exception as e:
    print(f"Error: {e}")
