package com.lyape.testing

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.runner.AndroidJUnitRunner

/** No permite EJECUTAR la instrumentación en hardware físico; instalar el APK sigue siendo adb. */
class EmulatorTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            "Las pruebas de LYape solo se ejecutan en un AVD de pruebas. No uses tu teléfono con datos."
        }
        return super.newApplication(cl, IsolatedTestApplication::class.java.name, context)
    }
}
