package com.lyape.ui

import androidx.compose.ui.semantics.Role
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.IdlingPolicies
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.MainActivity
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.MovementFactory
import com.lyape.testing.IsolatedTestApplication
import com.lyape.testing.testMovements
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
import java.util.UUID
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
        compose.onNodeWithText("Cierre diario").assertDoesNotExist()
        compose.onNodeWithText("Cuentas y salidas").assertDoesNotExist()
        tab("Cuentas")
        waitText("Cuentas")
        tab("Ajustes")
        waitText("Privacidad")
        tab("Inicio")
        compose.onNode(tabMatcher("Inicio")).assertIsSelected()
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
        tab("Inicio")
        val income = hasText("Ingresos del mes") and hasText("S/ 0.00")
        compose.waitUntil(10_000) { compose.onAllNodes(income).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(income).assertIsDisplayed()
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
        click("Registrar un ajuste")
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
        field("Monto (S/)").assertTextContains("77")
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
    fun ui07bReopeningAndRevertingDoesNotLeakDirtyState() {
        repeat(2) {
            newMovement()
            replace("Monto (S/)", "77")
            compose.onNodeWithContentDescription("Volver").performClick()
            waitText("¿Descartar cambios?")
            click("Seguir editando")
            replace("Monto (S/)", "")
            systemBack()
            waitAddAction()
            compose.onNodeWithText("¿Descartar cambios?").assertDoesNotExist()
        }
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
    fun ui08bEditRecreationKeepsTheSameAttemptAndSavesOnce() {
        seed("Venta editable", 10_000)
        openMovement("Venta editable")
        click("Editar")
        waitText("Editar movimiento")
        replace("Monto (S/)", "125.25")
        compose.activityRule.scenario.recreate()
        waitText("Editar movimiento")
        field("Monto (S/)").assertTextContains("125.25")
        click("Guardar cambios")
        waitText("Detalle del movimiento")
        val stored = records().single()
        assertEquals(12_525L, stored.amountCents)
        assertEquals(1L, stored.editVersion)
    }

    @Test
    fun ui09SearchCombinesWithClassificationFilter() {
        seed("María Negocio", 1_000)
        seed("María Personal", 2_000, MovementClassification.PERSONAL)
        seed("Otra venta", 3_000)
        tab("Movimientos")
        waitText("María Negocio")
        compose.onNodeWithContentDescription("Buscar movimientos").performClick()
        replace("Buscar movimientos", "maria")
        waitText("María Personal")
        compose.onNodeWithText("Otra venta").assertDoesNotExist()
        compose.onNodeWithText("Personal 1").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("María Negocio").fetchSemanticsNodes().isEmpty()
        }
        waitText("María Personal")
        compose.onNodeWithText("María Personal").assertExists()
        compose.onNodeWithText("María Negocio").assertDoesNotExist()
        replace("Buscar movimientos", "nadie-coincide")
        waitText("No encontramos movimientos que coincidan con tu búsqueda.")
        compose.onNodeWithContentDescription("Cerrar búsqueda").performClick()
        waitText("María Personal")
    }

    @Test
    fun ui10NoDailyClosingRequired() {
        seed("Venta cierre", 8_000)
        tab("Inicio")
        compose.onNodeWithText("Revisar").assertDoesNotExist()
        compose.onNodeWithText("Cierre diario").assertDoesNotExist()
        tab("Movimientos")
        waitText("Venta cierre")
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
        click("Definir meta")
        waitText("Ganancia deseada (S/)")
        click("Todos los meses")
        replace("Ganancia deseada (S/)", "300", scroll = false)
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

    @Test
    fun ui12HistorySearchReachesBeyondFirstPage() {
        val at = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        runBlocking {
            app.movementRepository.addAll((1..200).map { i ->
                MovementFactory.manual(
                    id = UUID(0, i.toLong()), amountCents = 100,
                    occurredAt = at.minusSeconds(i.toLong()), now = at,
                    label = if (i == 200) "María antigua" else "Venta $i",
                )
            })
        }
        tab("Movimientos")
        waitText("Todos 200")
        waitText("Venta 1")
        compose.onNodeWithText("María antigua").assertDoesNotExist()
        compose.onNodeWithContentDescription("Buscar movimientos").performClick()
        replace("Buscar movimientos", "maria")
        waitText("María antigua")
        compose.onNodeWithText("Todos 200").assertExists()
        compose.onNodeWithText("Venta 1").assertDoesNotExist()
    }

    @Test
    fun ui13ConfigureAccountWithoutChangingIncome() {
        seed("Ingreso anterior", 2500)
        tab("Cuentas")
        waitText("Yape")
        click("Yape")
        waitText("Configurar saldo")
        click("Configurar saldo")
        replace("Negocio (S/)", "1000")
        replace("Personal (S/)", "200")
        click("Guardar saldo")
        waitText("Actualizar saldo")
        val yape = runBlocking { app.accountsRepository.observeAccount("yape").first() }!!
        assertEquals(100_000L, yape.businessCents)
        assertEquals(20_000L, yape.personalCents)
        assertEquals(120_000L, yape.totalCents)
        assertEquals(2500L, records().single().amountCents)
    }

    @Test
    fun ui14TransferMovesBalanceWithoutCreatingIncome() {
        runBlocking {
            app.accountsRepository.setBalance("yape", 100_000, 20_000)
            app.accountsRepository.setBalance("cash", 0, 0)
        }
        tab("Cuentas")
        waitText("Yape")
        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Transferir") and hasClickAction())
        click("Transferir")
        waitText("Transferencia")
        replace("Importe (S/)", "100")
        field("Importe (S/)").assertTextContains("100")
        compose.onNodeWithContentDescription("Desde: Yape").assertExists()
        compose.onNodeWithContentDescription("Hacia: Efectivo").assertExists()
        hideKeyboard()
        click("Registrar transferencia")
        waitForNode(tabMatcher("Cuentas"))
        compose.onNode(tabMatcher("Cuentas")).assertIsSelected()
        awaitData { runBlocking { app.accountsRepository.observeOperations().first().size == 1 } }
        val summary = runBlocking { app.accountsRepository.observeSummary().first() }
        assertEquals(120_000L, summary.totalCents)
        assertEquals(100_000L, summary.businessCents)
        assertEquals(10_000L, summary.accounts.first { it.id == "cash" }.businessCents)
        assertTrue(records().isEmpty())
    }

    @Test
    fun ui15PartialExpensePaymentPreservesBudgetAndIncome() {
        val expense = runBlocking {
            app.accountsRepository.setBalance("yape", 100_000, 20_000)
            app.monthlyPlanRepository.addExpense(YearMonth.now(), "Luz de prueba", 15_000)
        }
        waitText("Ver plan")
        click("Ver plan")
        waitText("Luz de prueba")
        click("Pagar")
        waitText("Registra un pago que ya hiciste. LYape no mueve dinero.")
        replace("Importe (S/)", "75")
        click("Registrar pago")
        awaitData {
            runBlocking { app.accountsRepository.observeExpensePayments(YearMonth.now()).first()
                .first { it.expenseId == expense }.paidCents == 7500L }
        }
        assertEquals(15_000L, currentPlan().expenses.single().amountCents)
        assertEquals(92_500L, runBlocking { app.accountsRepository.observeAccount("yape").first() }!!.businessCents)
        assertTrue(records().isEmpty())
    }

    @Test fun ui16QuickClassificationAndRestoreKeepPersonal() {
        seed("Venta menú", 5000)
        tab("Movimientos")
        waitText("Venta menú")
        compose.onNodeWithContentDescription("Acciones de Venta menú").performClick()
        click("Marcar personal")
        awaitData { records().single().classification == MovementClassification.PERSONAL }
        compose.onNodeWithContentDescription("Acciones de Venta menú").performClick()
        click("Anular")
        awaitData { records().single().classification == MovementClassification.EXCLUDED }
        compose.onNodeWithContentDescription("Acciones de Venta menú").performClick()
        click("Restaurar")
        awaitData { records().single().classification == MovementClassification.PERSONAL }
        assertEquals(5000L, records().single().amountCents)
    }

    @Test fun ui17ManualPersonalWithExplicitAccount() {
        runBlocking { app.accountsRepository.setBalance("cash", 0, 0) }
        newMovement()
        replace("Monto (S/)", "15")
        click("Sin cuenta")
        click("Efectivo")
        click("Personal")
        click("Guardar movimiento")
        awaitData { records().size == 1 }
        assertEquals("cash", records().single().accountId)
        assertEquals(MovementClassification.PERSONAL, records().single().classification)
        assertEquals(1500L, runBlocking { app.accountsRepository.observeAccount("cash").first() }!!.personalCents)
    }

    @Test fun ui18ThreeDigitAmountStaysRightOfName() {
        seed("Venta de importe largo", 10000)
        tab("Movimientos")
        waitText("Venta de importe largo")
        val name = compose.onNodeWithText("Venta de importe largo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithText("+ S/ 100.00", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Las iniciales son decorativas y no tienen semántica de texto para TalkBack.
        val avatar = compose.onNodeWithTag("movement-avatar-${records().single().id}", useUnmergedTree = true)
        avatar.assertIsDisplayed()
        val avatarBounds = avatar.fetchSemanticsNode().boundsInRoot
        assertTrue("El avatar debe conservar su tamaño", avatarBounds.width > 0 && avatarBounds.height > 0)
        assertTrue("El avatar debe quedar antes del nombre", avatarBounds.right <= name.left)
        compose.onNodeWithText("VD").assertDoesNotExist()
        assertTrue("El importe debe quedar a la derecha del nombre", amount.left >= name.right)
    }

    @Test fun ui19GoalTypesShareOneRow() {
        click("Crear plan")
        click("Definir meta")
        val profit = compose.onNodeWithText("Ganancia", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val income = compose.onNodeWithText("Ingresos", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(income.left > profit.right)
        assertEquals(profit.center.y, income.center.y, 2f)
    }

    @Test fun ui22PlinReadyWithoutAccountPicker() {
        tab("Ajustes")
        waitText("Captura de pagos")
        compose.onNodeWithText("Se registra en Plin.").assertDoesNotExist()
        compose.onNodeWithText("Plin · Interbank").assertDoesNotExist()
        compose.onNodeWithText("Cambiar cuenta").assertDoesNotExist()
        compose.onNodeWithText("Elegir cuenta de Interbank").assertDoesNotExist()
        compose.onNodeWithText("Plin llega a tu banco").assertDoesNotExist()
        tab("Cuentas")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plin"))
        waitText("Plin")
        compose.onNodeWithText("Plin").assertIsDisplayed()
    }

    @Test fun ui20UndoPaymentFromPlanAndRemoveExpense() {
        val expense = runBlocking {
            app.accountsRepository.setBalance("yape", 10000, 0)
            app.monthlyPlanRepository.addExpense(YearMonth.now(), "Deuda prueba", 4000).also {
                app.accountsRepository.payExpense(it, YearMonth.now(), "yape", 4000)
            }
        }
        click("Ver plan")
        click("Ver pagos")
        waitText("Pagos de Deuda prueba")
        click("Anular pago")
        waitText("¿Anular pago de S/ 40.00?")
        compose.onAllNodes(hasText("Anular pago") and hasClickAction()).onLast().performClick()
        awaitData { runBlocking { app.accountsRepository.observeExpensePayments(YearMonth.now()).first().single().remainingCents == 4000L } }
        waitText("Pago anulado")
        click("Listo")
        click("Quitar")
        waitText("Quitar gasto")
        compose.onAllNodes(hasText("Quitar") and hasClickAction()).onLast().performClick()
        awaitData { currentPlan().expenses.none { it.id == expense } }
        assertEquals(10000L, runBlocking { app.accountsRepository.observeAccount("yape").first() }!!.businessCents)
    }

    @Test fun ui21ReportsOpenAtTopAfterScrollingHome() {
        click("Ver reportes")
        compose.onNodeWithText("Día").assertIsDisplayed()
        compose.onNodeWithText("Semana").assertIsDisplayed()
        compose.onNodeWithText("Mes").assertIsDisplayed()
        systemBack()
        compose.onNode(tabMatcher("Inicio")).assertIsSelected()
    }

    @Test fun ui23PrivacyAvailableOfflineAndBackReturnsToSettings() {
        tab("Ajustes")
        click("Leer política de privacidad")
        waitText("Política de privacidad")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("soporte@yerry.me", substring = true))
        compose.onNodeWithText("soporte@yerry.me", substring = true).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitText("Política de privacidad")
        systemBack()
        compose.onNode(tabMatcher("Ajustes")).assertIsSelected()
    }

    @Test fun ui24CaptureDisclosureCanBeDeclinedWithoutLeavingApp() {
        tab("Ajustes")
        click("Dar acceso a notificaciones")
        waitText("Acceso a tus notificaciones")
        compose.onNodeWithText("incluso cuando no estés viendo la app", substring = true).assertIsDisplayed()
        click("Ahora no")
        compose.onNodeWithText("Acceso a tus notificaciones").assertDoesNotExist()
        compose.onNode(tabMatcher("Ajustes")).assertIsSelected()
    }

    /** Solo datos ficticios de la aplicación aislada de pruebas, nunca datos del teléfono. */
    @Test fun ui25PlayScreenshotsWithFictionalData() {
        waitText("Ingresos del mes")
        runBlocking {
            app.accountsRepository.setBalance("yape", 78_000, 5_000)
            app.accountsRepository.setBalance("cash", 24_000, 0)
            app.monthlyPlanRepository.addExpense(YearMonth.now(), "Alquiler", 30_000)
            app.monthlyPlanRepository.setMonthlyGoal(YearMonth.now(), 90_000)
        }
        seed("Venta mostrador", 12_000)
        seed("Pedido de ejemplo", 8_500)
        seed("Venta de la tarde", 16_000)
        seed("Cobro personal", 5_000, MovementClassification.PERSONAL)
        awaitData { records().size == 4 }
        // Separar un fallo de datos de un timeout del selector de UI.
        assertEquals(36_500L, com.lyape.domain.TotalsCalculator.totalFor(records()).businessCents)
        // Espera a una cifra real del resumen antes de capturar, sin sleeps arbitrarios.
        waitText("S/ 365.00", substring = true)
        playScreenshot("01-inicio")
        tab("Movimientos")
        // La fila más antigua puede no estar compuesta aún en LazyColumn. Buscar mediante
        // su scroll semántico y volver al inicio para la captura, no esperar fuera de pantalla.
        waitForNode(hasScrollToIndexAction())
        val history = compose.onNode(hasScrollToIndexAction())
        history.performScrollToNode(hasText("Venta mostrador"))
        compose.onNodeWithText("Venta mostrador").assertIsDisplayed()
        history.performScrollToIndex(0)
        playScreenshot("02-movimientos")
        tab("Cuentas")
        waitText("Yape")
        playScreenshot("03-cuentas")
        tab("Inicio")
        click("Ver reportes")
        waitText("Mes")
        playScreenshot("04-reportes")
    }

    private fun playScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // AGP recoge este directorio ANTES de desinstalar el APK de pruebas.
        // La ruta depende de la API; no asumir /sdcard/Android/data en el workflow.
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.getExternalFilesDir(null), "test_data")
        val directory = File(output, "play-screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun resetData() = runBlocking(Dispatchers.IO) {
        app.database.clearAllTables()
        app.preferences.setCapturePaused(false)
    }

    private fun records() = runBlocking { app.movementRepository.testMovements() }
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
        compose.onNode(tabMatcher("Inicio")).assertDoesNotExist()
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
        waitForNode(hasSetTextAction() and hasText(label) and isEnabled())
        val node = field(label)
        if (scroll) node.performScrollTo()
        node.performTextReplacement(value)
        compose.waitForIdle()
    }
    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        waitForNode(matcher)
        val node = compose.onNode(matcher)
        // Los botones de AlertDialog/menús no pertenecen necesariamente a un scroll.
        val scrollParent = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        if (compose.onAllNodes(matcher and scrollParent).fetchSemanticsNodes().isNotEmpty()) {
            node.performScrollTo()
        }
        node.assertIsDisplayed().performClick()
    }
    private fun systemBack() {
        hideKeyboard()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun hideKeyboard() {
        // Con el IME abierto, el primer Atrás puede solo ocultarlo: aquí comprobamos la salida
        // del formulario, no el cierre del teclado. Los valores deben seguir intactos.
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            activity.currentFocus?.clearFocus()
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10_000) {
            var hidden = false
            compose.activityRule.scenario.onActivity { activity ->
                hidden = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            hidden
        }
        compose.waitForIdle()
    }
    private fun waitText(text: String, substring: Boolean = false) {
        waitForNode(hasText(text, substring = substring))
    }
    private fun waitForNode(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            // Solo datos ficticios: el reporte CI debe mostrar qué estaba en pantalla.
            val visibleText = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes().joinToString("\n") { it.config[SemanticsProperties.Text].toString() }
            throw AssertionError("No apareció: $matcher\nTextos de la pantalla:\n$visibleText", timeout)
        }
    }
    private fun awaitData(condition: () -> Boolean) = compose.waitUntil(10_000, condition)
    private fun waitAddAction() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Agregar").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
