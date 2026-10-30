# Revisión local — 28 de octubre de 2026

## Simplificación de textos — 30 de octubre de 2026

- Petición del usuario: reducir saturación textual, incluida la nota sobre
  «capturas reales». Aplicadas las guías distill/clarify de Impeccable.
- Eliminados la nota de las capturas, títulos e instrucciones redundantes de
  cada botón, relleno de soporte y correo duplicado. Requisitos y privacidad
  del registro quedan en ayuda desplegable; las limitaciones conservan acceso
  propio. La advertencia de pérdida de datos permanece visible.
- 14 pruebas web correctas, empaquetado correcto y `git diff --check` limpio.
- La comprobación visual de esta revisión queda pendiente: el navegador
  respondió «Local browser use is disabled in Work settings». Las capturas y
  comprobaciones de tamaños de la integración anterior no validan este recorte.
- No se publicó ni se modificó la aplicación Android.

## Integración de testers — 30 de octubre de 2026

- Implementación local de dos pasos: Google Groups y aceptación de la prueba
  en Google Play. Se conserva el aviso de acceso por grupo pendiente de revisión.
- Impeccable: recorrido de incorporación breve, identidad «Bodega cálida»,
  jerarquía de acciones y ayuda desplegable sin JavaScript.
- 13 pruebas del empaquetado web correctas: URLs exactas, orden, atributos de
  enlaces, textos de acceso y ausencia de formularios o scripts de registro.
- Vista previa en navegador a 320, 768, 1024 y 1440 px mediante marcos locales
  de QA (ancho útil descontando scrollbar: 305, 753, 1009 y 1425 px).
  Sin desbordamiento horizontal del documento; controles nuevos de al menos
  48 px de alto. Composición apilada en móvil y dos columnas en escritorio.
- Ayuda abierta y cerrada con Enter; foco visible de 3 px. Enlace a la sección
  de privacidad web comprobado en la página generada.
- No se ha probado el alta real ni la instalación con una cuenta nueva:
  la aprobación del cambio de Google Groups sigue sin confirmarse.
- Sin cambios Android, compilación Android, commit, push ni despliegue.
  Los marcos de QA están fuera de las fuentes y del paquete publicable.

La revisión del 28 de octubre que sigue se conserva como registro histórico;
sus pendientes de publicación no describen el estado actual del sitio público.

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
