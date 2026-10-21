"""Audit and copy the built APK, never install or contact a device."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile
import argparse
from dex_inventory import Dex

parser = argparse.ArgumentParser(description=__doc__)
mode = parser.add_mutually_exclusive_group()
mode.add_argument("--policy-probe", action="store_true", help="Audit the opt-in Vector system/self observation build")
mode.add_argument("--runtime", action="store_true", help="Audit the opt-in experimental remapping build")
args = parser.parse_args()
policy_probe, runtime = args.policy_probe, args.runtime
experimental = policy_probe or runtime

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
DEST = ROOT / "artifacts"

with zipfile.ZipFile(APK) as archive:
    required = "META-INF/xposed/"
    scope = archive.read(required + "scope.list").decode().splitlines()
    assert scope == (["system", "dev.opluskeycontrol"] if experimental else ["dev.opluskeycontrol"]), "Unexpected Vector scope"
    assert archive.read(required + "java_init.list").decode().strip() == "dev.opluskeycontrol.vector.VectorProbe"
    assert "targetApiVersion=101" in archive.read(required + "module.prop").decode()
    classes = []
    callbacks = {}
    for name in archive.namelist():
        if name.endswith(".dex"):
            dex = Dex(archive.read(name))
            count, offset = dex.u4(96), dex.u4(100)
            classes.extend(dex.types[dex.u4(offset + i * 32)] for i in range(count))
            for method in ("onSystemServerStarting", "attachRuntime", "attachPolicy"):
                code = dex.method_code("Ldev/opluskeycontrol/vector/VectorProbe;", method)
                if code is not None: callbacks[method] = code
    assert not any(name.startswith("Lio/github/libxposed/api/") for name in classes), "compileOnly API was bundled"
    assert "Ldev/opluskeycontrol/vector/VectorProbe;" in classes
    for method, enabled in (("onSystemServerStarting", experimental), ("attachRuntime", runtime), ("attachPolicy", policy_probe)):
        assert method in callbacks
        # D8 expands synchronized no-op helpers into monitor/return/exception cleanup.
        # No constructor, field access, invoke, branch on SDK or hook code may remain
        # in a disabled helper. Each allowed instruction here is exactly one code unit.
        inert = all(callbacks[method][i] in {0x00, 0x0d, 0x0e, 0x1d, 0x1e, 0x27, 0x28}
                    for i in range(0, len(callbacks[method]), 2))
        assert (not inert) == enabled, f"Wrong compiled build flag: {method}"

DEST.mkdir(exist_ok=True)
out = DEST / ("OPlusKeyControl-0.1.0-runtime-debug.apk" if runtime else "OPlusKeyControl-0.1.0-policy-probe-debug.apk" if policy_probe else "OPlusKeyControl-0.1.0-dev-debug.apk")
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
            "bytes": out.stat().st_size, "unitTests": results, "scope": scope,
            "xposed": "API 101 compileOnly; experimental complete-coverage Plus runtime" if runtime else "API 101 compileOnly; finite pass-through policy probe" if policy_probe else "API 101 compileOnly; own-scope zero-hook probe",
            "device": "No install/runtime success implied by this packaging audit"}
(DEST / ("runtime-build-evidence.json" if runtime else "policy-probe-build-evidence.json" if policy_probe else "build-evidence.json")).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
print(json.dumps(metadata, indent=2))
