# Estado de preparación — 28 de octubre de 2026

## Preparado en el árbol local

- API de compilación/objetivo 36, AGP 8.10.1 y wrapper Gradle 8.11.1 con checksum oficial.
- Kotlin/KSP permanecen en 2.0.21/2.0.21-1.0.28. El esquema de la base Room está en la versión 9 (la biblioteca sigue en 2.6.1), con migración
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

- **82 pruebas Python correctas** (`python3 -m unittest discover -s scripts/tests`).
- `prepare_play_assets.py --check`: política y SVGs coinciden con sus fuentes.
- PNG: `icon.png` 512×512 y `feature.png` 1024×500.
- `git diff --check`: correcto.
- `scripts/local-safe.sh check`: límites de kernel 3 GiB high / 4 GiB max y 1 GiB de swap
  confirmados. No se aumentaron.
- Contacto `soporte@yerry.me` y nombre `Reve Dev` presentes en política/declaraciones; su
  confirmación operativa y la publicación HTTPS siguen siendo del titular.
- `actionlint` no está instalado en este entorno y no se reejecutó. Los contratos Python del
  workflow sí pasan.

## CI del commit c4c82d0

El usuario subió el commit a main. La ejecución 40634712383 completó correctamente el trabajo
Pruebas y lint. Instrumentación ejecutó 147 pruebas en cada API: 9 fallos en 26, 6 en 35 y 7 en 36.
Los tres reportes confirman la migración 8→9 aprobada. El AAB y la publicación se omitieron.

Correcciones incluidas en el commit posterior `e433f8e`:

- Actividad anfitriona Compose disponible solo en debug mediante ui-test-manifest.
- Esperas por controles habilitados tras lecturas asíncronas; scroll solo donde existe.
- Avatar verificado por su contenedor, conservando las iniciales fuera de TalkBack.
- Capturas: buscar la última fila mediante LazyColumn, no esperar que ya esté compuesta.
- Plin: comprobar que una reparación obsoleta preserva la cuenta predeterminada existente.
- Canales de voz aislados por ID: no simular un bloqueo borrando/recreando el mismo canal.
- Teclado: cierre con insets y espera verificable antes de probar la navegación Atrás.
- Diagnóstico de los textos presentes si una espera de interfaz vuelve a fallar.

No se quitaron pruebas ni se aceptaron fallos para pasar la puerta de calidad.

## CI del commit e433f8e

Los nuevos ZIP aportados contienen 148 pruebas por API: todas pasan en 26; en 35 y 36
fallan dos recorridos (Atrás con formulario modificado y transferencia).
El log de API 26 confirma `BUILD SUCCESSFUL` y el verificador correcto. El job falla después:
`adb pull` intenta leer capturas de una app que UTP ya desinstaló al terminar las pruebas.

Correcciones posteriores, pendientes de CI:

- Capturas escritas en `additionalTestOutputDir`, que AGP extrae antes de desinstalar.
  El workflow recoge las cuatro imágenes desde el host, sin consultar un paquete eliminado;
  rechaza capturas ausentes, ambiguas o sin cabecera PNG. Guarda también la salida adicional
  entre los reportes de diagnóstico.
- Estado de protección del formulario ligado al destino, sin un reinicio asíncrono que pueda
  sobrescribir los avisos del hijo. Las ediciones notifican cambios en el mismo evento.
- Pruebas esperan el campo habilitado y la recomposición tras escribir. Se cierra el IME y
  se espera inactividad antes de transferir o enviar Atrás. La transferencia comprueba sus
  cuentas, el importe y el regreso a Cuentas, además de los saldos persistidos.
- Nueva regresión obligatoria de reapertura y reversión de cambios, sin guardar movimientos.

Impeccable se usó para endurecer los estados del formulario; sin rediseño ni cambios de texto.
No hay validación visual nueva. Las 82 pruebas Python pasan; la compilación Android local
sigue bloqueada al resolver KSP. No se presenta la nueva corrección como aprobada por Android.

## Bloqueo local / pendiente

El intento local de `:app:compileDebugAndroidTestKotlin` falló antes de compilar, al resolver el plugin KSP.
El entorno no encontró el artefacto en los repositorios configurados; no se cambiaron los
repositorios del proyecto ni se eludieron sus controles. En consecuencia:

- No hay compilación ni instrumentación aprobada de estas últimas correcciones locales.
- No hay APK ni AAB validado para distribuir.
- No se ejecutaron emuladores, instrumentación ni instalación local en teléfono.
- La interfaz Android nueva requiere validación visual real; las capturas antiguas son solo
  referencia.

HEAD es `e433f8e`; estas correcciones posteriores siguen sin commit. No se publicó en Google Play
ni en GitHub Releases.

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
