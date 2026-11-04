#!/usr/bin/env python3
"""Roll back one install record without overwriting subsequent user edits."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

parser = argparse.ArgumentParser()
parser.add_argument("manifest", type=Path)
args = parser.parse_args()
manifest = json.loads(args.manifest.read_text())
installed = Path(manifest["installed"])
if installed.exists():
    if hashlib.sha256(installed.read_bytes()).hexdigest() != manifest["installed_sha256"]:
        raise SystemExit("Installed JAR has changed since this backup. Leave it intact and review manually.")
for entry in manifest["restores"]:
    original = Path(entry["original"])
    if "modified_sha256" in entry and original.exists():
        if hashlib.sha256(original.read_bytes()).hexdigest() != entry["modified_sha256"]:
            raise SystemExit(f"{original} has been edited since installation. Backup retained; review manually.")
installed.unlink(missing_ok=True)
for entry in manifest["restores"]:
    shutil.copy2(entry["backup"], entry["original"])
print("Installation rolled back. Restart Minecraft.")
