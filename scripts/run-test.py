#!/usr/bin/env python3
"""Launch an isolated, offline client with copies of the profile's configuration.
Never reads account tokens or opens the original saves. Test data stays in build/run.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import uuid
from build import ROOT, DEFAULT_APP, ARTIFACT, libraries, jdk

parser = argparse.ArgumentParser()
parser.add_argument("--prepare-only", action="store_true")
parser.add_argument("--world", help="World directory name inside build/run/saves")
args = parser.parse_args()
profile = DEFAULT_APP / "profiles/holeworld"
run = ROOT / "build/run"
run.mkdir(parents=True, exist_ok=True)
for name in ["mods", "config", "shaderpacks", "resourcepacks"]:
    destination = run / name
    if not destination.exists(): shutil.copytree(profile / name, destination)
if not (run / "options.txt").exists(): shutil.copy2(profile / "options.txt", run / "options.txt")
for old in (run / "mods").glob("metal-miner-*.jar"): old.unlink()
shutil.copy2(ARTIFACT, run / "mods")
if args.prepare_only:
    print(run)
    raise SystemExit(0)
metadata, jars = libraries(DEFAULT_APP)
command = [str(jdk() / "bin/java"), "-XstartOnFirstThread", "-Xmx4G", "--enable-native-access=ALL-UNNAMED",
    "-Dmetalminer.benchmark=" + ("true" if os.environ.get("METALMINER_BENCHMARK") else "false"),
    "-Dmixin.debug.export=true", "-cp", os.pathsep.join(map(str, jars)), metadata["mainClass"],
    "--username", "MetalMinerTest", "--version", "26.3", "--gameDir", str(run),
    "--assetsDir", str(DEFAULT_APP / "meta/assets"), "--assetIndex", metadata["assetIndex"]["id"],
    "--uuid", uuid.uuid3(uuid.NAMESPACE_DNS, "MetalMinerTest").hex, "--accessToken", "0",
    "--userType", "legacy", "--versionType", "Metal Miner test", "--width", "960", "--height", "600"]
if args.world: command += ["--quickPlaySingleplayer", args.world]
(ROOT / "build/test-launch.pid").write_text(str(os.getpid()))
print("Launching offline test client. Log: build/run/logs/latest.log", flush=True)
os.chdir(run)
os.execv(command[0], command)
