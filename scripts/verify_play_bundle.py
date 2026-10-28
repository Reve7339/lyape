"""Valida el manifest XML de bundletool. No firma, no publica ni sustituye bundletool validate."""

import argparse
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"


def verify_manifest(xml, gradle):
    root = ET.fromstring(xml)
    if root.tag != "manifest" or root.get("package") != "com.lyape":
        raise ValueError("El paquete debe ser com.lyape.")
    for field in ("versionName", "versionCode"):
        values = re.findall(rf'^\s*{field}\s*=\s*"?([\w.]+)"?\s*$', gradle, re.MULTILINE)
        if len(values) != 1 or root.get(ANDROID + field) != values[0]:
            raise ValueError(f"{field} no coincide con el proyecto.")
    sdk = root.find("uses-sdk")
    if sdk is None or sdk.get(ANDROID + "targetSdkVersion") != "36":
        raise ValueError("El AAB de pruebas debe tener targetSdk 36.")
    if sdk.get(ANDROID + "minSdkVersion") != "26":
        raise ValueError("No cambiar el mínimo Android 8.0.")
    app = root.find("application")
    if app is None or app.get(ANDROID + "debuggable", "false") != "false":
        raise ValueError("El AAB no puede ser depurable.")
    if app.get(ANDROID + "allowBackup") != "false":
        raise ValueError("El respaldo debe seguir desactivado.")
    forbidden = {"android.permission.INTERNET", "android.permission.RECORD_AUDIO",
                 "android.permission.READ_SMS", "android.permission.READ_CONTACTS"}
    permissions = {p.get(ANDROID + "name") for p in root if p.tag.startswith("uses-permission")}
    if permissions & forbidden:
        raise ValueError("Permiso incompatible con la declaración de privacidad actual.")
    return f"com.lyape {root.get(ANDROID + 'versionName')} (code {root.get(ANDROID + 'versionCode')}); API 26–36"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--gradle", type=Path, default=Path("app/build.gradle.kts"))
    args = parser.parse_args()
    try:
        print(verify_manifest(args.manifest.read_text(), args.gradle.read_text()))
    except (OSError, ValueError, ET.ParseError) as error:
        parser.exit(1, f"AAB rechazado: {error}\n")


if __name__ == "__main__":
    main()
