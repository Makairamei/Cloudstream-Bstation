import os
import json
import zipfile

def get_version_from_cs3(file_path):
    """Extract version from manifest.json inside .cs3 (zip) file"""
    try:
        with zipfile.ZipFile(file_path, 'r') as z:
            if 'manifest.json' in z.namelist():
                with z.open('manifest.json') as f:
                    manifest = json.load(f)
                    return manifest.get('version', 1)
    except Exception as e:
        print(f"Warning: Could not read version from {file_path}: {e}")
    return 1

def generate_repo():
    github_repo = os.environ.get("GITHUB_REPOSITORY", "Makairamei/Cloudstream-Bstation")
    base_url = f"https://raw.githubusercontent.com/{github_repo}/builds"
    
    output_dir = "."
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    plugins = []

    print(f"Scanning {output_dir} for .cs3 files...")

    for filename in os.listdir(output_dir):
        if filename.endswith(".cs3"):
            name = filename.replace(".cs3", "")
            file_path = os.path.join(output_dir, filename)
            
            # Extract version from manifest.json inside the .cs3 file
            version = get_version_from_cs3(file_path)
            print(f"Found {name} with version {version}")
            
            plugin = {
                "name": name,
                "internalName": f"com.{name.lower()}",
                "version": version,  # Now reads from manifest!
                "apiVersion": 1,
                "url": f"{base_url}/{filename}",
                "iconUrl": "https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/ic_launcher-playstore.png",
                "authors": ["Makairamei"],
                "tvTypes": ["Anime", "Movie", "TvSeries"],
                "description": f"{name} Extension for Cloudstream",
                "status": 1,
                "fileSize": os.path.getsize(file_path),
                "language": "id"
            }
            plugins.append(plugin)
            print(f"Added {name} v{version}")

    # Write plugins.json
    plugins_path = os.path.join(output_dir, "plugins.json")
    with open(plugins_path, "w") as f:
        json.dump(plugins, f, indent=2)
    print(f"Generated plugins.json at {plugins_path}")

    # Write repo.json
    repo = {
        "name": "Makairamei Extensions",
        "description": "AnimeSail & Bstation Extensions for Cloudstream",
        "manifestVersion": 1,
        "pluginLists": [
            f"{base_url}/plugins.json"
        ]
    }
    
    repo_path = os.path.join(output_dir, "repo.json")
    with open(repo_path, "w") as f:
        json.dump(repo, f, indent=2)
    print(f"Generated repo.json at {repo_path}")

if __name__ == "__main__":
    generate_repo()
