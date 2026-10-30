"""Empaquetado web: pruebas ligeras, sin Android, navegador ni red."""

from pathlib import Path
from html.parser import HTMLParser
import posixpath
import re
import tempfile
import unittest
from urllib.parse import urlsplit
from unittest.mock import patch

from scripts import build_website
from scripts.prepare_play_assets import privacy_html

ROOT = Path(__file__).resolve().parents[2]


class References(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.urls = []
        self.ids = set()
        self.elements = []
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        data = dict(attrs)
        self.elements.append((tag, data))
        self.urls.extend(data[key] for key in ("href", "src") if key in data)
        if "id" in data:
            self.ids.add(data["id"])


class WebsiteTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for source in build_website.SOURCES.values():
            path = self.root / source
            path.parent.mkdir(parents=True, exist_ok=True)
            # No se presentan estos bytes de prueba como capturas publicables.
            path.write_bytes(b"\x89PNG\r\n\x1a\ntest" if path.suffix == ".png" else b"example")

    def test_missing_page_blocks_packaging(self):
        (self.root / "website/index.html").unlink()
        with self.assertRaisesRegex(ValueError, "website/index.html"):
            build_website.public_files(self.root)

    def test_missing_real_screenshot_blocks_packaging(self):
        (self.root / "website/assets/01-inicio.png").unlink()
        with self.assertRaisesRegex(ValueError, "01-inicio.png"):
            build_website.public_files(self.root)

    def test_wrong_screenshot_type_is_rejected(self):
        (self.root / "website/assets/03-cuentas.png").write_text("not a PNG")
        with self.assertRaisesRegex(ValueError, "PNG inválida"):
            build_website.public_files(self.root)

    def test_unlisted_files_are_not_published(self):
        (self.root / "website/.env").write_text("private fixture")
        (self.root / "website/README.md").write_text("internal notes")
        (self.root / "website/app.apk").write_text("not a web asset")
        result = build_website.public_files(self.root)
        self.assertEqual(set(build_website.SOURCES), set(result))

    def test_asset_symlinks_are_rejected(self):
        file = self.root / "website/assets/01-inicio.png"
        file.unlink()
        file.symlink_to(self.root / "website/assets/icon.png")
        with self.assertRaisesRegex(ValueError, "simbólicos"):
            build_website.public_files(self.root)

    def test_build_symlink_is_rejected(self):
        (self.root / "build").symlink_to(self.root / "website", target_is_directory=True)
        with patch.object(build_website, "ROOT", self.root):
            with self.assertRaisesRegex(ValueError, "build/"):
                build_website.build()

    def test_each_build_is_isolated_and_uses_exact_policy(self):
        with patch.object(build_website, "ROOT", self.root):
            first = build_website.build()
            (first / "stale.txt").write_text("not copied to the next build")
            second = build_website.build()
        self.assertNotEqual(first, second)
        self.assertFalse((second / "stale.txt").exists())
        self.assertEqual(build_website.website_privacy_html(), (second / "privacidad/index.html").read_text())
        expected = set(build_website.SOURCES) | {"privacidad/index.html", ".nojekyll"}
        self.assertEqual(expected, {str(p.relative_to(second)) for p in second.rglob("*") if p.is_file()})

    def test_public_policy_keeps_all_app_content(self):
        app_body = privacy_html().split("<main>", 1)[1].split("</main>", 1)[0]
        page = build_website.website_privacy_html()
        self.assertIn(app_body, page)
        self.assertIn('href="../"', page)
        self.assertIn("Sobre esta web", page)

    def test_workflow_is_manual_main_only_and_not_android(self):
        workflow = (ROOT / ".github/workflows/website.yml").read_text()
        self.assertIn("workflow_dispatch:", workflow)
        for forbidden in ("  push:", "pull_request:", "pull_request_target", "gradlew", "gh release", "secrets."):
            self.assertNotIn(forbidden, workflow)
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertIn("needs: build", workflow)
        self.assertIn("name: github-pages", workflow)
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn("path: ${{ steps.site.outputs.directory }}", workflow)

    def test_real_pages_have_no_broken_local_references(self):
        files = build_website.public_files(ROOT)
        files["privacidad/index.html"] = build_website.website_privacy_html().encode()
        pages = {name: References(content.decode()) for name, content in files.items() if name.endswith(".html")}
        for name, page in pages.items():
            for url in page.urls:
                ref = urlsplit(url)
                if ref.scheme or ref.netloc:
                    continue
                target = posixpath.normpath(posixpath.join(posixpath.dirname(name), ref.path)) if ref.path else name
                if ref.path.endswith("/"):
                    target = posixpath.normpath(posixpath.join(target, "index.html"))
                with self.subTest(page=name, url=url):
                    self.assertIn(target, set(files))
                    if ref.fragment:
                        self.assertIn(ref.fragment, pages[target].ids)
        for url in re.findall(r'url\("([^"]+)"\)', files["styles.css"].decode()):
            self.assertIn(url, set(files))

    def test_hidden_line_breaks_keep_word_separation(self):
        page = (ROOT / "website/index.html").read_text()
        self.assertIn("efectivo.<br> Las", page)
        self.assertIn("negocio,<br> en", page)

    def test_testing_links_are_exact_ordered_and_safe(self):
        page = (ROOT / "website/index.html").read_text()
        parsed = References(page)
        external = [attrs for tag, attrs in parsed.elements if tag == "a" and
                    attrs.get("href", "").startswith(("https://groups.google.com/", "https://play.google.com/"))]
        self.assertEqual([link["href"] for link in external], [
            "https://groups.google.com/g/reve-app-testers",
            "https://play.google.com/apps/testing/com.lyape",
        ])
        for link in external:
            self.assertEqual(link["target"], "_blank")
            self.assertEqual(set(link["rel"].split()), {"noopener", "noreferrer"})
            for described_id in link["aria-describedby"].split():
                self.assertIn(described_id, parsed.ids)
        self.assertIn('href="#prueba"', page)
        self.assertIn('class="testing-steps"', page)

    def test_testing_explains_access_without_collecting_data(self):
        page = (ROOT / "website/index.html").read_text()
        self.assertNotIn("en revisión", page)
        self.assertNotIn("testing-status", page)
        self.assertIn("Usa la misma cuenta de Google", page)
        self.assertIn("Android 8 o posterior", page)
        self.assertIn("cuenta de Play Store de Perú", page)
        tags = {tag for tag, _ in References(page).elements}
        self.assertTrue(tags.isdisjoint({"form", "input", "iframe", "script"}))
        self.assertNotIn("Próximamente en Google Play", page)
        self.assertIn("Google Groups y Google Play", build_website.website_privacy_html())

    def test_landing_copy_is_concise_but_keeps_safety_information(self):
        page = (ROOT / "website/index.html").read_text()
        self.assertNotIn("Capturas reales con datos de ejemplo", page)
        self.assertNotIn("Tu experiencia ayuda a mejorar", page)
        steps = page.split('<ol class="testing-steps"', 1)[1].split('</ol>', 1)[0]
        self.assertNotIn("<h3", steps)
        self.assertNotIn("<p>", steps)
        help_text = page.split('<details class="testing-help">', 1)[1].split('</details>', 1)[0]
        self.assertIn("Android 8 o posterior", help_text)
        self.assertIn("administradores del grupo pueden ver tu correo", help_text)
        self.assertIn("No hay respaldo automático", page)
        self.assertIn("LYape no mueve dinero ni consulta saldos bancarios", page)


if __name__ == "__main__":
    unittest.main()
