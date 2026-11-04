#!/usr/bin/env python3
"""Assemble the two site-upload artifacts and review files from a tested build."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

from build import ROOT, ARTIFACT, MOD_VERSION

release = ROOT / "release" / MOD_VERSION.split("+", 1)[0]
screenshots = ROOT / "build/run/screenshots"
required_images = {
    "basic.png": screenshots / "test-v1.0-basic.png",
    "advanced.png": screenshots / "test-v1.0-advanced-balanced.png",
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if MOD_VERSION != "1.0.0+mc26.3":
        raise SystemExit(f"Unexpected release version: {MOD_VERSION}")
    if not ARTIFACT.is_file():
        raise SystemExit("Build the 1.0.0 JAR first: python3 scripts/build.py")
    expected = (ARTIFACT.parent / (ARTIFACT.name + ".sha256")).read_text().split()[0]
    if digest(ARTIFACT) != expected:
        raise SystemExit("JAR does not match its build checksum")
    with zipfile.ZipFile(ARTIFACT) as jar:
        if jar.testzip() is not None:
            raise SystemExit("Built JAR has a corrupt ZIP entry")
        metadata = json.loads(jar.read("fabric.mod.json"))
        if metadata["version"] != MOD_VERSION or metadata["environment"] != "client":
            raise SystemExit("Release metadata does not match build")
        for name in ("LICENSE", "assets/metalminer/icon.png", "natives/macos-arm64/libmetalminer.dylib"):
            if name not in jar.namelist():
                raise SystemExit(f"Missing bundled release file: {name}")
        if any("/test/" in name or "AdvancedScreen" in name for name in jar.namelist()):
            raise SystemExit("Test code or retired UI class was bundled")
    for source in required_images.values():
        if not source.is_file():
            raise SystemExit("Run the 1.0.0 integration test to create release screenshots")
    release.mkdir(parents=True, exist_ok=True)
    for name in ("SUMMARY.txt", "PROJECT_DESCRIPTION.md", "CHANGELOG.md", "SUBMISSION.md"):
        if not (release / name).is_file():
            raise SystemExit(f"Missing release documentation: {name}")
    shutil.copy2(ARTIFACT, release / ARTIFACT.name)
    icon = ROOT / "src/main/resources/assets/metalminer/icon.png"
    if icon.stat().st_size > 256 * 1024:
        raise SystemExit("Project icon exceeds Modrinth's 256 KiB icon limit")
    shutil.copy2(icon, release / "icon.png")
    for name, source in required_images.items():
        shutil.copy2(source, release / name)

    sources = [ROOT / name for name in ("LICENSE", "README.md", "VALIDATION.md", ".gitignore", "icon/icon.svg")]
    for folder in ("src/main", "src/test/java", "native", "scripts"):
        sources.extend(path for path in (ROOT / folder).rglob("*") if path.is_file()
                       and "__pycache__" not in path.parts and path.suffix != ".pyc")
    sources.extend((release / name) for name in ("SUMMARY.txt", "PROJECT_DESCRIPTION.md", "CHANGELOG.md", "SUBMISSION.md"))
    source_jar = release / f"metal-miner-{MOD_VERSION}-sources.jar"
    with zipfile.ZipFile(source_jar, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for path in sorted(set(sources)):
            if not path.is_file():
                raise SystemExit(f"Source file disappeared: {path}")
            output.write(path, path.relative_to(ROOT))
    with zipfile.ZipFile(source_jar) as archive:
        if archive.testzip() is not None or not any(name.endswith(".java") for name in archive.namelist()):
            raise SystemExit("Source JAR validation failed")
        if any(name.endswith((".class", ".dylib")) for name in archive.namelist()):
            raise SystemExit("Compiled code entered the source JAR")

    assets = [release / ARTIFACT.name, source_jar, release / "icon.png",
              release / "basic.png", release / "advanced.png"]
    (release / "SHA256SUMS.txt").write_text("".join(f"{digest(path)}  {path.name}\n" for path in assets))
    print(f"Release package ready: {release}")
    for path in assets:
        print(f"  {path.name}: {path.stat().st_size:,} bytes")

if __name__ == "__main__":
    main()
