package com.lyape.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.lyape.LyapeApplication
import com.lyape.data.AppDatabase
import com.lyape.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/** Solo existe en el APK de pruebas. MainActivity usa sus repositorios sin abrir lyape.db. */
class IsolatedTestApplication : LyapeApplication() {
    override val database: AppDatabase by lazy {
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java).build()
    }

    override val preferences: AppPreferences by lazy {
        val file = File(cacheDir, "ui-${UUID.randomUUID()}.preferences_pb")
        AppPreferences(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { file },
            ),
        )
    }
}
