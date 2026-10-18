package com.lyape.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta aprobada "Bodega cálida".
val Cream = Color(0xFFF6EFE3)
val Grape = Color(0xFF704386)
val Ink = Color(0xFF35223E)
val Lavender = Color(0xFFCDA5DF)

// Derivados tomados del mockup de la propuesta elegida.
val CreamSurface = Color(0xFFFFFAF2)
val SurfaceMuted = Color(0xFFEDE2EA)
val MutedInk = Color(0xFF6F6270)
val OutlineSoft = Color(0xFFD5C7D3)
val SoftLavender = Color(0xFFE5D4EB)

/**
 * Borde de controles interactivos: campos, botones delineados y chips sin seleccionar. Es más
 * oscuro que [OutlineSoft] para que el límite de un control alcance 3:1 sobre crema y sobre la
 * superficie clara (3.19:1 y 3.51:1); las tarjetas y divisores conservan [OutlineSoft].
 */
val InteractiveOutline = Color(0xFF92818F)

// El mockup no define color de error; se usa un rojo legible sobre crema.
val ErrorRed = Color(0xFFB3261E)
