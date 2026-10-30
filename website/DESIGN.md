---
name: LYape Website
description: Extensión web de Bodega cálida, derivada de la implementación pública.
colors:
  cream: "#F6EFE3"
  paper: "#FFF9F0"
  grape: "#35223E"
  purple: "#704386"
  purple-dark: "#542F67"
  lavender: "#E9DCEC"
  lavender-light: "#F0E7F1"
  muted: "#69596E"
  line: "#D7C7D9"
  bezel: "#29252C"
typography:
  display:
    fontFamily: '"LYape Serif", Georgia, serif'
    fontSize: "clamp(3.2rem, 5.4vw, 5.2rem)"
    fontWeight: 700
    lineHeight: 1.08
    letterSpacing: "-.035em"
  headline:
    fontFamily: '"LYape Serif", Georgia, serif'
    fontSize: "clamp(2.2rem, 4vw, 3.7rem)"
    fontWeight: 700
    lineHeight: 1.08
    letterSpacing: "-.035em"
  body:
    fontFamily: '"Figtree", sans-serif'
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: '"Figtree", sans-serif'
    fontSize: "1rem"
    fontWeight: 600
  caption:
    fontFamily: '"Figtree", sans-serif'
    fontSize: ".85rem"
    lineHeight: 1.4
rounded:
  control: "12px"
  phone: "28px"
  screen: "21px"
spacing:
  s1: ".5rem"
  s2: "1rem"
  s3: "1.5rem"
  s4: "2rem"
  s5: "3rem"
  s6: "4rem"
  s7: "6rem"
components:
  button-primary:
    backgroundColor: "{colors.purple}"
    textColor: "{colors.paper}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: ".85rem 1.2rem"
  button-primary-hover:
    backgroundColor: "{colors.purple-dark}"
    textColor: "{colors.paper}"
  text-link:
    textColor: "{colors.grape}"
    typography: "{typography.label}"
  navigation:
    textColor: "{colors.grape}"
    padding: ".75rem 0"
  status:
    backgroundColor: "{colors.lavender}"
    textColor: "{colors.purple-dark}"
    rounded: "{rounded.control}"
    padding: ".65rem 1rem"
  voice-example:
    textColor: "{colors.purple}"
    padding: "2rem 0"
---

# Design System: LYape Website

## Overview

**Creative North Star: "Bodega cálida"**

La extensión web conserva la identidad aprobada de LYape: crema, tinta uva, acentos morados y el logo Registro continuo. Los titulares serif tienen presencia; el texto sans serif y las acciones breves mantienen una lectura cercana y clara.

Este documento describe exclusivamente la web en `website/` y la página de privacidad que genera `scripts/build_website.py`. No establece ni sustituye la autoridad visual de Android. Los valores proceden de `website/styles.css` y los componentes de `website/index.html`; la dirección y la adaptación autorizada se conservan en `.impeccable/surfaces/website-index-html.md`. La composición concreta de la portada pertenece a ese brief.

**Key Characteristics:**

- Fondo crema, tinta uva y capas suaves de lavanda.
- Source Serif 4 para titulares y Figtree para lectura y acciones.
- Capturas auténticas con proporción intacta y marcos discretos.
- Superficies planas; profundidad reservada a los teléfonos.

## Colors

La paleta combina calidez de papel con morado legible y lavanda tenue.

### Primary

- **Morado de acción** (`purple`): botones, enlaces al pasar el puntero, introducción, ejemplo de voz y foco.
- **Morado profundo** (`purple-dark`): estado hover de los botones y texto del estado informativo.

### Secondary

- **Lavanda suave** (`lavender`): estado informativo y transición curva de la portada.
- **Lavanda de fondo** (`lavender-light`): sección explicativa continua.

### Neutral

- **Crema cálida** (`cream`): fondo principal y fondo de las capturas.
- **Papel claro** (`paper`): texto sobre botones morados y enlace de salto.
- **Tinta uva** (`grape`): títulos, navegación y texto principal.
- **Uva atenuada** (`muted`): explicaciones, pies, disponibilidad y texto auxiliar.
- **Línea lavanda** (`line`): divisores finos de notas y pie.
- **Bisel oscuro** (`bezel`): marco exterior de capturas; no es un fondo de página.

