package com.lyape.ui.movementform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

internal data class DateInputGroups(
    val day: String,
    val month: String,
    val year: String,
) {
    fun asText(): String = "$day/$month/$year"
}

internal data class TimeInputGroups(
    val hour: String,
    val minute: String,
) {
    fun asText(): String = "$hour:$minute"
}

internal fun dateInputGroups(text: String): DateInputGroups {
    val separated = text.split('/', limit = 3)
    if (separated.size == 3) {
        return DateInputGroups(separated[0], separated[1], separated[2])
    }
    val digits = text.filter(Char::isDigit).take(8)
    return DateInputGroups(
        day = digits.take(2),
        month = digits.drop(2).take(2),
        year = digits.drop(4).take(4),
    )
}

internal fun timeInputGroups(text: String): TimeInputGroups {
    val separated = text.split(':', limit = 2)
    if (separated.size == 2) {
        return TimeInputGroups(separated[0], separated[1])
    }
    val digits = text.filter(Char::isDigit).take(4)
    return TimeInputGroups(
        hour = digits.take(2),
        minute = digits.drop(2).take(2),
    )
}

/**
 * Fecha y hora divididas en grupos. Los separadores pertenecen a la interfaz, así que nunca se
 * pueden borrar por accidente. Cada grupo acepta únicamente números y selecciona su contenido al
 * recibir el foco para facilitar el reemplazo.
 */
@Composable
internal fun SegmentedDateTimeFields(
    dateText: String,
    timeText: String,
    enabled: Boolean,
    dateError: String?,
    timeError: String?,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = dateInputGroups(dateText)
    val time = timeInputGroups(timeText)
    val dayFocus = remember { FocusRequester() }
    val monthFocus = remember { FocusRequester() }
    val yearFocus = remember { FocusRequester() }
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Fecha",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NumberGroupField(
                value = date.day,
                onValueChange = { onDateChange(date.copy(day = it).asText()) },
                label = "Día",
                maxLength = 2,
                enabled = enabled,
                isError = dateError != null,
                focusRequester = dayFocus,
                nextFocusRequester = monthFocus,
                modifier = Modifier.weight(1f),
            )
            Separator("/")
            NumberGroupField(
                value = date.month,
                onValueChange = { onDateChange(date.copy(month = it).asText()) },
                label = "Mes",
                maxLength = 2,
                enabled = enabled,
                isError = dateError != null,
                focusRequester = monthFocus,
                nextFocusRequester = yearFocus,
                modifier = Modifier.weight(1f),
            )
            Separator("/")
            NumberGroupField(
                value = date.year,
                onValueChange = { onDateChange(date.copy(year = it).asText()) },
                label = "Año",
                maxLength = 4,
                enabled = enabled,
                isError = dateError != null,
                focusRequester = yearFocus,
                nextFocusRequester = hourFocus,
                modifier = Modifier.weight(1.45f),
            )
        }
        FieldError(dateError)

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Hora",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NumberGroupField(
                value = time.hour,
                onValueChange = { onTimeChange(time.copy(hour = it).asText()) },
                label = "Hora",
                maxLength = 2,
                enabled = enabled,
                isError = timeError != null,
                focusRequester = hourFocus,
                nextFocusRequester = minuteFocus,
                modifier = Modifier.weight(1f),
            )
            Separator(":")
            NumberGroupField(
                value = time.minute,
                onValueChange = { onTimeChange(time.copy(minute = it).asText()) },
                label = "Min",
                maxLength = 2,
                enabled = enabled,
                isError = timeError != null,
                focusRequester = minuteFocus,
                nextFocusRequester = null,
                modifier = Modifier.weight(1f),
            )
        }
        FieldError(timeError)
    }
}

@Composable
private fun NumberGroupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    enabled: Boolean,
    isError: Boolean,
    focusRequester: FocusRequester,
    nextFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    var editingValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (editingValue.text != value) {
            editingValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = editingValue,
        onValueChange = { candidate ->
            val digits = candidate.text.filter(Char::isDigit).take(maxLength)
            val previousLength = editingValue.text.length
            editingValue = TextFieldValue(digits, selection = TextRange(digits.length))
            onValueChange(digits)
            if (digits.length == maxLength && previousLength < maxLength) {
                nextFocusRequester?.requestFocus()
            }
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (nextFocusRequester == null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { nextFocusRequester?.requestFocus() },
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    editingValue = editingValue.copy(
                        selection = TextRange(0, editingValue.text.length),
                    )
                }
            },
    )
}

@Composable
private fun Separator(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FieldError(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(4.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
