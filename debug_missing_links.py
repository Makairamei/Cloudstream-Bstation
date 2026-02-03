import requests
from bs4 import BeautifulSoup
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
    content = response.text
    
    # 1. Search for "1080p" occurrences
    print("\n--- SEARCHING FOR '1080p' ---")
    indices = [m.start() for m in re.finditer('1080p', content)]
    print(f"Found {len(indices)} occurrences of '1080p'")
    
    for i in indices:
        snippet = content[max(0, i-200):min(len(content), i+300)]
        print(f"\nContext around {i}:")
        print(snippet.replace("\n", " ").replace("\r", ""))
        
    # 2. Check for Download Section specifically
    soup = BeautifulSoup(content, 'html.parser')
    download_section = soup.select('.download-list, .dl-box, #download')
    if download_section:
        print(f"\n--- DOWNLOAD SECTION DETECTED ---")
        for sec in download_section:
            print(sec.prettify()[:500])
            
    # 3. Check for specific hosts mentioned by user (implied)
    hosts = ['gdrive', 'zippyshare', 'mediafire', 'terabox', 'uploade']
    for host in hosts:
        if host in content.lower():
            print(f"Found host keyword: {host}")

except Exception as e:
    print(f"Error: {e}")
