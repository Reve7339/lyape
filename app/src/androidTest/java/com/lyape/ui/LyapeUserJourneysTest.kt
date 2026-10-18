package com.lyape.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.MainActivity
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.testing.IsolatedTestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Pantallas reales y repositorios reales; los datos pertenecen solo a esta instrumentación. */
@RunWith(AndroidJUnit4::class)
class LyapeUserJourneysTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val app: IsolatedTestApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as IsolatedTestApplication

    private val isolatedData = object : ExternalResource() {
        override fun before() {
            // Fallar con diagnóstico si la UI no queda inactiva, en vez de esperar varios minutos.
            IdlingPolicies.setIdlingResourceTimeout(30, TimeUnit.SECONDS)
            IdlingPolicies.setMasterPolicyTimeout(45, TimeUnit.SECONDS)
            resetData()
        }
        override fun after() = resetData()
    }

    // Se vacían los datos ANTES de abrir la actividad y DESPUÉS de cerrarla, no mientras lee Room.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(isolatedData).around(compose)

    @Test
    fun ui01RootNavigationAndEmptyState() {
        tab("Movimientos")
        waitText("Todavía no hay movimientos.", substring = true)
        compose.onNodeWithContentDescription("Agregar").assertIsDisplayed()
        tab("Cierre")
        waitText("Cierre diario")
        tab("Ajustes")
        waitText("Privacidad")
        tab("Resumen")
        compose.onNode(tabMatcher("Resumen")).assertIsSelected()
    }

    @Test
    fun ui02CreateManualPaymentThroughForm() {
        newMovement()
        replace("Monto (S/)", "35.50")
        replace("Remitente o descripción (opcional)", "Venta mostrador")
        click("Guardar movimiento")
        waitText("Venta mostrador")
        val saved = records().single()
        assertEquals(3_550L, saved.amountCents)
        assertEquals(MovementSource.MANUAL, saved.source)
        assertFalse(saved.reviewed)
        compose.onNodeWithText("S/ 35.50", substring = true).assertExists()
    }

    @Test
    fun ui03EditAndClassifyUpdatesSummary() {
        seed("Venta editable", 10_000)
        openMovement("Venta editable")
        click("Editar")
        waitText("Editar movimiento")
        replace("Monto (S/)", "125.25")
        replace("Remitente o descripción (opcional)", "Venta corregida")
        click("Guardar cambios")
        waitText("Detalle del movimiento")
        compose.onNodeWithText("Venta corregida").assertExists()
        assertEquals(12_525L, records().single().amountCents)
        click("Personal")
        awaitData { records().single().classification == MovementClassification.PERSONAL }
        compose.onNodeWithContentDescription("Volver").performClick()
        tab("Resumen")
        waitText("S/ 0.00")
        compose.onNodeWithText("S/ 0.00").assertIsDisplayed()
    }

    @Test
    fun ui04ExcludeAndRestorePersonalWithoutDeleting() {
        seed("Pago personal", 4_000, MovementClassification.PERSONAL)
        openMovement("Pago personal")
        click("Anular")
        waitText("Restaurar")
        assertEquals(1, records().size)
        assertEquals(MovementClassification.EXCLUDED, records().single().classification)
        click("Restaurar")
        waitText("Anular")
        assertEquals(MovementClassification.PERSONAL, records().single().classification)
        assertEquals(4_000L, records().single().amountCents)
    }

    @Test
    fun ui05InvalidAmountDoesNotSave() {
        newMovement()
        replace("Monto (S/)", "0")
        click("Guardar movimiento")
        waitText("El monto debe ser mayor que cero.")
        assertTrue(records().isEmpty())
        compose.onNodeWithText("Nuevo movimiento").assertExists()
    }

    @Test
    fun ui06NegativeAdjustmentRequiresAndDisplaysReason() {
        newMovement()
        click("Ajuste")
        click("− Resta")
        replace("Monto (S/)", "5.25")
        click("Guardar movimiento")
        waitText("El ajuste necesita un motivo.")
        assertTrue(records().isEmpty())
        replace("Motivo (obligatorio)", "Devolución cliente")
        click("Guardar movimiento")
        waitText("Devolución cliente")
        val saved = records().single()
        assertEquals(-525L, saved.amountCents)
        assertEquals(MovementSource.ADJUSTMENT, saved.source)
        assertEquals("Devolución cliente", saved.adjustmentReason)
        compose.onNodeWithText("Devolución cliente").performClick()
        waitText("Detalle del movimiento")
        compose.onNodeWithText("Devolución cliente").assertExists()
    }

    @Test
    fun ui07BackConfirmsOnlyDirtyForms() {
        newMovement()
        compose.onNodeWithContentDescription("Volver").performClick()
        waitAddAction()
        compose.onNodeWithText("¿Descartar cambios?").assertDoesNotExist()
        newMovement(fromMovements = true)
        replace("Monto (S/)", "77")
        systemBack()
        waitText("¿Descartar cambios?")
        compose.onNodeWithText("Seguir editando").performClick()
        field("Monto (S/)").assertTextContains("77")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitText("¿Descartar cambios?")
        compose.onNodeWithText("Descartar").performClick()
        waitAddAction()
        assertTrue(records().isEmpty())
    }

    @Test
    fun ui08RecreationKeepsUnsavedValuesAndExitGuard() {
        newMovement()
        replace("Monto (S/)", "88.20")
        replace("Remitente o descripción (opcional)", "Borrador")
        compose.activityRule.scenario.recreate()
        waitText("Nuevo movimiento")
        field("Monto (S/)").assertTextContains("88.20")
        field("Remitente o descripción (opcional)").assertTextContains("Borrador")
        systemBack()
        waitText("¿Descartar cambios?")
        compose.onNodeWithText("Seguir editando").performClick()
        click("Guardar movimiento")
        waitText("Borrador")
        assertEquals(8_820L, records().single().amountCents)
    }

    @Test
    fun ui09SearchCombinesWithClassificationFilter() {
        seed("María Negocio", 1_000)
        seed("María Personal", 2_000, MovementClassification.PERSONAL)
        seed("Otra venta", 3_000)
        tab("Movimientos")
        waitText("María Negocio")
        compose.onNodeWithContentDescription("Buscar movimientos").performClick()
        replace("Buscar por remitente, descripción o motivo", "maria")
        waitText("María Personal")
        compose.onNodeWithText("Otra venta").assertDoesNotExist()
        compose.onNodeWithText("Personal 1").performClick()
        compose.onNodeWithText("María Personal").assertExists()
        compose.onNodeWithText("María Negocio").assertDoesNotExist()
        replace("Buscar por remitente, descripción o motivo", "nadie-coincide")
        waitText("No encontramos movimientos que coincidan con tu búsqueda.")
        compose.onNodeWithContentDescription("Cerrar búsqueda").performClick()
        waitText("María Personal")
    }

    @Test
    fun ui10CloseAndReopenDayWithoutChangingMoney() {
        seed("Venta cierre", 8_000)
        tab("Cierre")
        waitText("Venta cierre")
        click("Marcar como revisado")
        waitText("Reabrir el día")
        assertTrue(records().single().reviewed)
        click("Reabrir el día")
        waitText("Marcar como revisado")
        assertFalse(records().single().reviewed)
        assertEquals(8_000L, records().single().amountCents)
    }

    @Test
    fun ui11PlanDialogsSaveAndConfirmGoalRemoval() {
        waitText("Crear plan")
        click("Crear plan")
        waitText("Plan mensual")
        click("Agregar gasto fijo o extra")
        waitText("Agregar gasto")
        replace("Nombre", "Alquiler de prueba")
        replace("Monto mensual (S/)", "100")
        compose.onNodeWithText("Guardar").performClick()
        waitText("Alquiler de prueba")
        click("Fija mensual")
        click("Definir meta")
        waitText("Meta fija mensual")
        replace("Meta (S/)", "300", scroll = false)
        compose.onNodeWithText("Guardar").performClick()
        waitText("Quitar meta")
        assertEquals(30_000L, currentPlan().goal!!.amountCents)
        click("Quitar meta")
        waitText("¿Quitar esta meta?")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(30_000L, currentPlan().goal!!.amountCents)
        click("Quitar meta")
        waitText("¿Quitar esta meta?")
        compose.onNode(hasText("Quitar") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitText("Definir meta")
        assertEquals(null, currentPlan().goal)
        assertEquals(10_000L, currentPlan().expenses.single().amountCents)
        assertTrue(records().isEmpty())
    }

    private fun resetData() = runBlocking(Dispatchers.IO) {
        app.database.clearAllTables()
        app.preferences.setCapturePaused(false)
    }

    private fun records() = runBlocking { app.movementRepository.observeAll().first() }
    private fun currentPlan() = runBlocking { app.monthlyPlanRepository.observePlan(YearMonth.now()).first() }

    private fun seed(
        label: String,
        amount: Long,
        classification: MovementClassification = MovementClassification.BUSINESS,
    ) = runBlocking {
        val at = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        val id = app.movementRepository.addManual(amount, at, label)
        if (classification != MovementClassification.BUSINESS) {
            app.movementRepository.classify(id, classification)
        }
    }

    private fun newMovement(fromMovements: Boolean = false) {
        if (!fromMovements) tab("Movimientos")
        waitAddAction()
        compose.onNodeWithContentDescription("Agregar").performClick()
        waitText("Nuevo movimiento")
        compose.onNode(tabMatcher("Resumen")).assertDoesNotExist()
    }

    private fun openMovement(label: String) {
        tab("Movimientos")
        waitText(label)
        compose.onNodeWithText(label).performClick()
        waitText("Detalle del movimiento")
    }

    private fun tab(label: String) = compose.onNode(tabMatcher(label)).performClick()
    private fun tabMatcher(label: String) = hasText(label) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
    private fun replace(label: String, value: String, scroll: Boolean = true) {
        val node = field(label)
        if (scroll) node.performScrollTo()
        node.performTextReplacement(value)
    }
    private fun click(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    }
    private fun systemBack() {
        // Con el IME abierto, el primer Atrás puede solo ocultarlo: aquí comprobamos la salida
        // del formulario, no el cierre del teclado. Los valores deben seguir intactos.
        Espresso.closeSoftKeyboard()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun waitText(text: String, substring: Boolean = false) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun awaitData(condition: () -> Boolean) = compose.waitUntil(10_000, condition)
    private fun waitAddAction() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Agregar").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
