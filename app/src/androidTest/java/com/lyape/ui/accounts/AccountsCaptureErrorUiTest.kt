package com.lyape.ui.accounts

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lyape.ui.theme.LyapeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Comprobación semántica preparada; no se ejecuta sin autorización de instrumentación. */
class AccountsCaptureErrorUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun elErrorDeCapturaSeAnunciaYTieneReintento() {
        var retried = 0
        compose.setContent {
            LyapeTheme {
                CaptureCheckError { retried++ }
            }
        }

        compose.onNodeWithText("No se pudo comprobar la captura de pagos.")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Reintentar")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retried)
    }
}
