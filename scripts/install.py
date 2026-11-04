#!/usr/bin/env python3
"""Install only this project's JAR; preserve replaced files for rollback."""
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo
import argparse
import hashlib
import json
import os
import shutil
import tempfile
import zipfile
from build import ROOT, DEFAULT_APP, ARTIFACT

parser = argparse.ArgumentParser()
parser.add_argument("--profile", type=Path, default=DEFAULT_APP / "profiles/holeworld")
args = parser.parse_args()
profile = args.profile.resolve()
mods = profile / "mods"
if not mods.is_dir(): raise SystemExit("Profile mods directory does not exist")
source = ARTIFACT
expected = (source.parent / (source.name + ".sha256")).read_text().split()[0]
if hashlib.sha256(source.read_bytes()).hexdigest() != expected: raise SystemExit("Built JAR checksum does not match")
existing = []
for path in mods.glob("*.jar"):
    with zipfile.ZipFile(path) as archive:
        if "fabric.mod.json" not in archive.namelist(): continue
        # Some installed mods use literal line breaks in description strings;
        # Fabric's JSON reader accepts these, so accept them while inspecting IDs.
        metadata = json.loads(archive.read("fabric.mod.json"), strict=False)
        if metadata.get("id") == "metalupscaler":
            raise SystemExit("Remove Metal Upscaler from this profile before installing Metal Miner")
        if metadata.get("id") == "metalminer": existing.append(path)
stamp = datetime.now(ZoneInfo("America/New_York")).strftime("%Y%m%d-%H%M%S")
backup = profile / "metal-miner-backups" / stamp
backup.mkdir(parents=True)
manifest = {"profile": str(profile), "installed": str(mods / source.name), "installed_sha256": expected, "restores": []}
for old in existing:
    saved = backup / old.name
    shutil.copy2(old, saved)
    manifest["restores"].append({"backup": str(saved), "original": str(old)})
for name in ("metal-miner.json", "metal-miner-ui.json"):
    configuration = profile / "config" / name
    if configuration.exists():
        saved = backup / name
        shutil.copy2(configuration, saved)
        manifest["restores"].append({"backup": str(saved), "original": str(configuration),
            "modified_sha256": hashlib.sha256(configuration.read_bytes()).hexdigest()})
fd, temporary = tempfile.mkstemp(prefix=".metal-miner-", suffix=".tmp", dir=mods)
os.close(fd)
try:
    shutil.copy2(source, temporary)
    os.replace(temporary, mods / source.name)
finally:
    Path(temporary).unlink(missing_ok=True)
for old in existing:
    if old.name != source.name: old.unlink()
(backup / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(f"Installed {mods / source.name}\nRollback record: {backup / 'manifest.json'}")
print("Restart Minecraft. Open Mods → Metal Miner → Configure.")
