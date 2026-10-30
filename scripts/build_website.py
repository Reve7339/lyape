"""Empaqueta solo los archivos públicos de LYape. No compila Android ni publica."""

from pathlib import Path
import argparse
import tempfile

if __package__:
    from .prepare_play_assets import privacy_html
else:
    from prepare_play_assets import privacy_html

ROOT = Path(__file__).resolve().parents[1]
SCREENSHOTS = ("01-inicio.png", "02-movimientos.png", "03-cuentas.png")
SOURCES = {
    "index.html": "website/index.html",
    "styles.css": "website/styles.css",
    "assets/logo.svg": "website/assets/logo.svg",
    "assets/icon.png": "website/assets/icon.png",
    "assets/feature.png": "website/assets/feature.png",
    **{f"assets/{name}": f"website/assets/{name}" for name in SCREENSHOTS},
    **{f"assets/fonts/{name}": f"website/assets/fonts/{name}" for name in (
        "figtree-regular.ttf", "figtree-semibold.ttf", "source-serif-bold.ttf",
        "figtree-license.txt", "source-serif-license.txt",
    )},
}


def website_privacy_html() -> str:
    """Conserva íntegro el texto de la app y añade navegación de la web."""
    body = privacy_html().split("<main>", 1)[1].split("</main>", 1)[0]
    return '''<!doctype html>
<html lang="es-PE"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#F6EFE3">
<meta name="description" content="Cómo LYape utiliza las notificaciones y guarda tus registros en el dispositivo.">
<title>LYape · Política de privacidad</title>
<link rel="canonical" href="https://lyape.yerry.me/privacidad/">
<link rel="icon" href="../assets/logo.svg" type="image/svg+xml">
<link rel="stylesheet" href="../styles.css?v=20261030-concise">
</head><body class="policy-body">
<a class="skip-link" href="#contenido">Saltar al contenido</a>
<header class="site-header wrap">
<a class="brand" href="../" aria-label="LYape, inicio"><img src="../assets/logo.svg" width="52" height="52" alt=""><span>LYape</span></a>
<nav aria-label="Navegación principal"><a href="../#la-app">La app</a><a href="./" aria-current="page">Privacidad</a><a href="../#soporte">Soporte</a></nav>
</header><main id="contenido">
<a class="policy-back text-link" href="../"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 12H4m6-6-6 6 6 6"/></svg> Volver a LYape</a>
''' + body + '''
<section class="website-notice" aria-labelledby="website-title">
<h2 id="website-title">Sobre esta web</h2>
<p>Esta página está alojada en GitHub Pages. No añadimos anuncios, analítica, formularios ni cookies propias. El alojamiento es un servicio externo sujeto a la <a href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement">política de privacidad de GitHub</a>. Las fuentes y las imágenes se sirven desde esta misma web.</p>
<p>Para participar en la prueba cerrada, enlazamos a Google Groups y Google Play. Esta web no recoge tu correo ni comprueba tu pertenencia al grupo. Al unirte, los administradores del grupo pueden ver tu dirección de correo y gestionar tu acceso. Estos servicios están sujetos a la <a href="https://policies.google.com/privacy?hl=es">política de privacidad de Google</a>. Puedes abandonar el grupo desde Google Groups y la prueba desde Google Play.</p>
</section></main>
<footer class="site-footer wrap"><p>LYape · Reve Dev</p><a href="mailto:soporte@yerry.me">soporte@yerry.me</a></footer>
</body></html>
'''


def public_files(root: Path) -> dict[str, bytes]:
    """Una lista explícita impide publicar archivos privados del repositorio."""
    missing = [source for source in SOURCES.values() if not (root / source).is_file()]
    if missing:
        raise ValueError("La web no está lista; faltan: " + ", ".join(missing))
    files = {}
    for destination, source in SOURCES.items():
        path = root / source
        if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
            raise ValueError(f"No se publican enlaces simbólicos: {source}")
        files[destination] = path.read_bytes()
    for name in SCREENSHOTS:
        if not files[f"assets/{name}"].startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError(f"Captura PNG inválida: {name}")
    return files


def build() -> Path:
    files = public_files(ROOT)
    # La misma función que genera la política de Play, sin duplicar su texto.
    files["privacidad/index.html"] = website_privacy_html().encode("utf-8")
    files[".nojekyll"] = b""
    parent = ROOT / "build"
    if parent.is_symlink():
        raise ValueError("build/ no puede ser un enlace simbólico")
    parent.mkdir(exist_ok=True)
    # Una carpeta nueva por ejecución no puede arrastrar secretos o archivos antiguos.
    output = Path(tempfile.mkdtemp(prefix="website-", dir=parent))
    for relative, content in files.items():
        destination = output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    try:
        output = build()
    except (ValueError, OSError) as error:
        parser.exit(1, f"Web: {error}\n")
    # Una sola ruta por stdout: el workflow la entrega al empaquetador de Pages.
    print(output)


if __name__ == "__main__":
    main()
