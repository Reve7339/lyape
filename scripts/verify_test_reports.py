"""Impide dar por verdes ejecuciones vacías o sin los escenarios BDD obligatorios.

Usa únicamente la biblioteca estándar. Lee los XML JUnit producidos por Gradle,
no los contadores históricos de la documentación.
"""

import argparse
from dataclasses import dataclass
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REQUIRED_SCENARIOS = {
    "unit": {
        "com.lyape.ui.components.MovementRowLayoutTest": {
            "conserva avatar e importe lateral en pantallas compactas",
            "mantiene avatar solo cuando ambas columnas tienen espacio",
            "fuente grande o importe extremo puede apilar sin reducir la letra",
        },
        "com.lyape.domain.ProfitGoalTest": {
            "ganancia 500 gastos 950 e ingresos 1200 requieren 1450",
            "un extra eleva el objetivo pero pagar no cambia el presupuesto",
            "meta de ingresos no suma gastos a su importe",
            "sin gastos la ganancia coincide con ingresos",
            "la perdida mantiene el signo y aumenta lo que falta",
            "coste anual conserva centimos y cambia el objetivo mensual",
            "extremos no producen meta alcanzada ni objetivo saturado",
            "meta antigua sin tipo conserva ingresos",
        },
        "com.lyape.ui.movements.MovementActionsTest": {
            "doble toque guarda una vez y libera la fila",
            "error permite reintentar sin anunciar exito",
            "registro desaparecido no anuncia exito",
        },
        "com.lyape.voice.ServiceStopGateTest": {
            "un cierre viejo no detiene la sesion nueva",
            "inicio concurrente durante parada se ejecuta despues del efecto completo",
            "fallo de inicio se informa sin aceptar el token nuevo",
        },
        "com.lyape.acceptance.BusinessRulesAcceptanceTest": {
            "bdd01PersonalAndExcludedDoNotInflateIncome",
            "bdd02ExpensesAndGoalAreIndependent",
            "bdd03AnnualCentsAreNotLost",
            "bdd04RefundLowersMonthlyCurve",
            "bdd05MonthBoundaryUsesLocalTime",
            "bdd06PersonalRestoresAsPersonal",
            "bdd07NegativeAdjustmentRequiresReason",
        },
        "com.lyape.preferences.AppPreferencesTest": {
            "un fallo de lectura bloquea la captura sin modificar lo guardado",
            "datos corruptos no habilitan la captura",
            "una nueva lectura recupera la pausa persistida tras un fallo temporal",
        },
        "com.lyape.domain.CashTest": {
            "annualRemainingSharesAlwaysSumExactly",
            "monthlyPaymentDoesNotChangeBudget",
            "annualPeriodCannotBePaidAgainInAnotherMonthOfSameYear",
            "rejectsOverpaidBudget",
            "businessAndPersonalAreSeparateAndUnconfiguredIsExplicit",
            "overflowNeverWrapsIntoNegativeMoney",
            "intermediateOverflowDoesNotRejectRepresentableFinalBalance",
            "rejectsPositiveFinalSumOverflow",
            "rejectsNegativeFinalSumOverflow",
            "summaryDoesNotDependOnAccountOrderWhenSignedBalancesCancel",
        },
        "com.lyape.domain.CaptureProviderTest": {
            "yape usa medio Yape y su cuenta integrada",
            "Plin Interbank usa medio Plin y exige una cuenta bancaria",
            "ningun proveedor usa el medio desconocido",
        },
        "com.lyape.ui.settings.CaptureConfigUiStateTest": {
            "un fallo de lectura se presenta como error y no como ausencia de asociacion",
            "sin asociacion es un estado listo con enlace nulo",
            "la asociacion valida se conserva en el estado listo",
        },
        "com.lyape.data.CaptureConfigurationTest": {
            "una cuenta bancaria valida se resuelve solo con datos crudos",
            "una asociacion a Yape no se resuelve para Plin",
            "sin asociacion el enlace es nulo pero la cuenta sigue disponible",
        },
        "com.lyape.notification.CaptureProviderRegistryTest": {
            "el paquete confirmado de Interbank esta habilitado provisionalmente",
            "un proveedor falso habilitado resuelve solo su paquete",
            "un proveedor deshabilitado no captura aunque declare un paquete",
            "el parser Interbank real solo procesa el paquete falso registrado",
        },
        "com.lyape.notification.NotificationParserTest": {
            "rechaza una evidencia mixta con un posible remitente no reconocido en el expandido",
            "rechaza la posicion invertida de una evidencia mixta con remitente no reconocido",
            "acepta un complemento sin remitente que solo repite la frase y el importe",
            "acepta un complemento generico con conector y el mismo importe",
        },
        "com.lyape.notification.NotificationIngestorTest": {
            "un fallo al consultar duplicados no guarda ni produce evento",
            "un fallo al resolver la cuenta no se presenta como cuenta ausente",
            "una cuenta realmente inexistente si es Unconfigured",
            "un fallo de insercion no produce evento Stored",
            "una cuenta invalidada al insertar es Unconfigured y no un fallo generico",
            "otro IllegalArgumentException no se disfraza de cuenta invalida",
            "un fallo del parser es un fallo identificable",
            "una preferencia ilegible mantiene silencio y no guarda",
            "un guardado confirmado expone solo el evento minimo",
            "dos entregas concurrentes producen una fila y un evento",
            "un pago legitimo siguiente no se descarta",
            "una evidencia mixta con remitente no reconocido no guarda ni publica voz",
            "la cancelacion se propaga y nunca se convierte en Stored",
        },
        "com.lyape.notification.CaptureAdmissionTest": {
            "solo acepta paquete confirmado y usuario principal",
            "un resumen de grupo nunca se analiza como un cobro",
        },
        "com.lyape.data.MovementWritesTest": {
            "un alta con submilisegundos usa la precision que persiste Room",
            "un milisegundo distinto sigue siendo contenido distinto",
        },
        "com.lyape.voice.VoiceSessionPresentationTest": {
            "apagada no parece una sesion detenida",
            "preferencia activa sin servicio se presenta como detenida",
            "inicializando no se presenta como activa",
            "sesion lista se presenta como activa",
            "motor fallido tiene estado propio",
            "sin voces tras consulta se presenta como ausencia",
            "preferencia ilegible no se presenta como ausencia de sesion",
            "fallo de guardado mantiene el interruptor y el reintento",
            "aviso bloqueado no se presenta como sesion activa",
            "captura no disponible no se presenta como activa",
        },
        "com.lyape.voice.VoiceSessionCoordinatorTest": {
            "inicio solicitado sin confirmacion queda preparando y no habla",
            "confirmacion y motor listo activan una sola vez",
            "fallo de inicio no deja activo ni conserva recursos",
            "sin confirmacion dentro del plazo se limpia y no queda preparando",
            "una inicializacion TTS bloqueada no deja Preparando para siempre",
            "una confirmacion tardia de A no activa ni detiene B",
            "servicio detenido durante la inicializacion no se activa al completar",
            "servicio detenido por sistema conserva la preferencia y no reproduce historial",
            "desactivar durante una inicializacion bloqueada no espera ni activa tarde",
            "la pausa detiene la sesion sin depender de Ajustes",
            "revocar acceso despues de admitir un pago evita el audio",
            "revocar acceso mientras se espera foco evita la locucion",
            "una preferencia de captura ilegible no inicia sesion",
            "una preferencia de voz ilegible mantiene silencio y no anuncia acumulado",
            "desactivar con la cola llena no espera a vaciarla",
            "aviso bloqueado antes de iniciar no crea motor ni servicio",
            "bloqueo del aviso durante la sesion corta audio y servicio",
            "una rafaga no crea una espera por pago y respeta el limite",
            "la caducidad usa el reloj monotono y no reproduce lo viejo",
            "un cobro interrumpe la prueba y conserva el orden real",
            "un guardado true tardio no revive una sesion apagada",
            "fallo al guardar apagado mantiene silencio y permite reintentar",
            "apagado del servicio durante TTS no deja activo ni duplica liberacion",
            "desactivar desde servicio persiste aunque se cancele el scope del servicio",
            "fallo de escritura desde servicio deja error recuperable y reintento",
            "una orden vieja del servicio no detiene B",
            "desactivar repetido no duplica cierres ni escrituras",
            "una orden diferida de la sesion A no detiene B",
        },
        "com.lyape.voice.VoiceAnnouncementControllerTest": {
            "cancelar preview durante lectura no habla y conserva la sesion",
            "cancelar preview durante preparacion del motor no habla tarde",
            "preview preparandose no reaparece despues de un cobro",
            "cancelar A e iniciar B no permite que A cancele ni duplique B",
            "un callback tardio de A no cancela ni duplica la prueba B",
        },
        "com.lyape.voice.VoiceAnnouncementQueueTest": {
            "remove retira solo la prueba y conserva el cobro",
        },
        "com.lyape.ui.capture.CaptureStatusTest": {
            "con acceso pero listener desconectado no se presenta como activa",
        },
    },
    "android": {
        "com.lyape.ui.movements.MovementPagesTest": {
            "accountChangesAndReturningReusePageSafely",
        },
        "com.lyape.data.MonthlyPlanIntegrationTest": {
            "cambiarFrecuenciaConservaTipoEImporteDeCadaMeta",
        },
        "com.lyape.voice.VoiceNotificationChannelTest": {
            "crearElCanalNoEvadeUnBloqueoExistente",
            "canalNuevoEsSilenciosoYNoEstaBloqueado",
        },
        "com.lyape.acceptance.BusinessJourneyAcceptanceTest": {
            "bdd08DuplicateDeliveryAndPauseResume",
            "bdd09ReconciliationCloseAndReopenKeepIncome",
            "bdd10RecurringPlanKeepsExtrasInTheirMonth",
            "bdd11GoalModesPreserveEachConfiguration",
        },
        "com.lyape.ui.LyapeUserJourneysTest": {
            "ui01RootNavigationAndEmptyState",
            "ui16QuickClassificationAndRestoreKeepPersonal",
            "ui17ManualPersonalWithExplicitAccount",
            "ui02CreateManualPaymentThroughForm",
            "ui03EditAndClassifyUpdatesSummary",
            "ui04ExcludeAndRestorePersonalWithoutDeleting",
            "ui05InvalidAmountDoesNotSave",
            "ui06NegativeAdjustmentRequiresAndDisplaysReason",
            "ui07BackConfirmsOnlyDirtyForms",
            "ui08RecreationKeepsUnsavedValuesAndExitGuard",
            "ui09SearchCombinesWithClassificationFilter",
            "ui10NoDailyClosingRequired",
            "ui18ThreeDigitAmountStaysRightOfName",
            "ui19GoalTypesShareOneRow",
            "ui20UndoPaymentFromPlanAndRemoveExpense",
            "ui21ReportsOpenAtTopAfterScrollingHome",
            "ui22PlinReadyWithoutAccountPicker",
            "ui23PrivacyAvailableOfflineAndBackReturnsToSettings",
            "ui24CaptureDisclosureCanBeDeclinedWithoutLeavingApp",
            "ui25PlayScreenshotsWithFictionalData",
            "ui11PlanDialogsSaveAndConfirmGoalRemoval",
            "ui12HistorySearchReachesBeyondFirstPage",
            "ui13ConfigureAccountWithoutChangingIncome",
            "ui14TransferMovesBalanceWithoutCreatingIncome",
            "ui15PartialExpensePaymentPreservesBudgetAndIncome",
        },
        "com.lyape.data.PersistenceRestartIntegrationTest": {
            "file01ReopenKeepsMovementsAndMonthlyPlan",
            "file02ReopenPreferencesKeepsCapturePause",
        },
        "com.lyape.data.HistoryPagingIntegrationTest": {
            "history01PagesAreBoundedStableAndComplete",
            "history02SearchAndFilterReachRecordsBeyondFirstPage",
            "history03SearchIsLiteralAndIncludesDescriptionsAndReasons",
            "history04CountsAndTotalsCoverEntirePeriodNotJustPage",
            "history05EditsInvalidatePagesAndRebuildSearch",
            "history06ClassificationChangesInvalidateFilteredPages",
        },
        "com.lyape.data.AppDatabaseMigrationTest": {
            "migrar7a8ConservaAmbasMetasComoIngresos",
            "historyMigration04To05PreservesRowsAndBackfillsSearch",
            "accountsMigration05To06PreservesHistoryWithoutInventingBalances",
            "captureMigration06To07SeparatesMediumAndAssociatesYape",
        },
        "com.lyape.data.ReconciliationIntegrationTest": {
            "unAltaConSubmilisegundosSeReconoceTrasPersistirSinDuplicar",
            "unaEdicionConSubmilisegundosSeReconoceComoYaAplicada",
            "reutilizarElIntentoDeEdicionConOtroMilisegundoOImporteSigueSiendoConflicto",
        },
        "com.lyape.data.CaptureAccountsIntegrationTest": {
            "yapeYPlinTienenCuentaPorDefectoSinConfiguracionManual",
            "inicializacionConcurrenteNoDuplicaCuentasNiReiniciaSaldos",
            "actualizacionConservaCuentaPlinPreviaSinCrearOtra",
            "plinSoloSeAsociaAUnaCuentaBancariaExistente",
            "cambiarLaAsociacionNoReescribeElHistorialNiLosSaldos",
            "saldoBaseClasificacionYAnulacionConservanLosBuckets",
            "cuentaValidaSeResuelveAunqueFalleSuCalculoDeSaldo",
            "laDobleEntregaConcurrenteDelProveedorFalsoGuardaUnaSolaFila",
            "unPagoYapeConservaSuMedioYCuentaExplicita",
            "primerPagoPlinSeRegistraSinElegirCuentaYNuncaEnYape",
            "conAsociacionInterbankGuardaMedioYCuentaBancaria",
            "unaCuentaAsociadaEliminadaBloqueaNuevasCapturasSinBorrarLaAsociacion",
        },
        "com.lyape.data.AccountsIntegrationTest": {
            "undoPaidExpenseReopensDebtAndAllowsDeletion",
            "undoExpenseBeforeNewBalanceDoesNotCreditTwice",
            "expenseHistoryIncludesOtherPeriodsAndHasLimit",
            "profitGoalDoesNotMoveWhenExpenseIsPaid",
            "manualPersonalGoesToChosenAccountNotBusinessIncome",
            "defaultsDoNotInferHistoricalBalance",
            "sameMillisecondExistingMovementIsNotCountedTwiceButNewDeliveryIs",
            "classificationExclusionAndEditsUpdateOnlyCorrectBucket",
            "transferConservesMoneyAndIncomeAndUndoKeepsAudit",
            "paymentChangesCashAndUnpaidButNotPlannedCostOrIncome",
            "personalFundsDoNotPayBusinessExpenseAndConcurrentPaymentsCannotOverspend",
            "annualPaymentSharedAcrossMonthsButMonthlyCostRetained",
            "reconciliationDoesNotReplayLedgerAndDisallowsUnsafeUndo",
            "aggregateOverflowRollsBackCorrection",
            "assigningAndClearingAccountPreservesIncomeAndTraceability",
            "undoTransferRejectsSpentDestinationFundsWithoutPartialChange",
            "undoOverflowAcrossAccountsRollsBackVoiding",
            "maxBaselineWithdrawalAndReceiptRemainReadable",
            "paymentObserverReadsBudgetAndPaymentsFromSameSnapshot",
        },
        "com.lyape.notification.CapturePipelineIntegrationTest": {
            "proveedorInterbankDeProduccionGuardaUnaFilaYUnEventoConCentimos",
            "resumenDeGrupoSeDescartaAntesDelParser",
            "unFalloDePreferenciasImpideGuardarUnPago",
            "descartaOtraAplicacionAntesDeLeerElContenido",
            "unProveedorFalsoSeFiltraPorSuPaqueteAntesDeLeerExtras",
            "unaNotificacionValidaGuardaExactamenteUnMovimiento",
            "lasEntregasConcurrentesGuardanSoloUnMovimiento",
        },
    },
}

