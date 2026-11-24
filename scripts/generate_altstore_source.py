#!/usr/bin/env python3
"""Generate the AltStore Classic source (source.json) for a release's PokeDaisy.ipa.

    python3 scripts/generate_altstore_source.py <PokeDaisy.ipa> <tag> > source.json

GITHUB_REPOSITORY and GITHUB_SHA come from the workflow (ios-release.yml). The source is attached to the release beside
the IPA, and AltStore reads it from releases/latest/download/source.json: the newest stable release, never a beta.
"""

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import plistlib
import sys
from zipfile import ZipFile


def main():
    ipa = Path(sys.argv[1])
    tag = sys.argv[2]
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    icon_url = (
        f"https://raw.githubusercontent.com/{repository}/{commit}/iosApp/PokeDaisy/Assets.xcassets/"
        "AppIcon.appiconset/AppIcon.png"
    )

    with ZipFile(ipa) as archive:
        info = plistlib.loads(archive.read("Payload/PokeDaisy.app/Info.plist"))
        archive.getinfo(f'Payload/PokeDaisy.app/{info["CFBundleExecutable"]}')
        privacy = {
            key: value for key, value in info.items() if key.endswith("UsageDescription")
        }

    checksum = hashlib.sha256()
    with ipa.open("rb") as contents:
        for chunk in iter(lambda: contents.read(1024 * 1024), b""):
            checksum.update(chunk)

    source = {
        "name": "PokeDaisy Builds",
        "identifier": "com.pokedaisy.altstore",
        "sourceURL": f"https://github.com/{repository}/releases/latest/download/source.json",
        "iconURL": icon_url,
        "apps": [{
            "name": info["CFBundleDisplayName"],
            "bundleIdentifier": info["CFBundleIdentifier"],
            "developerName": "PokeDaisy contributors",
            "localizedDescription": "PokeDaisy for iPhone and iPad, from the latest stable GitHub release.",
            "iconURL": icon_url,
            "category": "games",
            "appPermissions": {"entitlements": [], "privacy": privacy},
            "versions": [{
                "version": info["CFBundleShortVersionString"],
                "buildVersion": info["CFBundleVersion"],
                "minOSVersion": info["MinimumOSVersion"],
                "date": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "localizedDescription": f"Built from commit {commit[:12]}.",
                "downloadURL": f"https://github.com/{repository}/releases/download/{tag}/{ipa.name}",
                "size": ipa.stat().st_size,
                "sha256": checksum.hexdigest(),
            }],
        }],
    }
    json.dump(source, sys.stdout, indent=2)
    print()


if __name__ == "__main__":
    main()
