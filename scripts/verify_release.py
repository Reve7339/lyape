"""Verifica versión y manifest de la APK antes de publicarla; la firma la verifica apksigner."""

import argparse
import json
from pathlib import Path
import re
import sys


def verify_release(tag, metadata, badging):
    number = r"(?:0|[1-9][0-9]*)"
    if not re.fullmatch(rf"v{number}\.{number}\.{number}", tag):
        raise ValueError("Se requiere una etiqueta vX.Y.Z, por ejemplo v0.0.1.")
    version = tag[1:]
    if metadata.get("variantName") != "release" or metadata.get("applicationId") != "com.lyape":
        raise ValueError("La entrega debe ser la variante release de com.lyape.")
    elements = metadata.get("elements", [])
    if len(elements) != 1:
        raise ValueError("Se requiere una única APK universal.")
    element = elements[0]
    code = element.get("versionCode")
    if type(code) is not int or code < 1 or element.get("versionName") != version:
        raise ValueError("La etiqueta y el versionado de la APK no coinciden.")
    if element.get("outputFile") != "app-release.apk" or element.get("filters") != []:
        raise ValueError("La salida debe ser app-release.apk universal y firmada.")
    package = re.search(r"^package: (.+)$", badging, re.MULTILINE)
    attributes = dict(re.findall(r"(\w+)='([^']*)'", package[1])) if package else {}
    if (attributes.get("name") != "com.lyape"
            or attributes.get("versionName") != version
            or attributes.get("versionCode") != str(code)):
        raise ValueError("El manifest de la APK no coincide con los metadatos de versión.")
    if "application-debuggable" in badging:
        raise ValueError("No se publican APK depurables.")
    return version


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--badging", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        version = verify_release(
            args.tag, json.loads(args.metadata.read_text(encoding="utf-8")),
            args.badging.read_text(encoding="utf-8"),
        )
    except (OSError, ValueError, TypeError, AttributeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"APK release verificada: {version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
