import os
import json

def generate_repo():
    # Helper to get current repo info (or hardcode/env var)
    # We assume the user follows the standard pattern: https://raw.githubusercontent.com/<USER>/<REPO>/builds
    # We can try to read GITHUB_REPOSITORY env var if running in Actions
    github_repo = os.environ.get("GITHUB_REPOSITORY", "Makairamei/Cloudstream-Bstation")
    base_url = f"https://raw.githubusercontent.com/{github_repo}/builds"
    
    output_dir = "builds"
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    plugins = []

    print(f"Scanning {output_dir} for .cs3 files...")

    for filename in os.listdir(output_dir):
        if filename.endswith(".cs3"):
            name = filename.replace(".cs3", "")
            file_path = os.path.join(output_dir, filename)
            
            # Simple metadata generation
            # Ideally we would parse the .cs3 (zip) to get true metadata from AndroidManifest/Plugin class
            # But for a quick fix, we use reasonable defaults.
            
            plugin = {
                "name": name,
                "internalName": f"com.{name.lower()}", # Convention used in code
                "version": 1,
                "apiVersion": 1,
                "url": f"{base_url}/{filename}",
                "iconUrl": "https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/ic_launcher-playstore.png", # Default Icon
                "authors": ["Makairamei"],
                "tvTypes": ["Anime", "Movie", "TvSeries"],
                "description": f"{name} Extension for Cloudstream",
                "status": 1,
                "fileSize": os.path.getsize(file_path),
                "language": "id"
            }
            plugins.append(plugin)
            print(f"Added {name}")

    # Write plugins.json
    plugins_path = os.path.join(output_dir, "plugins.json")
    with open(plugins_path, "w") as f:
        json.dump(plugins, f, indent=2)
    print(f"Generated plugins.json at {plugins_path}")

    # Write repo.json
    repo = {
        "name": "Cloudstream-Bstation",
        "description": "Bstation Extension Repository",
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
