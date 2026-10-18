# Entorno Android

Java y Android SDK son compartidos por usuario; el proyecto fija Java en
`mise.toml` y sus paquetes Android en `android-sdk.packages`. No se guardan
SDK, rutas personales ni caches en Git. Gradle usa el wrapper existente y
su cache local `.buildenv/gradle-home`.

En un equipo nuevo instala [mise](https://mise.jdx.dev/getting-started) y
las Android command-line tools en `~/.local/share/android/sdk/cmdline-tools/latest`.
Revisa `mise.toml`, ejecuta `mise trust` y despues:

```sh
mise install
mise run android:setup
mise run env:check
```

`android:setup` instala exclusivamente los paquetes declarados, solicita las
licencias cuando corresponda y actualiza el archivo ignorado `local.properties`.
`android:configure` solo actualiza rutas; `env:check` no compila ni toca dispositivos.

El JDK debe ser completo: el chequeo requiere java, javac, jar, jlink, jmod y
jmods/java.base.jmod, y que JAVA_HOME coincida con el Java de PATH. Un runtime
recortado de un editor no cumple aunque incluya javac. Las pruebas del chequeo
se ejecutan con `python3 scripts/test_android_environment.py`, sin Gradle.

Para Gradle conserva siempre la proteccion conjunta de memoria de 4 GiB:

```sh
mise exec -- bash scripts/local-safe.sh gradle tasks
```

No ejecutes Gradle directamente ni inicies un emulador para verificar el entorno.
Cambiar versiones se hace editando los manifiestos deliberadamente. El SDK puede
contener API 35 y API 36 simultaneamente; platform-tools es compartido y mutable.
Esto aisla la seleccion de versiones y caches, no es una maquina virtual.
Las versiones de CI no cambian con esta configuracion local.
