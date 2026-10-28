# LYape — Preparación de pruebas en Google Play

Este paquete prepara pruebas; no publica en Google Play ni en GitHub Releases. No crea etiquetas. La aprobación de CI no equivale a aprobación de Google.

## Antes de subir

- Confirmar que el commit enviado contiene todos los cambios revisados, incluidos los archivos nuevos y los esquemas Room. No subir `.commandcode`, `.buildenv`, claves ni documentos locales de `docs/`.
- Ejecutar de nuevo `Calidad y release` con ese commit. Deben pasar JVM/lint debug y release, y Android API 26, 35 y 36. La migración 8→9 y los nuevos casos Room están en la suite Android.
- Revisar las capturas generadas en CI; no sustituirlas por imágenes de una versión anterior.
- Confirmar contacto `soporte@yerry.me` y nombre público Reve Dev antes de publicar la política.
- Probar el APK debug actual en el teléfono solo mediante actualización compatible. No desinstalar ni borrar datos para resolver un conflicto de firma.

## Generar el AAB sin publicarlo

1. Revisar, hacer commit y push de la rama `main`, **sin etiquetas**. El trabajo de publicación de GitHub se omite para ramas.
2. GitHub → Actions → **Calidad y release** → Run workflow.
3. Elegir **main** y activar **Generar AAB firmado para pruebas (solo main; NO publica)**.
4. Tras pasar todas las pruebas, el trabajo **AAB para pruebas (sin publicar)** usa los cuatro secretos de firma existentes. No utiliza cuentas de servicio de Google ni permisos de publicación.
5. Descargar `lyape-play-testing-<número>` y extraerlo. Contiene `LYape-testing.aab`, versión, commit, manifest, certificado público y checksum. No incluye la clave privada.
6. Conservar la clave de firma y verificar que es la prevista. El AAB no se instala con `adb install`: se carga en Play Console.

El job valida la estructura con bundletool 1.18.2 verificado por SHA-256, la firma con jarsigner y el manifest con `verify_play_bundle.py`. Falla si no están los secretos; no sustituye la firma por debug. No reutilizar un AAB de otro commit.

El proyecto mantiene `versionName=0.0.2` y `versionCode=2`. Si Play ya recibió el código 2, incrementar `versionCode` y volver a compilar. No cambiarlo a ciegas ni reemplazar una versión ya usada.

## Firma y usuarios que vienen de GitHub

Antes de configurar Play App Signing, decidir si se conserva la clave de firma de la APK publicada en GitHub. Para que ambas distribuciones puedan actualizarse entre sí, deben coincidir paquete y certificado de firma de la app; la clave de subida de Play puede ser diferente. No elegir una clave de aplicación generada por Google sin evaluar esta continuidad.

La versión debug tiene otra firma y no puede actualizarse directamente con la de Play. No desinstalarla si tiene datos: esta app no tiene exportación/restauración de respaldo. Usar otro dispositivo o un perfil de pruebas limpio para la instalación desde Play; nunca instalar instrumentación en el teléfono con datos reales.

## Política, ficha y gráficos

- `listing/es-419.txt`: nombre, descripción breve, descripción completa y novedades, listos para copiar; revisar antes de enviar.
- `graphics/icon.png`: 512×512, fondo blanco y símbolo original.
- `graphics/feature.png`: 1024×500, gráfico de funciones con la identidad aprobada.
- CI genera cuatro capturas reales con **datos ficticios** en el artefacto `capturas-play-api-36` (inicio, movimientos, cuentas y reportes). Revisarlas por recortes, estados de carga y claridad antes de subirlas. No son pruebas audibles.
- `site/index.html`: política pública estática, sin rastreo ni recursos externos. Se genera desde el mismo texto que lee la app: `app/src/main/res/raw/privacy_policy.txt`.

