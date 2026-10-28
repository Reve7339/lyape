package com.lyape.ui.components

internal enum class MovementRowLayout { WITH_AVATAR, STACKED }

/** Avatar de 40 dp + dos separaciones de 12 dp + al menos 72 dp para el nombre.
 * El menú vive bajo el importe y no consume otra columna. El avatar nunca desaparece. */
internal fun movementRowLayout(availableWidth: Float, amountWidth: Float): MovementRowLayout = when {
    availableWidth >= amountWidth + 72f + 64f -> MovementRowLayout.WITH_AVATAR
    else -> MovementRowLayout.STACKED
}