**The Cream and Grape Rule.** Mantener el fondo claro y la tinta uva como base de esta extensión web; el morado identifica acciones y énfasis.

## Typography

**Display Font:** Source Serif 4, servida localmente como LYape Serif, con Georgia y serif como alternativas.

**Body Font:** Figtree, con sans-serif como alternativa. Se sirven archivos locales de pesos 400 y 600; el archivo serif es de peso 700. Las licencias y procedencia están en `website/ASSETS.md`.

**Character:** Titulares de trazo firme y lectura cotidiana sin ornamentación tipográfica añadida.

### Hierarchy

- **Display:** token `display`; titular principal. Los ajustes de anchura se detallan en Layout.
- **Headline:** token `headline`; títulos de las secciones.
- **Body:** token `body`; los párrafos explicativos y de soporte usan 1.1rem en escritorio y 1rem hasta 600px. La explicación limita la línea a 65ch.
- **Label:** token `label`; botones y enlaces de texto con peso 600.
- **Caption:** token `caption`; pies centrados de las capturas.
- **Lectura de privacidad:** párrafos de 1.05rem con interlineado 1.75; título de `clamp(2.3rem, 5vw, 3.5rem)` y subtítulos de 1.65rem.
- **Texto auxiliar:** 0.8–0.95rem según nota, disponibilidad, contacto o pie.

**The Two Voices Rule.** Usar la serif para titulares y la voz ilustrada; usar Figtree para navegación, explicación y acciones.

## Layout

El contenedor compartido tiene anchura máxima de 1340px y resta 5rem al viewport. La escala de espaciado procede de las variables `s1` a `s7`: controles cercanos, separación intermedia entre componentes y amplios descansos entre secciones.

En la portada implementada, el bloque principal usa columnas 0.9fr / 2fr y la galería tres columnas iguales. La sección explicativa usa 0.85fr / 1.3fr. El soporte dispone texto y acciones en una fila; las notas finales usan dos columnas. Estas proporciones describen la página actual, no una obligación para futuras páginas.

| Anchura | Comportamiento observado |
| --- | --- |
| Hasta 1050px | El contenedor resta 3rem; la portada pasa a 0.85fr / 1.65fr y reduce sus huecos. El titular principal usa 3.4rem. |
| Hasta 900px | La portada se apila; el texto limita su anchura a 36rem y la galería a 780px. El titular usa `clamp(3rem, 7.3vw, 4.8rem)`. |
| Hasta 600px | El contenedor resta 2.5rem. La navegación ocupa una segunda fila. El titular usa 3.45rem. La galería es un desplazamiento horizontal nativo con snap y figuras de `min(73vw, 270px)`. La explicación precede al ejemplo de voz; soporte y notas se apilan. |

La privacidad usa una columna de lectura de hasta 74ch con relleno horizontal de 1.5rem. El pie se reorganiza en móvil sin ocultar sus enlaces. No hay menú móvil colapsado ni carrusel JavaScript.

## Elevation & Depth

Las secciones y los controles descansan sobre fondos planos. Los teléfonos son la única superficie con sombra: separa las capturas del fondo y refuerza su carácter de objetos. La curva de lavanda aporta continuidad por color y geometría, sin degradado ni textura.

### Shadow Vocabulary

- **Teléfono en reposo:** `0 14px 24px rgba(53,34,62,.19)`.
- **Teléfono al pasar el puntero:** `0 16px 28px rgba(53,34,62,.19)`.
- **Entrada del teléfono:** inicia con `0 4px 8px rgba(53,34,62,.19)` y desplazamiento vertical de 9px.

La entrada dura 420ms con `cubic-bezier(.16,1,.3,1)`; los teléfonos segundo y tercero esperan 60ms y 120ms. Solo se ejecuta desde 901px y cuando no se solicita movimiento reducido. Con movimiento reducido se eliminan animaciones y transiciones.

**The Phone Depth Rule.** Reservar las sombras de esta web para las capturas enmarcadas; las secciones y acciones permanecen planas.

## Shapes

Controles y estado comparten esquinas suavizadas mediante el token `control`. El teléfono de escritorio usa los tokens `phone` y `screen`, con 7px de bisel. Entre 901px y 1050px se reduce a radios de 20px / 15px y bisel de 5px; hasta 900px usa 26px / 19px y 7px. Los divisores son líneas finas de 1px.

