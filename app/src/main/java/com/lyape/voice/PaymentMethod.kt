package com.lyape.voice

import com.lyape.domain.PaymentMedium

/**
 * El prototipo de voz comparte el medio de pago del dominio.
 *
 * No define un enum propio para no duplicar reglas entre datos y audio.
 */
typealias PaymentMethod = PaymentMedium