Publicar **solo** `site/index.html` en una URL HTTPS estable propia, por ejemplo `https://yerry.me/lyape/privacidad/`. Es una URL propuesta: no está creada ni configurada por este cambio. También puede alojarse en un sitio estático público. No enviar una URL local, un archivo PDF o un enlace que requiera acceso. Abrirla sin iniciar sesión y comprobar su lectura desde un teléfono antes de introducirla en Play Console.

Para regenerar política y gráficos: `python3 scripts/prepare_play_assets.py --png` (requiere `rsvg-convert`). `--check` comprueba que no difieren de las fuentes.

## Prueba interna y cerrada

1. Crear la aplicación en Play Console: LYape, aplicación, idioma español, gratuita. No activar una publicación de producción.
2. Completar las declaraciones de contenido que pida la consola con `DECLARATIONS.md` como guía, no como respuestas automáticas.
3. Crear una versión en **Prueba interna**, cargar el AAB y revisar las advertencias de Play. Añadir probadores y compartir el enlace de inscripción. Instalar desde ese enlace con la cuenta autorizada.
4. Ejecutar la matriz de abajo. Corregir fallos antes de ampliar el grupo.
5. Para una cuenta personal nueva, realizar después una **prueba cerrada con al menos 12 participantes inscritos continuamente durante 14 días** y uso real documentado. La prueba interna no sustituye ese requisito. Luego se solicita acceso a producción; no se concede por cumplir solo el plazo.

## Matriz de prueba manual

Registrar modelo/Android, versión/código, fecha y resultado; no enviar nombres ni códigos bancarios.

| Caso | Resultado esperado |
|---|---|
| Primera apertura sin permisos | Permite usar movimientos manuales; no afirma estar capturando |
| Conceder acceso | Explicación previa clara y autorización explícita en Android |
| Cobro Yape / Plin Interbank | Un registro, medio correcto y monto exacto |
| Envío, promoción o aviso ambiguo | No aumenta ingresos ni genera voz |
| App en segundo plano, pantalla bloqueada, retirada de recientes | Verificar captura y voz en cada caso; no asumir por pasar otro |
| Voz desactivada | Guarda el cobro sin anunciarlo |
| Desactivar desde notificación | Se detienen voz y sesión, sin desactivar la captura |
| No molestar, llamada o Bluetooth | Comprobar respeto de interrupciones y salida de audio |
| Reinicio / detención forzada | No reproducir cobros históricos ni presentar una sesión detenida como activa |
| Alta/edición, doble toque y rotación | Una operación; sin pérdidas, duplicados ni cierre accidental |
| Cambio personal/negocio/anulado | Totales y saldos coherentes; historial conservado |
| Pago y anulación de gasto | Saldo y pendiente coherentes; sin rebajar artificialmente el coste o la meta de ganancia |
| Política sin conexión / atrás / rotación | Texto completo legible y retorno a Ajustes |
| Fuente grande, horizontal y TalkBack | Controles accesibles, importes y acciones sin recortes |

La migración automatizada usa datos de prueba. Antes de actualizar una instalación con datos reales, confirmar que pasó en CI; no experimentar con borrados o reinstalaciones.

## Pendientes que requieren al titular

Publicar la URL de privacidad, revisar la ficha y capturas, decidir Play App Signing, completar declaraciones, grabar la demostración del servicio de voz y gestionar los probadores. El uso `mediaPlayback` sigue sujeto a revisión: no se promete aceptación ni se cambia a `specialUse` para evitarla.

Fuentes oficiales consultadas el 27-10-2026:

- API objetivo: https://support.google.com/googleplay/android-developer/answer/11926878
- Pruebas de cuentas personales: https://support.google.com/googleplay/android-developer/answer/14151465
- AAB: https://support.google.com/googleplay/android-developer/answer/9844279
- Datos: https://support.google.com/googleplay/android-developer/answer/10144311
- FGS: https://support.google.com/googleplay/android-developer/answer/13392821
