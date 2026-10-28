package com.lyape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lyape.R
import com.lyape.ui.accounts.AccountsScreen
import com.lyape.ui.accounts.AccountDetailScreen
import com.lyape.ui.accounts.AccountFormScreen
import com.lyape.ui.monthlyplan.ExpensePaymentScreen
import com.lyape.ui.dashboard.DashboardScreen
import com.lyape.ui.movementdetail.MovementDetailScreen
import com.lyape.ui.movementform.AddMovementScreen
import com.lyape.ui.movementform.EditMovementScreen
import com.lyape.ui.monthlyplan.MonthlyPlanScreen
import com.lyape.ui.movements.MovementsScreen
import com.lyape.ui.navigation.BottomTab
import com.lyape.ui.navigation.Destination
import com.lyape.ui.navigation.ExitDecision
import com.lyape.ui.navigation.NavState
import com.lyape.ui.navigation.NavStateSaver
import com.lyape.ui.navigation.NavigationRequests
import com.lyape.ui.navigation.exitDecision
import com.lyape.ui.navigation.isMovementForm
import com.lyape.ui.navigation.isRootDestination
import com.lyape.ui.settings.SettingsScreen
import com.lyape.ui.settings.PrivacyScreen

/**
 * Raíz de la interfaz: barra superior, contenido por destino y barra inferior de pestañas.
 */