Las capturas mantienen su relación 1:2 mediante `object-fit: contain`; el marco no modifica su contenido. Los iconos funcionales son SVG de trazo, con color heredado. La curva de la portada es una elipse CSS recortada por su sección.

## Components

### Buttons

Acciones claras sobre morado, con texto papel y peso semibold. El relleno y radio están en `button-primary`; la altura mínima es 48px, con separación de 0.8rem entre texto e icono. Hover usa el morado profundo y una transición de fondo de 160ms. El foco visible usa contorno morado de 3px con separación de 5px. No existe una variante deshabilitada ni un estado activo especial en esta implementación.

### Text links

Enlaces subrayados con altura mínima de 44px, peso 600 y separación de 0.5rem para el SVG. El subrayado se separa 0.23em y tiene grosor de 1px. Hover cambia a morado; el foco comparte el contorno global. Los enlaces de navegación y botones mantienen sus tratamientos específicos.

### Status

La etiqueta informativa de estado es lavanda, no interactiva y sin icono ornamental. Mantiene el texto en una línea; en móvil reduce texto a 0.8rem y relleno a 0.5rem / 0.75rem. Su contenido actual es «En pruebas»; no representa una descarga.

### Navigation

El logo y el nombre enlazan al inicio. Los enlaces se disponen horizontalmente con 2rem entre ellos y texto de 1.05rem. Hover incorpora subrayado; el foco es visible. En móvil se conservan todos los destinos en una segunda fila. La privacidad marca su enlace con `aria-current="page"`, sin estilo activo adicional.

### App screenshots

Cada captura real está dentro de un enlace para ampliarla y lleva descripción alternativa y pie visible. La galería puede recibir foco; en móvil se puede recorrer con desplazamiento nativo y muestra la indicación de deslizamiento. No recortar, sustituir ni reconstruir la interfaz dentro de las capturas.

### Voice example

Un SVG de altavoz acompaña una frase serif morada y una aclaración sans serif atenuada. En escritorio la frase usa `clamp(1.8rem, 3vw, 2.6rem)` e interlineado 1.25; en móvil usa 1.8rem y el icono baja de 48px a 32px. Es contenido ilustrativo, no un control de reproducción.

### Registro de prueba cerrada

La extensión del 30-10-2026 mantiene Bodega cálida y añade `#prueba`: introducción
a la izquierda y una lista ordenada de dos pasos a la derecha; hasta 900px se
apila. Los números indican orden real, no decoración. El primer botón usa el
morado principal; el segundo, lavanda con texto morado profundo. Ambos destinos
son externos y avisan de apertura en otra pestaña. No hay formulario ni estado
de éxito simulado ni avisos temporales de revisión de Play Console. La ayuda usa `details/summary` nativo,
con foco visible y una acción de soporte. Se conservan fuentes locales y capturas.

Por petición del usuario se elimina la sobreexplicación: cada paso es un botón,
sin título y párrafo que repitan su acción. Requisitos y privacidad del grupo
quedan en «Requisitos y ayuda». Se retira la nota de «capturas reales», el texto
genérico de soporte y el correo duplicado. Las limitaciones se consultan en
«Antes de usar LYape»; la pérdida del historial al desinstalar sigue visible.

### Lectura y acceso por teclado

La privacidad conserva el texto de su fuente y añade navegación web. El enlace «Saltar al contenido» aparece al recibir foco y permite saltar al contenido principal. Todos los enlaces conservan indicación de foco visible. Esta web no implementa campos, formularios ni tarjetas de funciones; no se inventan variantes para ellos.

## Do's and Don'ts

### Do:

- **Do** conservar la identidad Bodega cálida y el logo Registro continuo.
- **Do** usar las capturas reales con sus proporciones y contenido intactos.
- **Do** mantener visibles el foco, los destinos de navegación y las indicaciones de desplazamiento.
- **Do** servir las fuentes localmente y respetar la preferencia de movimiento reducido.

### Don't:

- **Don't** trasladar estas reglas web a Android como si sustituyeran su sistema visual.
- **Don't** inventar pantallas, cifras o funcionalidades dentro de las imágenes de producto.
- **Don't** sustituir iconos SVG por caracteres tipográficos.
- **Don't** convertir el estado de pruebas en un botón de descarga sin un destino real aprobado.
