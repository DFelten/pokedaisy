#!/usr/bin/env python3
"""Generate the AltStore Classic feed from the packaged iOS app."""

import hashlib
import json
import os
from pathlib import Path
import plistlib
import sys
from zipfile import ZipFile


def main():
    ipa = Path(sys.argv[1])
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    tag = os.environ["TAG"]
    published_at = os.environ["PUBLISHED_AT"]
    raw_url = f"https://raw.githubusercontent.com/{repository}"
    icon_url = (
        f"{raw_url}/{commit}/iosApp/PokeDaisy/Assets.xcassets/"
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
        "sourceURL": f"{raw_url}/altstore/source.json",
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
                "date": published_at,
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