@Composable
fun LyapeApp(modifier: Modifier = Modifier) {
    var navState by rememberSaveable(stateSaver = NavStateSaver) { mutableStateOf(NavState()) }
    var movementsSearchActive by rememberSaveable { mutableStateOf(false) }
    var movementsQuery by rememberSaveable { mutableStateOf("") }
    var formDirty by rememberSaveable { mutableStateOf(false) }
    var formSaving by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    var pendingSettings by rememberSaveable { mutableStateOf(false) }

    val destination = navState.current

    LaunchedEffect(destination) {
        formDirty = false
        formSaving = false
        confirmingDiscard = false
    }

    val requestSettings: () -> Unit = {
        when (exitDecision(destination, formDirty, formSaving)) {
            ExitDecision.LEAVE -> {
                pendingSettings = false
                navState = navState.selectTab(BottomTab.SETTINGS)
            }
            ExitDecision.CONFIRM -> {
                pendingSettings = true
                confirmingDiscard = true
            }
            ExitDecision.STAY -> pendingSettings = true
        }
    }
    val latestRequestSettings = rememberUpdatedState(requestSettings)
    LaunchedEffect(Unit) {
        NavigationRequests.openSettings.collect {
            latestRequestSettings.value()
        }
    }

    // Una solicitud recibida mientras se guardaba espera al resultado; si el formulario sigue
    // sucio, se pide confirmación en vez de saltar a Ajustes y perder los cambios.
    LaunchedEffect(pendingSettings, formSaving, formDirty, destination) {
        if (!pendingSettings || formSaving) return@LaunchedEffect
        when (exitDecision(destination, formDirty, formSaving)) {
            ExitDecision.LEAVE -> {
                pendingSettings = false
                navState = navState.selectTab(BottomTab.SETTINGS)
            }
            ExitDecision.CONFIRM -> confirmingDiscard = true
            ExitDecision.STAY -> Unit
        }
    }

    fun finishForm() {
        confirmingDiscard = false
        formDirty = false
        formSaving = false
        if (pendingSettings) {
            pendingSettings = false
            navState = navState.selectTab(BottomTab.SETTINGS)
        } else {
            navState = navState.back()
        }
    }

    val onBackRequested: () -> Unit = {
        when (exitDecision(destination, formDirty, formSaving)) {
            ExitDecision.CONFIRM -> confirmingDiscard = true
            ExitDecision.LEAVE -> navState = navState.back()
            ExitDecision.STAY -> Unit
        }
    }

    BackHandler(enabled = navState.canGoBack, onBack = onBackRequested)

    val action: TopBarAction? = when (destination) {
        Destination.Movements -> if (movementsSearchActive) {
            TopBarAction(Icons.Filled.Close, "Cerrar búsqueda") {
                movementsSearchActive = false
                movementsQuery = ""
            }
        } else {
            TopBarAction(Icons.Filled.Search, "Buscar movimientos") {
                movementsSearchActive = true
            }
        }
        Destination.Summary,
        Destination.DailyClose,
        Destination.Settings,
        Destination.Privacy,
        Destination.AddMovement,
        is Destination.MovementDetail,
        is Destination.MovementEdit,
        is Destination.MonthlyPlan,
        Destination.Accounts,
        Destination.Reports,
        is Destination.AccountDetail,
        is Destination.AccountForm,
        is Destination.ExpensePayment,
        -> null
    }

    val secondaryTitle: String? = when (destination) {
        Destination.Privacy -> androidx.compose.ui.res.stringResource(com.lyape.R.string.privacy_policy_title)
        is Destination.MovementDetail -> "Detalle del movimiento"
        Destination.AddMovement -> "Nuevo movimiento"
        is Destination.MovementEdit -> "Editar movimiento"
        is Destination.MonthlyPlan -> "Plan mensual"
        Destination.DailyClose -> null // Compatibilidad con estados guardados antiguos.
        Destination.Reports -> "Reportes"
        is Destination.AccountDetail -> "Detalle de cuenta"
        is Destination.AccountForm -> when (destination.mode) {
            "add" -> "Nueva cuenta"
            "balance" -> "Saldo actual"
            "transfer" -> "Transferencia"
            else -> "Registrar salida"
        }
        is Destination.ExpensePayment -> "Registrar pago"
        else -> null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LyapeTopBar(
                title = secondaryTitle,
                action = action,
                onBack = if (secondaryTitle != null) onBackRequested else null,
                backEnabled = !formSaving,
            )
        },
        bottomBar = {
            if (destination.isRootDestination) {
                LyapeBottomBar(
                    selected = navState.selectedTab,
                    onSelect = { tab ->
                        if (tab != BottomTab.MOVEMENTS) {
                            movementsSearchActive = false
                            movementsQuery = ""
                        }
                        navState = navState.selectTab(tab)
                    },
                )
            }
        },
    ) { innerPadding ->
        // Las raíces ya tienen el inset inferior en la barra. En secundarios, consumir primero
        // navegación permite que imePadding añada solo el espacio restante del teclado.
        val horizontalSafe = Modifier.windowInsetsPadding(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
        )
        val contentInsets = if (destination.isRootDestination) horizontalSafe else horizontalSafe
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .imePadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(contentInsets),
        ) {
            when (destination) {
                Destination.Summary, Destination.Reports -> DashboardScreen(
                    reportsOnly = destination == Destination.Reports,
                    onConfigurePlan = { month ->
                        navState = navState.open(Destination.MonthlyPlan(month.toString()))
                    },
                    onOpenAccounts = { navState = navState.selectTab(BottomTab.ACCOUNTS) },
                    onOpenAccount = { navState = navState.open(Destination.AccountDetail(it)) },
                    onOpenReports = { navState = navState.open(Destination.Reports) },
                )
                Destination.Movements, Destination.DailyClose -> MovementsScreen(
                    onOpenMovement = { id -> navState = navState.open(Destination.MovementDetail(id)) },
                    onAddManual = { navState = navState.open(Destination.AddMovement) },
                    searchActive = movementsSearchActive,
                    searchQuery = movementsQuery,
                    onSearchQueryChange = { movementsQuery = it },
                    onEditMovement = { navState = navState.open(Destination.MovementEdit(it)) },
                )
                is Destination.MovementDetail -> MovementDetailScreen(
                    movementId = destination.movementId,
                    onEdit = {
                        navState = navState.open(Destination.MovementEdit(destination.movementId))
                    },
                )
                Destination.AddMovement -> AddMovementScreen(
                    onSaved = { finishForm() },
                    onDirtyChange = { formDirty = it },
                    onSavingChange = { formSaving = it },
                )
                is Destination.MovementEdit -> EditMovementScreen(
                    movementId = destination.movementId,
                    onSaved = { finishForm() },
                    onDirtyChange = { formDirty = it },
                    onSavingChange = { formSaving = it },
                )
                is Destination.MonthlyPlan -> MonthlyPlanScreen(
                    initialMonth = destination.month,
                    onPayExpense = { expenseId, month ->
                        navState = navState.openExpensePayment(expenseId, month)
                    },
                )
                Destination.Accounts -> AccountsScreen(
                    onOpenAccount = { navState = navState.open(Destination.AccountDetail(it)) },
                    onAddAccount = { navState = navState.open(Destination.AccountForm("add")) },
                    onTransfer = { navState = navState.open(Destination.AccountForm("transfer")) },
                    onWithdraw = { navState = navState.open(Destination.AccountForm("withdraw")) },
                )
                is Destination.AccountDetail -> AccountDetailScreen(
                    accountId = destination.accountId,
                    onSetBalance = { navState = navState.open(Destination.AccountForm("balance", destination.accountId)) },
                    onTransfer = { navState = navState.open(Destination.AccountForm("transfer", destination.accountId)) },
                    onWithdraw = { navState = navState.open(Destination.AccountForm("withdraw", destination.accountId)) },
                )
                is Destination.AccountForm -> AccountFormScreen(
                    mode = destination.mode,
                    accountId = destination.accountId,
                    onSaved = { finishForm() },
                    onDirtyChange = { formDirty = it },
                    onSavingChange = { formSaving = it },
                )
                is Destination.ExpensePayment -> ExpensePaymentScreen(
                    expenseId = destination.expenseId,
                    month = destination.month,
                    onSaved = { finishForm() },
                    onDirtyChange = { formDirty = it },
                    onSavingChange = { formSaving = it },
                )
                Destination.Settings -> SettingsScreen(
                    onOpenMovements = { navState = navState.selectTab(BottomTab.MOVEMENTS) },
                    onOpenAccounts = { navState = navState.selectTab(BottomTab.ACCOUNTS) },
                    onOpenPrivacy = { navState = navState.open(Destination.Privacy) },
                )
                Destination.Privacy -> PrivacyScreen()
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = {
                if (!formSaving) {
                    confirmingDiscard = false
                    pendingSettings = false
                }
            },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Los cambios que hiciste no se guardarán.") },
            confirmButton = {
                TextButton(
                    enabled = !formSaving,
                    onClick = {
                        confirmingDiscard = false
                        formDirty = false
                        if (pendingSettings) {
                            pendingSettings = false
                            navState = navState.selectTab(BottomTab.SETTINGS)
                        } else {
                            navState = navState.back()
                        }
                    },
                ) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !formSaving,
                    onClick = {
                        confirmingDiscard = false
                        pendingSettings = false
                    },
                ) {
                    Text("Seguir editando")
                }
            },
        )
    }
}

private data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
)

@Composable
private fun LyapeTopBar(
    title: String?,
    action: TopBarAction?,
    onBack: (() -> Unit)?,
    backEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.statusBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                    )
                }
                Text(
                    text = title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_lyape_logo),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "LYape",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (action != null) {
                    TopBarActionButton(action)
                }
            }
        }
    }
}

@Composable
private fun TopBarActionButton(action: TopBarAction) {
    IconButton(onClick = action.onClick) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.description,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LyapeBottomBar(
    selected: BottomTab?,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTab.entries.forEach { tab ->
                BottomTabItem(
                    tab = tab,
                    selected = tab == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = iconFor(tab),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun iconFor(tab: BottomTab): ImageVector = when (tab) {
    BottomTab.SUMMARY -> Icons.Filled.Home
    BottomTab.MOVEMENTS -> Icons.AutoMirrored.Filled.List
    BottomTab.ACCOUNTS -> ImageVector.vectorResource(R.drawable.ic_accounts_wallet)
    BottomTab.SETTINGS -> Icons.Filled.Settings
}
