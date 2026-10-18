# LYape

Registro local de pagos de Yape para negocios. Android 8.0 o superior.

- Ingresos diarios, semanales y mensuales con gráficos.
- Pagos de negocio o personales, registro manual y ajustes.
- Cierre diario, gastos recurrentes y metas de ingresos.

Los datos se guardan en el teléfono, sin respaldo automático. Concede acceso a notificaciones
desde Ajustes. No recupera pagos no detectados ni captura con el teléfono apagado o tras
forzar su detención.

Solo los movimientos de negocio cuentan en los totales; los ajustes negativos restan.

Pendiente: registrar pagos de Plin desde notificaciones.

## Desarrollo

Kotlin y Jetpack Compose. `domain/` contiene las reglas, `data/` la persistencia con Room,
`notification/` la captura y `ui/` las pantallas. Los importes usan céntimos enteros.

Requiere JDK 17, Android SDK 35, Build Tools 35.0.0 y Python 3.10+. Configura `JAVA_HOME`
y `ANDROID_HOME`. En Linux con systemd y cgroups v2:

```sh
bash scripts/local-safe.sh check
bash scripts/local-safe.sh gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El lanzador limita las tareas a 4 GiB compartidos. Compila con el emulador apagado y no
ejecutes pruebas sobre un teléfono con datos reales. [Pruebas y contribuciones](CONTRIBUTING.md).

Proyecto independiente, no afiliado a Yape.

Licencia [MIT](LICENSE).
