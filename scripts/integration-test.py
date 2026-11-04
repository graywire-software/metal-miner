#!/usr/bin/env python3
"""Build the development-only test driver and launch it in build/run."""
from pathlib import Path
import json
import os
import subprocess
from build import ROOT, jdk

subprocess.run(["python3", str(ROOT / "scripts/run-test.py"), "--prepare-only"], check=True)
classes = ROOT / "build/test-classes"
classes.mkdir(exist_ok=True)
cp = (ROOT / "build/classpath.txt").read_text() + os.pathsep + str(ROOT / "build/classes")
subprocess.run([str(jdk() / "bin/javac"), "--release", "25", "-proc:none", "-cp", cp, "-d", str(classes),
    *map(str, (ROOT / "src/test/java").rglob("*.java"))], check=True)
subprocess.run([str(jdk() / "bin/java"), "-cp", str(classes) + os.pathsep + cp, "dev.metalminer.test.SettingsTest"], check=True)
(classes / "fabric.mod.json").write_text(json.dumps({
    "schemaVersion": 1, "id": "metalminer_test", "version": "1.0.0", "environment": "client",
    "entrypoints": {"client": ["dev.metalminer.test.IntegrationTest"]}, "mixins": ["metalminer-test.mixins.json"]}))
(classes / "metalminer-test.mixins.json").write_text(json.dumps({
    "required": True, "package": "dev.metalminer.test.mixin", "compatibilityLevel": "JAVA_25",
    "client": ["RenderAssertions"], "injectors": {"defaultRequire": 1}}))
subprocess.run([str(jdk() / "bin/jar"), "--create", "--file", str(ROOT / "build/run/mods/metalminer-test.jar"), "-C", str(classes), "."], check=True)
options = ROOT / "build/run/options.txt"
text = options.read_text().replace("fullscreen:true", "fullscreen:false").replace("pauseOnLostFocus:true", "pauseOnLostFocus:false")
options.write_text(text)
if os.environ.get("METALMINER_BENCHMARK"):
    options.write_text(text.replace("enableVsync:true", "enableVsync:false")
        .replace('inactivityFpsLimit:"afk"', 'inactivityFpsLimit:"minimized"')
        .replace("maxFps:120", "maxFps:260"))
(ROOT / "build/run/config/metal-miner.json").unlink(missing_ok=True)
(ROOT / "build/run/config/metal-miner-ui.json").write_text('{"advanced":false}\n')
os.execvp("python3", ["python3", str(ROOT / "scripts/run-test.py")])
