# Revisión local — 28 de octubre de 2026

## Alcance

Web estática y política de privacidad, no aplicación Android. No hubo commit,
push, publicación, modificación de DNS, instalación ni acceso al teléfono.

- 93 pruebas de scripts correctas, incluidas 11 del empaquetado web.
- `prepare_play_assets.py --check` correcto.
- Enlaces locales, fragmentos, fuentes y recursos presentes.
- La política conserva íntegro el contenido generado desde la fuente de Android.
- Los cinco PNG públicos incluyen metadatos de procedencia; no se modificaron
  los píxeles de las capturas reales.
- Vista previa HTTP con fuentes e imágenes cargadas; navegación a privacidad,
  vuelta a inicio, ancla de contenido y ampliación de una captura comprobadas.
- Revisión visual de portada a 320, 390, 699 y 1440 px; privacidad a 390 y
  1440 px. Sin desbordamiento horizontal del documento en esos tamaños. La
  galería móvil tiene desplazamiento horizontal propio.

## Impeccable

La revisión independiente inicial pidió sustituir tres flechas Unicode por SVG
coherentes con los iconos. Aplicado, reconstruido y recapturado. El veredicto
posterior fue `ship` para esa corrección, puntuada como resuelta. No se presenta
como una certificación universal de accesibilidad o de producción.

El usuario autorizó adaptar composición, marcos y tamaños a las capturas reales:
«Sí, adapta el diseño a las capturas reales». La propuesta elegida se usa como
referencia de composición, no como contrato píxel a píxel. La comparación
automática anterior no reconoció esa autorización en español y conserva su
fase SPEC abierta. No se afirma que esa comprobación automática haya pasado.

## Pendiente antes de usar la URL en Play

Publicar mediante el workflow manual, configurar `lyape.yerry.me`, verificar
HTTPS y comprobar las dos páginas públicas sin iniciar sesión. La vista previa
local no sirve como URL pública de privacidad. Pasos en `README.md`.
