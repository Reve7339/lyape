package com.lyape.data

import androidx.room.TypeConverter
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.PaymentMedium
import java.time.Instant
import java.util.UUID

/** Conversores de tipos ricos del dominio a columnas primitivas de Room. */
class Converters {

    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun fromUuid(value: UUID): String = value.toString()

    @TypeConverter
    fun toUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter
    fun fromSource(value: MovementSource): String = value.name

    @TypeConverter
    fun toSource(value: String): MovementSource = MovementSource.valueOf(value)

    @TypeConverter
    fun fromClassification(value: MovementClassification?): String? = value?.name

    @TypeConverter
    fun toClassification(value: String?): MovementClassification? =
        value?.let(MovementClassification::valueOf)

    @TypeConverter
    fun fromMedium(value: PaymentMedium): String = value.name

    @TypeConverter
    fun toMedium(value: String): PaymentMedium = PaymentMedium.valueOf(value)
}
