#!/usr/bin/env python3
"""Build against the exact installed 26.3 jars, without redistributing them.

26.3 ships unobfuscated Java classes, so no mapping/remapping task is needed.
Requires Python 3, a JDK 25+, Xcode command line tools and the installed profile.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_APP = Path.home() / "Library/Application Support/ModrinthApp"
MOD_VERSION = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())["version"]
ARTIFACT = ROOT / "dist" / f"metal-miner-{MOD_VERSION}.jar"

def libraries(app):
    version = app / "meta/versions/26.3-0.19.5"
    metadata = json.loads((version / "26.3-0.19.5.json").read_text())
    paths = [version / "26.3-0.19.5.jar"]
    for item in metadata["libraries"]:
        parts = item["name"].split(":")
        if len(parts) > 3 and "natives" in parts[3]:
            if parts[3] != "natives-macos-arm64": continue
        group, artifact, ver = parts[:3]
        classifier = "-" + parts[3] if len(parts) > 3 else ""
        path = app / "meta/libraries" / group.replace(".", "/") / artifact / ver / f"{artifact}-{ver}{classifier}.jar"
        if path.exists(): paths.append(path)
    return metadata, paths

def jdk():
    candidates = [Path(os.environ["METALMINER_JDK"])] if "METALMINER_JDK" in os.environ else []
    hint = ROOT / ".tools/java-home"
    if hint.exists(): candidates.append(Path(hint.read_text().strip()))
    if "JAVA_HOME" in os.environ: candidates.append(Path(os.environ["JAVA_HOME"]))
    for path in candidates:
        if (path / "bin/javac").exists(): return path
    raise SystemExit("Set METALMINER_JDK to a JDK 25+ directory (Contents/Home).")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--app", type=Path, default=DEFAULT_APP)
    parser.add_argument("--profile", default="holeworld")
    args = parser.parse_args()
    java = jdk()
    build = ROOT / "build"
    classes = build / "classes"
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir(parents=True)
    native = classes / "natives/macos-arm64"
    native.mkdir(parents=True)
    subprocess.run(["xcrun", "clang++", "-std=c++17", "-fobjc-arc", "-O2", "-dynamiclib", "-arch", "arm64",
        "-mmacosx-version-min=13.0", "-I" + str(java / "include"), "-I" + str(java / "include/darwin"),
        str(ROOT / "native/bridge.mm"), "-framework", "Foundation", "-framework", "Metal", "-framework", "MetalFX",
        "-framework", "IOSurface", "-framework", "OpenGL", "-o", str(native / "libmetalminer.dylib")], check=True)
    subprocess.run(["codesign", "--force", "--sign", "-", str(native / "libmetalminer.dylib")], check=True)
    _, paths = libraries(args.app)
    profile = args.app / "profiles" / args.profile
    paths.extend(p for p in sorted((profile / "mods").glob("*.jar")) if not p.name.startswith("metal-miner-"))
    nested = build / "compile-libraries"
    nested.mkdir(exist_ok=True)
    for path in list(paths):
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                    destination = nested / Path(name).name
                    destination.write_bytes(archive.read(name))
                    paths.append(destination)
    classpath = os.pathsep.join(str(p) for p in paths)
    (build / "classpath.txt").write_text(classpath)
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    subprocess.run([str(java / "bin/javac"), "--release", "25", "-proc:none", "-encoding", "UTF-8",
        "-cp", classpath, "-d", str(classes), *map(str, sources)], check=True)
    shutil.copytree(ROOT / "src/main/resources", classes, dirs_exist_ok=True)
    if (ROOT / "LICENSE").exists(): shutil.copy2(ROOT / "LICENSE", classes / "LICENSE")
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    jar = ARTIFACT
    subprocess.run([str(java / "bin/jar"), "--create", "--file", str(jar), "-C", str(classes), "."], check=True)
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    (dist / (jar.name + ".sha256")).write_text(f"{digest}  {jar.name}\n")
    print(f"Built {jar}\nSHA-256 {digest}")

if __name__ == "__main__": main()
