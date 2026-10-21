"""Audit and copy the built APK, never install or contact a device."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile
from dex_inventory import Dex

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
DEST = ROOT / "artifacts"

with zipfile.ZipFile(APK) as archive:
    required = "META-INF/xposed/"
    assert archive.read(required + "scope.list").decode().strip() == "dev.opluskeycontrol"
    assert archive.read(required + "java_init.list").decode().strip() == "dev.opluskeycontrol.vector.VectorProbe"
    assert "targetApiVersion=101" in archive.read(required + "module.prop").decode()
    classes = []
    for name in archive.namelist():
        if name.endswith(".dex"):
            dex = Dex(archive.read(name))
            count, offset = dex.u4(96), dex.u4(100)
            classes.extend(dex.types[dex.u4(offset + i * 32)] for i in range(count))
    assert not any(name.startswith("Lio/github/libxposed/api/") for name in classes), "compileOnly API was bundled"
    assert "Ldev/opluskeycontrol/vector/VectorProbe;" in classes

DEST.mkdir(exist_ok=True)
out = DEST / "OPlusKeyControl-0.1.0-dev-debug.apk"
shutil.copy2(APK, out)
digest = hashlib.sha256(out.read_bytes()).hexdigest()
(DEST / (out.name + ".sha256")).write_text(digest + "  " + out.name + "\n", encoding="utf-8")
results = []
import xml.etree.ElementTree as ET
for pattern in ("domain/build/test-results/test/*.xml", "app/build/test-results/testDebugUnitTest/*.xml"):
    for test in ROOT.glob(pattern):
        suite = ET.parse(test).getroot()
        results.append({key: suite.attrib[key] for key in ("name", "tests", "failures", "errors")})
assert results and all(int(r["failures"]) == 0 and int(r["errors"]) == 0 for r in results)
metadata = {"project": "OPlusKeyControl", "applicationId": "dev.opluskeycontrol", "version": "0.1.0-dev", "sha256": digest,
            "bytes": out.stat().st_size, "unitTests": results, "xposed": "API 101 compileOnly; own-scope zero-hook probe",
            "device": "No install/runtime success implied by this packaging audit"}
(DEST / "build-evidence.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
print(json.dumps(metadata, indent=2))
