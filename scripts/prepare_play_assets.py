"""Genera política pública y gráficos de Play desde fuentes locales. No publica ni usa red."""

from html import escape
from pathlib import Path
import argparse
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = "{http://schemas.android.com/apk/res/android}"


def privacy_html():
    text = (ROOT / "app/src/main/res/raw/privacy_policy.txt").read_text(encoding="utf-8").strip()
    parts = text.split("\n\n")
    body = f"<h1>{escape(parts[0])}</h1>\n"
    for part in parts[1:]:
        if "\n" in part and not part.startswith("Responsable:"):
            title, paragraph = part.split("\n", 1)
            body += f"<section><h2>{escape(title)}</h2><p>{escape(paragraph)}</p></section>\n"
        else:
            body += f'<p class="contact">{escape(part)}</p>\n'
    return '''<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>LYape · Política de privacidad</title>
<meta name="description" content="Cómo LYape utiliza las notificaciones y guarda tus registros en el dispositivo.">
<style>
:root{color-scheme:light;background:#F6EFE3;color:#35223E;font-family:system-ui,sans-serif}
body{margin:0}main{max-width:70ch;margin:auto;padding:40px 24px 64px}
h1,h2{font-family:Georgia,serif;line-height:1.2}h1{font-size:clamp(1.8rem,5vw,2.6rem)}
h2{font-size:1.4rem;margin:32px 0 12px}p{font-size:1rem;line-height:1.7;margin:0 0 16px}
.contact{white-space:pre-line}::selection{background:#CDA5DF;color:#35223E}
@media(prefers-reduced-motion:no-preference){html{scroll-behavior:smooth}}
</style></head><body><main>''' + body + "</main></body></html>\n"


def logo_group():
    vector = ET.parse(ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").getroot()
    group = vector.find("group")
    if group is None:
        raise ValueError("El icono debe conservar su grupo de escala.")
    px, py = group.get(A + "pivotX"), group.get(A + "pivotY")
    sx, sy = group.get(A + "scaleX"), group.get(A + "scaleY")
    paths = []
    for path in group.findall("path"):
        attributes = {"d": path.get(A + "pathData"), "fill": path.get(A + "fillColor", "none")}
        if attributes["fill"] == "#00000000":
            attributes["fill"] = "none"
        for source, target in (("strokeColor", "stroke"), ("strokeWidth", "stroke-width"),
                               ("strokeLineCap", "stroke-linecap"), ("strokeLineJoin", "stroke-linejoin")):
            if path.get(A + source):
                attributes[target] = path.get(A + source)
        paths.append("<path " + " ".join(f'{key}="{escape(value, quote=True)}"' for key, value in attributes.items()) + "/>")
    return f'<g transform="translate({px} {py}) scale({sx} {sy}) translate(-{px} -{py})">' + "".join(paths) + "</g>"


def artifacts():
    logo = logo_group()
    icon = '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><rect width="512" height="512" fill="white"/>' + logo + '</svg>\n'
    feature = '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500"><rect width="1024" height="500" fill="#F6EFE3"/>'
    feature += '<g transform="translate(32 58) scale(.75)">' + logo + '</g>'
    feature += '<text x="450" y="231" fill="#35223E" font-family="Liberation Serif,serif" font-weight="bold" font-size="92">LYape</text>'
    feature += '<text x="454" y="294" fill="#704386" font-family="DejaVu Sans,sans-serif" font-size="28">Tus cobros, en orden.</text></svg>\n'
    return {"play/site/index.html": privacy_html(), "play/graphics/icon.svg": icon,
            "play/graphics/feature.svg": feature}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--png", action="store_true", help="Renderiza SVG con rsvg-convert.")
    args = parser.parse_args()
    for name, content in artifacts().items():
        path = ROOT / name
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                parser.exit(1, f"Regenera el archivo: {name}\n")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
    if args.png:
        for name in ("icon", "feature"):
            source = ROOT / f"play/graphics/{name}.svg"
            subprocess.run(["rsvg-convert", str(source), "-o", str(source.with_suffix(".png"))], check=True)


if __name__ == "__main__":
    main()
