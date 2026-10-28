package com.lyape.ui.accounts

import com.lyape.domain.AccountBalance
import com.lyape.domain.MovementClassification
import com.lyape.ui.format.AmountParse
import com.lyape.ui.format.parseAmountCents

internal data class AccountFormValues(
    val name: String = "",
    val business: String = "",
    val personal: String = "",
    val fromId: String = "",
    val toId: String = "",
    val amount: String = "",
    val classification: MovementClassification = MovementClassification.BUSINESS,
    val note: String = "",
) {
    fun saved(): List<String> = listOf(name, business, personal, fromId, toId, amount, classification.name, note)
}

internal fun restoreAccountForm(values: List<String>): AccountFormValues? {
    if (values.size != 8) return null
    val classification = runCatching { MovementClassification.valueOf(values[6]) }.getOrNull()
        ?.takeIf { it != MovementClassification.EXCLUDED } ?: return null
    return AccountFormValues(values[0], values[1], values[2], values[3], values[4], values[5], classification, values[7])
}

internal data class AccountFormResult(
    val errors: Map<String, String> = emptyMap(),
    val businessCents: Long? = null,
    val personalCents: Long? = null,
    val amountCents: Long? = null,
) {
    val valid: Boolean get() = errors.isEmpty()
}

/** Se valida otra vez en el repositorio, dentro de la transacción, frente a cambios de saldo. */
internal fun validateAccountForm(
    mode: String,
    values: AccountFormValues,
    accounts: List<AccountBalance>,
): AccountFormResult {
    val errors = mutableMapOf<String, String>()
    fun amount(field: String, text: String, allowZero: Boolean): Long? = when (val parsed = parseAmountCents(text)) {
        AmountParse.Empty -> { errors[field] = "Ingresa un importe, incluso si es 0."; null }
        AmountParse.Malformed -> { errors[field] = "Usa números, por ejemplo 35.50."; null }
        AmountParse.TooLarge -> { errors[field] = "El importe es demasiado grande."; null }
        is AmountParse.Valid -> if (!allowZero && parsed.cents == 0L) {
            errors[field] = "El importe debe ser mayor que cero."
            null
        } else parsed.cents
    }

    if (mode == "add") {
        if (values.name.isBlank()) errors["name"] = "Escribe el nombre de la cuenta."
        if (values.name.trim().length > 60) errors["name"] = "Usa hasta 60 caracteres."
        return AccountFormResult(errors)
    }
    if (mode == "balance") {
        val business = amount("business", values.business, allowZero = true)
        val personal = amount("personal", values.personal, allowZero = true)
        if (business != null && personal != null && business > Long.MAX_VALUE - personal) {
            errors["personal"] = "El saldo total es demasiado grande."
        }
        return AccountFormResult(errors, businessCents = business, personalCents = personal)
    }
    if (mode != "transfer" && mode != "withdraw") {
        return AccountFormResult(mapOf("form" to "No encontramos este formulario."))
    }
    val amount = amount("amount", values.amount, allowZero = false)
    val from = accounts.firstOrNull { it.id == values.fromId && it.initialized }
    if (from == null) errors["from"] = "Elige una cuenta con saldo configurado."
    if (values.classification == MovementClassification.EXCLUDED) {
        errors["classification"] = "Elige dinero del negocio o personal."
    }
    if (from != null && amount != null) {
        val available = if (values.classification == MovementClassification.PERSONAL) from.personalCents else from.businessCents
        if (amount > available) errors["amount"] = "Saldo insuficiente. Reduce el importe o elige otra cuenta."
    }
    if (mode == "transfer") {
        if (accounts.none { it.id == values.toId && it.initialized }) {
            errors["to"] = "Elige una cuenta con saldo configurado."
        } else if (values.fromId == values.toId) {
            errors["to"] = "Elige una cuenta distinta."
        }
    }
    if (values.note.trim().length > 240) errors["note"] = "Usa hasta 240 caracteres."
    return AccountFormResult(errors, amountCents = amount)
}

internal fun unconfiguredAccountsLabel(count: Int): String =
    if (count == 1) "Falta configurar 1 cuenta." else "Falta configurar $count cuentas."
