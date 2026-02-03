"""Extract ALL video links from AnimeSail episode page"""
import requests
from bs4 import BeautifulSoup
import base64
import re

session = requests.Session()
session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
})
session.cookies.set('_as_ipin_ct', 'ID')

BASE_URL = "https://154.26.137.28"

def extract_all_sources(episode_url):
    """Extract all video sources from episode page"""
    print(f"\n{'='*70}")
    print(f"Fetching: {episode_url}")
    print('='*70)
    
    resp = session.get(episode_url, verify=False)
    print(f"Status: {resp.status_code}")
    print(f"Content length: {len(resp.text)}")
    
    soup = BeautifulSoup(resp.text, 'html.parser')
    
    # Check if we got real content
    title = soup.select_one('h1.entry-title')
    if title:
        print(f"Title: {title.text}")
    else:
        print("WARNING: No title found - might be blocked")
        print(f"First 500 chars: {resp.text[:500]}")
        return []
    
    # Find all mirror options
    mirrors = soup.select('.mobius > .mirror > option, select.mirror option')
    print(f"\nFound {len(mirrors)} mirror options")
    
    sources = []
    for i, mirror in enumerate(mirrors):
        label = mirror.text.strip()
        data_em = mirror.get('data-em', '')
        
        if not data_em or not label:
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
                
                # Parse quality from label
                quality_match = re.search(r'(\d{3,4})p', label)
                quality = quality_match.group(1) if quality_match else 'Unknown'
                
                # Parse server name from label
                server = label.replace(quality + 'p', '').strip() if quality != 'Unknown' else label
                
                source_info = {
                    'server': server,
                    'quality': quality,
                    'iframe_url': src,
                    'label': label
                }
                sources.append(source_info)
                
                print(f"\n[{i+1}] {label}")
                print(f"    Server: {server}")
                print(f"    Quality: {quality}p")
                print(f"    URL: {src[:80]}{'...' if len(src) > 80 else ''}")
                
        except Exception as e:
            print(f"\n[{i+1}] {label} - Error: {e}")
    
    return sources

def test_homepage():
    """Test if homepage is accessible"""
    print("Testing homepage...")
    resp = session.get(BASE_URL, verify=False)
    print(f"Status: {resp.status_code}")
    
    soup = BeautifulSoup(resp.text, 'html.parser')
    articles = soup.select('article')
    print(f"Found {len(articles)} articles on homepage")
    
    if articles:
        for article in articles[:3]:
            link = article.select_one('a')
            title = article.select_one('.tt h2, h2')
            if link and title:
                print(f"  - {title.text.strip()[:50]}")
                print(f"    URL: {link.get('href', '')}")
    
    return len(articles) > 0

if __name__ == "__main__":
    import urllib3
    urllib3.disable_warnings()
    
    print("AnimeSail Video Source Extractor")
    print("="*70)
    
    # Test homepage first
    if test_homepage():
        print("\n✓ Homepage accessible!")
        
        # Test episode page
        episode_url = f"{BASE_URL}/swallowed-star-4th-season-episode-124/"
        sources = extract_all_sources(episode_url)
        
        print(f"\n{'='*70}")
        print(f"SUMMARY: Extracted {len(sources)} video sources")
        print('='*70)
        
        # Group by server
        servers = {}
        for s in sources:
            server = s['server']
            if server not in servers:
                servers[server] = []
            servers[server].append(s)
        
        for server, items in servers.items():
            print(f"\n{server}:")
            for item in items:
                print(f"  - {item['quality']}p: {item['iframe_url'][:60]}...")
    else:
        print("\n✗ Homepage not accessible - Cloudflare might be blocking")
