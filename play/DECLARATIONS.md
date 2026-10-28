# Play Console — borrador de declaraciones

Revisar contra el AAB final y las preguntas reales de la consola. Este archivo no certifica cumplimiento ni envía formularios.

## Ficha y acceso

- Aplicación gratuita, sin anuncios, compras integradas ni suscripciones en 0.0.2. Las donaciones conversadas anteriormente no están implementadas; no anunciarlas.
- Categoría sugerida: Finanzas (registro y control de gastos). Es una sugerencia de ficha, no una afirmación de ser un servicio bancario.
- Audiencia prevista: personas adultas que llevan un negocio; no está diseñada para niños. El titular debe confirmar edades, países y cuestionario de clasificación.
- No requiere cuenta de usuario, inicio de sesión ni credenciales para abrir la app. No proporcionar credenciales bancarias a revisores.
- Contacto propuesto y ya elegido por el titular: soporte@yerry.me. Probar que recibe mensajes antes de enviar.
- Nombre público del desarrollador: Reve Dev; confirmar que coincide con Play Console y la política.

### Instrucciones para revisión (inglés)

LYape is a local bookkeeping app. It does not transfer funds, access bank accounts, or verify bank settlement. No login or developer account is required.

Open Movimientos and use Agregar to create a manual record. Accounts are local bookkeeping records, not bank accounts. Reports, expenses and goals can be tested using manual entries without notification access.

Notification capture is optional. In Ajustes, choose Dar acceso a notificaciones, read the disclosure and grant notification access in Android. Capture currently recognizes incoming Yape and Interbank Plin payment notifications in the device's primary user profile. Other banks and notification formats are not guaranteed. Bank credentials are never requested by LYape.

Voice is optional. In Ajustes, enable Anunciar pagos, permit the ongoing notification if requested and choose an installed local Spanish TTS voice. Probar voz plays a synthetic sample; it does not create a movement or simulate a real payment. Actual payment announcements occur only after a new recognized payment has been stored. They contain payment medium and amount, not the sender's name. The user can stop the voice session in settings or from the ongoing notification.

## Seguridad de datos

Evidencia actual: la app no declara INTERNET, no incorpora publicidad ni analítica, guarda Room/DataStore localmente y desactiva el respaldo automático. Google define «recopilación» en este formulario como transmisión fuera del dispositivo; el tratamiento exclusivamente local no se declara como recopilación.

Respuesta candidata, **condicionada a confirmar el AAB y sus componentes**: no recopila ni comparte datos fuera del dispositivo mediante LYape. No confundir esto con «no accede a datos»: sí procesa nombres, importes, fechas y registros financieros localmente, y la política lo explica.

Revisar por separado el motor TTS: LYape exige voces declaradas locales y le entrega medio y monto mediante Android; el motor instalado es independiente. No afirmar que se auditó la red de todos los motores del mercado. Si cambia el motor elegido o se introduce un SDK que transmita datos, revisar el formulario y la política antes de publicar.

No hay cuentas de usuario creadas en LYape. «Cuentas» en la interfaz significa saldos locales. Editar o anular no borra físicamente el historial. La eliminación completa local se realiza borrando el almacenamiento o desinstalando; no hay recuperación en servidor. No prometer una función de exportación o borrado remoto inexistente.

El correo de soporte es un canal externo y voluntario, tratado en la política. No solicitar datos bancarios por correo. Confirmar las respuestas de cifrado, borrado y prácticas de seguridad según las preguntas aplicables; no marcar opciones basándose solo en que la app sea offline.

## Funciones financieras

Completar la declaración financiera también para la prueba cerrada. LYape no ofrece préstamos, banca, custodia, cambio de moneda, inversión, asesoría financiera ni transferencias de dinero. Su función es el registro local de cobros/gastos y estimaciones.

Si el formulario incluye seguimiento de gastos o «Otros», describir esta función con precisión. No declarar «billetera digital» porque aparezca Yape/Plin ni «banca» porque existan cuentas locales. Si la clasificación elegida exige una cuenta de organización, aclarar con Google la elegibilidad de este registro contable antes de enviar; no cambiar la respuesta para eludir el requisito.

## Servicio foreground de voz — decisión pendiente de Play

Tipo actual: `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Se mantiene la arquitectura revisada; no se añade MediaSession ficticia, no se usa `specialUse` como atajo y no se oculta la espera entre cobros.

Descripción factual propuesta:

> The user explicitly starts an optional voice session from the visible Settings screen. The app keeps an ongoing notification with a Stop action. During the session it waits for new supported payment notifications and briefly speaks the payment medium and amount after the record is stored. The session remains available between announcements, including when the screen is locked. It does not play continuous audio, replay history, record microphone audio or start itself at boot. Stopping voice leaves notification capture independent.

Esta espera persistente entre avisos es el punto a justificar. No afirmar que Google ya aceptó `mediaPlayback` para este caso ni que CI valida políticas. Si no encaja en la declaración, resolverlo antes de pedir producción; cualquier alternativa debe conservar el comportamiento solicitado y pasar pruebas de restricciones de segundo plano y foco en Android 15/16.

### Guion de vídeo para la declaración

Grabar solo un dispositivo de prueba, sin datos personales visibles:

1. Mostrar Ajustes y activar la voz mediante acción del usuario.
2. Mostrar el aviso persistente y la acción Desactivar.
3. Mostrar la muestra de voz, etiquetándola como prueba sintética.
4. Mostrar un cobro de prueba autorizado, grabando también el audio; verificar una fila y una locución. No incluir códigos de seguridad ni credenciales.
5. Salir de la interfaz y comprobar un nuevo cobro; explicar que hay espera entre anuncios. No editar el vídeo para aparentar reproducción continua.
6. Desactivar desde el aviso y comprobar que desaparece la sesión mientras la captura sigue habilitada.

Subir el vídeo a un enlace accesible para revisión solo después de comprobar su contenido. No hay vídeo generado ni publicado en esta preparación.

## Fuentes oficiales

- Seguridad de datos y procesamiento local: https://support.google.com/googleplay/android-developer/answer/10787469
- Privacidad y consentimiento: https://support.google.com/googleplay/android-developer/answer/10144311
- Funciones financieras: https://support.google.com/googleplay/android-developer/answer/13849271
- Declaración FGS: https://support.google.com/googleplay/android-developer/answer/13392821

Consultadas el 27-10-2026. El titular debe comprobar los avisos específicos que muestre su consola al enviar.
