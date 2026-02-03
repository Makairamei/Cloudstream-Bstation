"""Debug AnimeSail video sources"""
import requests
from bs4 import BeautifulSoup
import base64
import re

# Session with proper headers
session = requests.Session()
session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
})
session.cookies.set('_as_ipin_ct', 'ID')

BASE_URL = "https://154.26.137.28"

def debug_homepage():
    """Debug homepage structure"""
    print("\n" + "="*60)
    print("DEBUGGING HOMEPAGE")
    print("="*60)
    
    resp = session.get(BASE_URL, verify=False)
    soup = BeautifulSoup(resp.text, 'html.parser')
    
    print(f"Status: {resp.status_code}")
    print(f"Title: {soup.title.text if soup.title else 'No title'}")
    
    # Check different selectors
    selectors = [
        'article',
        '.listupd article',
        '.bixbox article',
        'div.excstf article',
        '.post-body article',
    ]
    
    for sel in selectors:
        items = soup.select(sel)
        if items:
            print(f"\nFound {len(items)} items with selector: {sel}")
            for item in items[:2]:
                link = item.select_one('a')
                title = item.select_one('h2') or item.select_one('.tt') or item.select_one('a')
                if link:
                    print(f"  - {title.text.strip()[:50] if title else 'No title'}")
                    print(f"    URL: {link.get('href', '')[:60]}...")
    
    # Print raw HTML snippet
    print("\n--- First 2000 chars of body ---")
    body = soup.select_one('body')
    if body:
        print(str(body)[:2000])
    
    return soup

def get_episode_sources(episode_url):
    """Get all video sources from an episode page"""
    print(f"\n{'='*60}")
    print(f"Fetching: {episode_url}")
    print('='*60)
    
    resp = session.get(episode_url, verify=False)
    soup = BeautifulSoup(resp.text, 'html.parser')
    
    print(f"Status: {resp.status_code}")
    
    # Try different selectors for mirrors
    mirror_selectors = [
        '.mobius > .mirror > option',
        '.mobius .mirror option',
        'select.mirror option',
        'option[data-em]',
        '.mirror option',
    ]
    
    mirrors = []
    for sel in mirror_selectors:
        found = soup.select(sel)
        if found:
            print(f"\nFound {len(found)} mirrors with selector: {sel}")
            mirrors = found
            break
    
    if not mirrors:
        print("\nNo mirrors found! Checking page structure...")
        # Print all iframes
        iframes = soup.select('iframe')
        print(f"Found {len(iframes)} iframes directly")
        for iframe in iframes:
            print(f"  - {iframe.get('src', '')}")
        
        # Print options
        options = soup.select('option')
        print(f"\nFound {len(options)} option tags")
        for opt in options[:5]:
            print(f"  - value={opt.get('value', '')} data-em={opt.get('data-em', '')[:30]}...")
        
        return []
    
    sources = []
    for i, mirror in enumerate(mirrors):
        label = mirror.text.strip()
        data_em = mirror.get('data-em', '')
        
        if not data_em:
            continue
            
        try:
            # Decode base64
            decoded = base64.b64decode(data_em).decode('utf-8')
            iframe_soup = BeautifulSoup(decoded, 'html.parser')
            iframe = iframe_soup.select_one('iframe')
            
            if iframe:
                src = iframe.get('src', '')
                
                # Fix URL
                if src.startswith('//'):
                    src = 'https:' + src
                elif src.startswith('/'):
                    src = BASE_URL + src
                    
                print(f"\n[{i+1}] {label}")
                print(f"    Iframe: {src}")
                
                sources.append({
                    'label': label,
                    'iframe': src
                })
                
                # Try to get actual video link for internal players
                if '/utils/player/' in src:
                    try:
                        player_resp = session.get(src, headers={'Referer': episode_url}, verify=False)
                        player_soup = BeautifulSoup(player_resp.text, 'html.parser')
                        
                        source_tag = player_soup.select_one('source')
                        if source_tag:
                            video_url = source_tag.get('src', '')
                            print(f"    Video: {video_url}")
                        
                        # Check for nested iframe
                        nested_iframe = player_soup.select_one('iframe')
                        if nested_iframe:
                            nested_src = nested_iframe.get('src', '')
                            print(f"    Nested Iframe: {nested_src}")
                    except Exception as e:
                        print(f"    Error fetching player: {e}")
                        
        except Exception as e:
            print(f"\n[{i+1}] {label} - Error: {e}")
            
    return sources

if __name__ == "__main__":
    import urllib3
    urllib3.disable_warnings()
    
    print("AnimeSail Video Source Debugger")
    print("="*60)
    
    # First debug homepage
    debug_homepage()
    
    # Try a known episode URL pattern
    # You can change this to any episode URL
    test_urls = [
        f"{BASE_URL}/ore-dake-level-up-na-ken-season-2-episode-5/",
        f"{BASE_URL}/blue-lock-vs-u-20-japan-episode-18/",
    ]
    
    for url in test_urls:
        print(f"\n\nTrying: {url}")
        try:
            sources = get_episode_sources(url)
            if sources:
                print(f"\n✓ Found {len(sources)} sources!")
                break
        except Exception as e:
            print(f"Error: {e}")
