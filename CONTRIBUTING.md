# Aportar a LYape

Para proponer un cambio: describe qué resuelve, añade o ajusta su prueba y envía un pull request.
No hace falta introducir nuevas capas, módulos o herramientas para un cambio pequeño.

## Qué prueba corresponde

- Cálculos, fechas, parser o validación: prueba unitaria JVM con un ejemplo reproducible.
- Consultas, migraciones o persistencia: prueba instrumentada con datos exclusivos de prueba.
- Un botón, formulario o recorrido: prueba Compose en `LyapeUserJourneysTest`, usando las
  pantallas y repositorios reales. No basta con probar solo la función que utiliza la pantalla.
- Aspecto visual o accesibilidad: documentar también la revisión manual pertinente. Las pruebas
  funcionales de Compose no demuestran contraste, ausencia de recortes ni lectura audible correcta.

Para un comportamiento nuevo o una corrección: primero reproduce el caso con una prueba que
falle, implementa el cambio y vuelve a ejecutar. No borres ni omitas pruebas para obtener verde.
Usa datos artificiales o anonimizados; nunca claves, bases personales o notificaciones completas.

## Antes de proponer el cambio

Configura el entorno según [README](README.md#desarrollo). Compila con el emulador apagado:

```sh
python3 -m unittest discover -s scripts/tests -v
bash scripts/local-safe.sh check
bash scripts/local-safe.sh gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python3 scripts/verify_test_reports.py unit
adb devices -l
```

Después, **selecciona un AVD de pruebas**, no tu teléfono. Ejemplo para el emulador local:

```sh
bash scripts/local-safe.sh android-tests --serial emulator-5554
python3 scripts/verify_test_reports.py android --reports app/build/outputs/androidTest-results/direct
```

El runner rechaza ejecutar las pruebas en hardware físico. Aun así, Gradle instala los APK
antes de iniciar el runner: seleccionar bien el dispositivo sigue siendo obligatorio. No uses
estas tareas sobre una instalación con datos reales.

Las tareas locales deben cumplir los [límites de recursos](README.md#desarrollo): compilar
sin un AVD activo y luego ejecutar las mismas pruebas con el runner ligero protegido. Este
instala exclusivamente en un serial de emulador validado, no ejecuta Gradle, y genera JUnit XML
que debe superar la misma puerta de escenarios. En GitHub Actions se mantiene la tarea Gradle.

Los pull requests ejecutarán la CI cuando el repositorio esté en GitHub. Solo deben integrarse
con los checks obligatorios en verde y revisión humana. La protección de rama que lo impone
debe activarse en GitHub; un archivo de workflow por sí solo no impide integrar un PR fallido.

Si añades o renombras uno de los recorridos obligatorios, actualiza también el listado en
`scripts/verify_test_reports.py`. No se exige un número
fijo de pruebas ni se afirma una cobertura del 100 %.
