package com.lyape.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementCounts
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.components.MovementRow
import com.lyape.ui.model.MovementUi
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.flow.first

internal enum class MovementFilter(val label: String, val classification: MovementClassification?) {
    ALL("Todos", null),
    BUSINESS("Negocio", MovementClassification.BUSINESS),
    PERSONAL("Personal", MovementClassification.PERSONAL),
    EXCLUDED("Anulado", MovementClassification.EXCLUDED),
}

internal fun MovementCounts.filterCounts(): Map<MovementFilter, Int> = mapOf(
    MovementFilter.ALL to total,
    MovementFilter.BUSINESS to business,
    MovementFilter.PERSONAL to personal,
    MovementFilter.EXCLUDED to excluded,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MovementsScreen(
    onOpenMovement: (String) -> Unit,
    onAddManual: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEditMovement: (String) -> Unit = onOpenMovement,
    viewModel: MovementsViewModel = viewModel(),
) {
    var filter by rememberSaveable { mutableStateOf(MovementFilter.ALL) }
    var retryKey by remember { mutableIntStateOf(0) }
    val countsState by remember(viewModel, retryKey) { viewModel.counts }
        .collectAsStateWithLifecycle(initialValue = MovementCountsState.Loading)
    val movements = viewModel.movements.collectAsLazyPagingItems()
    val busy by viewModel.actions.busy.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.actions.feedback.collect { snackbars.showSnackbar(it) } }
    val normalizedQuery = remember(searchQuery) { normalizeForSearch(searchQuery) }
    val totals = (countsState as? MovementCountsState.Content)?.counts ?: MovementCounts()
    val counts = remember(totals) { totals.filterCounts() }
    val retry = {
        retryKey += 1
        movements.retry()
    }
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(filter, normalizedQuery) {
        viewModel.updateQuery(filter.classification, normalizedQuery)
        listState.scrollToItem(0)
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            listState.scrollToItem(0)
            // La fila debe estar compuesta antes de solicitar el foco.
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == "search" } }
                .first { it }
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 17.dp,
                end = 17.dp,
                top = 8.dp,
                bottom = 96.dp,
            ),
        ) {
            item(key = "title") {
                Text(
                    text = "Movimientos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (searchActive) {
                item(key = "search") {
                    Spacer(Modifier.height(12.dp))
                    SearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        focusRequester = searchFocusRequester,
                    )
                }
            }

            item(key = "filters") {
                Spacer(Modifier.height(14.dp))
                FiltersRow(
                    selected = filter,
                    counts = counts,
                    onSelect = { filter = it },
                )
            }

            item(key = "gap") { Spacer(Modifier.height(16.dp)) }

            when {
                movements.itemCount > 0 -> {
                    if (movements.loadState.prepend is LoadState.Loading) {
                        item(key = "prepend-loading") { LoadingMessage() }
                    } else if (movements.loadState.prepend is LoadState.Error) {
                        item(key = "prepend-error") { ErrorMessage(onRetry = retry) }
                    }
                    items(
                        count = movements.itemCount,
                        key = movements.itemKey { it.id },
                    ) { index ->
                        movements[index]?.let { movement ->
                            MovementListItem(
                                movement = movement,
                                isFirst = index == 0,
                                isLast = index == movements.itemCount - 1,
                                onClick = { onOpenMovement(movement.id) },
                                busy = movement.id in busy,
                                onAction = { viewModel.actions.perform(movement.id, it) },
                                onEdit = { onEditMovement(movement.id) },
                            )
                        }
                    }
                    // Si falla otra página, se conservan visibles las filas ya cargadas.
                    when {
                        movements.loadState.append is LoadState.Error ||
                            movements.loadState.refresh is LoadState.Error ||
                            countsState is MovementCountsState.Error ->
                            item(key = "append-error") { ErrorMessage(onRetry = retry) }
                        movements.loadState.append is LoadState.Loading ||
                            movements.loadState.refresh is LoadState.Loading ->
                            item(key = "append-loading") { LoadingMessage() }
                    }
                }

                movements.loadState.refresh is LoadState.Error || countsState is MovementCountsState.Error ->
                    item(key = "error") {
                        LyapeCard(contentPadding = PaddingValues(0.dp)) { ErrorMessage(onRetry = retry) }
                    }

                movements.loadState.refresh is LoadState.Loading || countsState is MovementCountsState.Loading ->
                    item(key = "loading") {
                        LyapeCard(contentPadding = PaddingValues(0.dp)) { LoadingMessage() }
                    }

                totals.total == 0 -> item(key = "empty") {
                    LyapeCard(contentPadding = PaddingValues(0.dp)) {
                        Message(
                            "Todavía no hay movimientos. Activa el acceso a las notificaciones "
                                + "en Ajustes para capturar pagos automáticamente.",
                        )
                    }
                }

                normalizedQuery.isNotEmpty() -> item(key = "no-results") {
                    LyapeCard(contentPadding = PaddingValues(0.dp)) {
                        Message("No encontramos movimientos que coincidan con tu búsqueda.")
                    }
                }

                else -> item(key = "no-filter-match") {
                    LyapeCard(contentPadding = PaddingValues(0.dp)) {
                        Message("No hay movimientos para este filtro.")
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddManual,
            icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
            text = { Text("Agregar") },
            shape = PillShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 17.dp, bottom = 16.dp)
                // Material3 borra las semánticas del contenido del botón extendido, así que su
                // etiqueta visible no llega a TalkBack; se declara el nombre aquí.
                .semantics { contentDescription = "Agregar" },
        )
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}

/** Fila del historial con el aspecto de la tarjeta original, sin anidar desplazamientos. */
@Composable
private fun MovementListItem(
    movement: MovementUi,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    busy: Boolean,
    onAction: (MovementAction) -> Unit,
    onEdit: () -> Unit,
) {
    val shape = listSegmentShape(isFirst, isLast)
    var menu by remember(movement.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        MovementRow(movement, onClick, actions = {
        Box {
            IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Acciones de ${movement.title}")
            }
            DropdownMenu(expanded = menu && !busy, onDismissRequest = { menu = false }) {
                if (movement.isExcluded) {
                    DropdownMenuItem(text = { Text("Restaurar") }, onClick = { menu = false; onAction(MovementAction.RESTORE) })
                } else {
                    val personal = movement.classification != MovementClassification.PERSONAL
                    DropdownMenuItem(text = { Text(if (personal) "Marcar personal" else "Marcar negocio") },
                        onClick = { menu = false; onAction(if (personal) MovementAction.PERSONAL else MovementAction.BUSINESS) })
                    DropdownMenuItem(text = { Text("Anular") }, onClick = { menu = false; onAction(MovementAction.EXCLUDE) })
                }
                DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() })
            }
        }
        })
    }
}

/** Esquinas redondeadas solo en los extremos del grupo. Coincide con `shapes.large` del tema. */
private val MovementCardCorner = 24.dp

private fun listSegmentShape(isFirst: Boolean, isLast: Boolean): Shape = when {
    isFirst && isLast -> RoundedCornerShape(MovementCardCorner)
    isFirst -> RoundedCornerShape(topStart = MovementCardCorner, topEnd = MovementCardCorner)
    isLast -> RoundedCornerShape(bottomStart = MovementCardCorner, bottomEnd = MovementCardCorner)
    else -> RoundedCornerShape(0.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersRow(
    selected: MovementFilter,
    counts: Map<MovementFilter, Int>,
    onSelect: (MovementFilter) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MovementFilter.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text("${option.label} ${counts[option] ?: 0}") },
                shape = PillShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Buscar movimientos") },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
private fun LoadingMessage() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Cargando movimientos…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun ErrorMessage(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "No pudimos leer los movimientos guardados en este dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRetry, shape = PillShape) {
            Text("Reintentar")
        }
    }
}
