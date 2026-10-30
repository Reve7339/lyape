# lyape.yerry.me

Web estática de LYape, preparada para GitHub Pages. Incluye la página principal,
capturas auténticas, soporte y política de privacidad generada desde la app.
No se ha publicado ni configurado DNS.

## Contenido

- `index.html` y `styles.css`: propuesta «La app a la vista», aprobada por el titular.
- `assets/01-inicio.png`, `assets/02-movimientos.png`, `assets/03-cuentas.png`:
  capturas reales con datos ficticios del artefacto `capturas-play-api-36`
  de la ejecución verde de CI, revisadas e incluidas sin alterar la interfaz.
- La política pública se genera desde `app/src/main/res/raw/privacy_policy.txt`.
- No usar las interfaces generadas del boceto como capturas del producto.
- Los iconos de Yape, Plin y efectivo gustaron al titular como posible mejora
  futura de Android. Este trabajo no cambia esos iconos en la aplicación.

## Preparar y comprobar

```sh
python3 scripts/prepare_play_assets.py --check
python3 -m unittest discover -s scripts/tests -p test_website.py -v
python3 scripts/build_website.py
```

El último comando devuelve una carpeta nueva en `build/website-*`, ignorada por
Git. Falla si faltan archivos de la página o capturas. Solo empaqueta la lista
explícita del script; no publica el repositorio entero, datos ni claves.
No necesita Gradle, emuladores ni Node.

## Registro de testers

La sección `#prueba` enlaza, en orden, al grupo público
`https://groups.google.com/g/reve-app-testers` y a la aceptación de la prueba
`https://play.google.com/apps/testing/com.lyape`. Ambos enlaces abren otra pestaña.
Los dos botones son los pasos; requisitos, privacidad y recuperación de acceso
están en «Requisitos y ayuda», sin repetir instrucciones junto a cada botón.
No hay formulario, recogida de correos, API de miembros ni verificación automática
del acceso: el usuario confirma cada paso en Google con la misma cuenta.

Preparado el 30-10-2026 con el cambio de testers aún en revisión. Antes de anunciar
el registro, confirmar en Play Console que el cambio está publicado y probar el
recorrido con una cuenta que se una al grupo. Solo entonces retirar el aviso
`#testing-status` de `index.html`, la referencia a ese ID en el segundo enlace y
la frase de revisión en la ayuda; actualizar también la prueba correspondiente.
No confundir miembros del grupo con testers que ya aceptaron participar.

Para previsualizar, sirve la carpeta que devuelve el último comando:

```sh
python3 -m http.server 4175 --bind 127.0.0.1 --directory /ruta/devuelta/por/el/script
```

Abre `http://127.0.0.1:4175/`, no `website/index.html` como archivo: la ruta
`/privacidad/` y los gráficos de Play se completan al empaquetar. Los tipos de
letra se alojan localmente; no hay scripts, analítica ni formularios.

## Publicar, cuando la web esté revisada

1. Hacer commit y push solo con autorización del titular.
2. En `Reve7339/lyape` → **Settings → Pages**, seleccionar **GitHub Actions**.
3. Configurar primero **Custom domain** como `lyape.yerry.me` en GitHub.
   Verificar la propiedad del dominio si GitHub lo solicita.
4. En Namecheap → Advanced DNS, comprobar si ya existe un registro para `lyape`.
   Si no existe, añadir **CNAME**, Host **lyape**, Value **reve7339.github.io**,
   TTL **Automatic**. No poner `https://` ni `/lyape` en el destino.
   Si existe, revisarlo antes de cambiarlo. No tocar `@`, `www`, MX, SPF, DKIM
   ni los registros de correo/Brevo.
5. Ejecutar **Web de LYape (publicación manual)** en `main`. No se dispara con
   cada push y no lanza un release, APK ni AAB.
6. Esperar a que GitHub valide el DNS y emita el certificado; activar
   **Enforce HTTPS** cuando esté disponible.
7. Abrir `https://lyape.yerry.me/` y `https://lyape.yerry.me/privacidad/` sin
   iniciar sesión, también desde móvil. Solo después usar la URL de privacidad
   en Play Console.

El archivo `CNAME` no configura el dominio cuando se publica mediante Actions;
el dominio se establece en Settings → Pages.

Fuentes: [workflow de Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
y [dominio personalizado](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/managing-a-custom-domain-for-your-github-pages-site).
