# Estado de preparación — 27 de octubre de 2026

## Preparado en el árbol local

- API de compilación/objetivo 36, AGP 8.10.1 y wrapper Gradle 8.11.1 con checksum oficial.
- Kotlin/KSP actualizados a 2.0.21/2.0.21-1.0.28. Room está en la versión 9, con migración
  no destructiva 8→9 y esquemas 8/9 versionados; las pruebas Android cubren la migración.
- Interbank-Plin figura habilitado provisionalmente con el paquete confirmado
  `pe.com.interbank.mobilebanking`. La compatibilidad física, las variantes de notificación y
  la agrupación siguen pendientes de validación; no se presenta como captura terminada.
- CI con Android API 26/35/36 y generación manual de un AAB firmado, solo en main después de
  las pruebas, sin publicación.
- Política completa accesible sin conexión desde Ajustes y aviso previo al acceso a
  notificaciones.
- Política HTML para alojar, ficha en español, icono y gráfico de Play, guía de declaraciones
  y plan de prueba.
- Pruebas de navegación/privacidad y generación de capturas con datos ficticios preparadas
  para CI.

## Verificado en esta preparación

- **75 pruebas Python correctas** (`python3 -m unittest discover -s scripts/tests`).
- `prepare_play_assets.py --check`: política y SVGs coinciden con sus fuentes.
- PNG: `icon.png` 512×512 y `feature.png` 1024×500.
- `git diff --check`: correcto.
- `scripts/local-safe.sh check`: límites de kernel 3 GiB high / 4 GiB max y 1 GiB de swap
  confirmados. No se aumentaron.
- Contacto `soporte@yerry.me` y nombre `Reve Dev` presentes en política/declaraciones; su
  confirmación operativa y la publicación HTTPS siguen siendo del titular.
- `actionlint` no está instalado en este entorno y no se reejecutó. Los contratos Python del
  workflow sí pasan.

## Bloqueado / no ejecutado

El intento local de `:app:testDebugUnitTest` falló antes de compilar, al resolver el plugin KSP.
El entorno no encontró el artefacto en los repositorios configurados; no se cambiaron los
repositorios del proyecto ni se eludieron sus controles. En consecuencia:

- No hay una ejecución JVM/lint nueva de este árbol que pueda presentarse como verde.
- No hay APK ni AAB nuevo validado de estos cambios.
- No se ejecutaron emuladores, instrumentación, capturas nuevas ni instalación en teléfono.
- La migración 8→9 y los casos Room están preparados para la suite Android, pero no se
  ejecutaron en este entorno.
- La interfaz Android nueva requiere validación visual real; las capturas antiguas son solo
  referencia.

HEAD es `cd5667f`; el árbol contiene numerosas correcciones, archivos nuevos, `play/`,
esquemas Room y cambios de workflow sin commit. No se hizo commit, push, etiqueta ni release.
No se publicó en Google Play ni en GitHub Releases.

## Siguiente paso

Revisar y guardar los cambios en un commit autorizado, subir `main` sin etiquetas y ejecutar la
CI nueva. Solo después de pasar los trabajos de calidad se podrá generar el AAB mediante la
opción manual. Si falla, corregir y repetir; no cargar un artefacto antiguo como si fuera el
nuevo.

Antes de publicar, el titular debe publicar la política en una URL HTTPS, confirmar
contacto/nombre, decidir la continuidad de firma con GitHub, revisar la ficha y las capturas,
preparar el vídeo del servicio de voz y gestionar los probadores. Si Play ya recibió
`versionCode=2`, debe incrementarlo antes de compilar el AAB. El uso `mediaPlayback` sigue
sujeto a revisión de Google; no se promete aceptación ni se cambia a `specialUse` para evitarla.
