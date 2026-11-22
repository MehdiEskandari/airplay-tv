#!/usr/bin/env python3
"""Verify translation coverage, formatting and the built store APKs before uploading."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import unicodedata
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
FORMAT = re.compile(r"%(?:(\d+)\$)?[-+0 #]*\d*(?:\.\d+)?([dfs])")


def resources(folder):
    tree = ET.parse(RES / folder / "strings.xml").getroot()
    return {node.attrib["name"]: node for node in tree}


def placeholders(value):
    return sorted({(index or "1", kind) for index, kind in FORMAT.findall(value)})


def verify_vectors():
    # Android's vector parser reads arc flags as floats; compact SVG flags can
    # pass aapt/lint yet make System UI reject the foreground notification icon.
    number = r"[+-]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][+-]?\d+)?"
    for folder in (RES / "drawable", RES / "mipmap-anydpi"):
        for path in folder.glob("*.xml"):
            for element in ET.parse(path).getroot().iter():
                data = element.get("{http://schemas.android.com/apk/res/android}pathData", "")
                for _, values in re.findall(r"([Aa])([^MmLlHhVvCcSsQqTtAaZz]*)", data):
                    numbers = re.findall(number, values)
                    assert len(numbers) % 7 == 0, f"Android-incompatible arc in {path.name}"
                    for offset in range(0, len(numbers), 7):
                        assert numbers[offset + 3] in ("0", "1") and numbers[offset + 4] in ("0", "1"), f"Ambiguous SVG arc flags in {path.name}"
    print("PASS: native icon vector arc flags are compatible with Android")


def verify_resources():
    persian = resources("values")
    for folder in ("values", "values-b+mul"):
        for name, element in resources(folder).items():
            for value in element.itertext():
                assert not any("\u0600" <= c <= "\u08ff" and unicodedata.category(c) in ("Mn", "Me") for c in value), f"Arabic diacritics in {name}"
    for folder in ("values-en", "values-b+mul"):
        translated = resources(folder)
        assert persian.keys() == translated.keys(), f"Missing translations in {folder}"
        for name, original in persian.items():
            counterpart = translated[name]
            assert original.tag == counterpart.tag, name
            if original.tag == "string":
                assert placeholders(original.text or "") == placeholders(counterpart.text or ""), name
                if folder == "values-en":
                    assert not re.search(r"[\u0600-\u06ff]", counterpart.text or ""), f"Persian text in English: {name}"
            else:
                assert len(original) == len(counterpart), name
    print(f"PASS: {len(persian)} resources covered in all three languages; format arguments match")


def certificate(apksigner, apk):
    result = subprocess.check_output([str(apksigner), "verify", "--print-certs", str(apk)], text=True)
    return re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", result).group(1)


def verify_apk(apk, require_signed, previous_apk):
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    tools = sorted((sdk / "build-tools").iterdir(), key=lambda p: [int(n) for n in re.findall(r"\d+", p.name)])[-1]
    manifest = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    assert "name='io.github.mehdieskandari.airplaytv'" in manifest, "Wrong application ID"
    assert "REQUEST_INSTALL_PACKAGES" not in manifest, f"Forbidden install permission in {apk}"
    assert "UpdateInstallReceiver" not in subprocess.check_output([
        str(tools / "aapt2"), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"
    ], text=True), "Old installer receiver remains"
    assert "versionCode='150'" in manifest and "versionName='1.5.0'" in manifest, "Wrong release version"
    with zipfile.ZipFile(apk) as archive:
        for abi in ("arm64-v8a", "armeabi-v7a"):
            assert f"lib/{abi}/libairplay_native.so" in archive.namelist(), f"Missing native receiver for {abi}"
    signed = subprocess.run([str(tools / "apksigner"), "verify", str(apk)], capture_output=True).returncode == 0
    if require_signed:
        assert signed, f"Release APK is not signed: {apk}"
    if previous_apk:
        assert certificate(tools / "apksigner", apk) == certificate(tools / "apksigner", previous_apk), "Signing key changed"
    print(f"PASS: {apk.name}: version 150, both ARM ABIs, no installer permission/receiver; signed={signed}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", nargs="*", type=Path)
    parser.add_argument("--require-signed", action="store_true")
    parser.add_argument("--previous-apk", type=Path)
    args = parser.parse_args()
    verify_resources()
    verify_vectors()
    for path in args.apk:
        verify_apk(path, args.require_signed, args.previous_apk)