DEFAULT_REPORTS = {
    "unit": "app/build/test-results/testDebugUnitTest",
    "android": "app/build/outputs/androidTest-results/connected",
}


class QualityError(ValueError):
    """La ejecución no cumple la puerta de calidad."""


@dataclass(frozen=True)
class ReportSummary:
    tests: int
    scenarios: int
    skipped: int


def verify_reports(paths, required):
    paths = list(paths)
    if not paths:
        raise QualityError("No se encontraron reportes JUnit XML.")

    executed = set()
    tests = skipped = 0
    for path in paths:
        try:
            root = ET.parse(path).getroot()
            for suite in root.iter("testsuite"):
                if int(suite.get("failures", "0")) or int(suite.get("errors", "0")):
                    raise QualityError(f"Hay fallos en {path}.")
            for case in root.iter("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    raise QualityError(f"Falló {case.get('name')} en {path}.")
                if case.find("skipped") is not None:
                    skipped += 1
                    continue
                tests += 1
                executed.add((case.get("classname"), case.get("name")))
        except (OSError, ET.ParseError, ValueError) as error:
            raise QualityError(f"Reporte no válido: {path}: {error}") from error

    expected = {(name, method) for name, methods in required.items() for method in methods}
    missing = expected - executed
    if missing:
        names = ", ".join(f"{name}.{method}" for name, method in sorted(missing))
        raise QualityError(f"Escenarios obligatorios ausentes u omitidos: {names}")
    if tests == 0:
        raise QualityError("No se ejecutó ninguna prueba.")
    return ReportSummary(tests=tests, scenarios=len(expected), skipped=skipped)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=REQUIRED_SCENARIOS)
    parser.add_argument("--reports", type=Path)
    args = parser.parse_args(argv)
    directory = args.reports or Path(DEFAULT_REPORTS[args.kind])
    try:
        summary = verify_reports(directory.rglob("*.xml"), REQUIRED_SCENARIOS[args.kind])
    except QualityError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(
        f"{args.kind}: {summary.tests} pruebas ejecutadas; "
        f"{summary.scenarios} escenarios obligatorios; {summary.skipped} omitidas."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
