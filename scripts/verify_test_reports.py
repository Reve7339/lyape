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
        "com.lyape.acceptance.BusinessRulesAcceptanceTest": {
            "bdd01PersonalAndExcludedDoNotInflateIncome",
            "bdd02ExpensesAndGoalAreIndependent",
            "bdd03AnnualCentsAreNotLost",
            "bdd04RefundLowersMonthlyCurve",
            "bdd05MonthBoundaryUsesLocalTime",
            "bdd06PersonalRestoresAsPersonal",
            "bdd07NegativeAdjustmentRequiresReason",
        },
    },
    "android": {
        "com.lyape.acceptance.BusinessJourneyAcceptanceTest": {
            "bdd08DuplicateDeliveryAndPauseResume",
            "bdd09ReconciliationCloseAndReopenKeepIncome",
            "bdd10RecurringPlanKeepsExtrasInTheirMonth",
            "bdd11GoalModesPreserveEachConfiguration",
        },
        "com.lyape.ui.LyapeUserJourneysTest": {
            "ui01RootNavigationAndEmptyState",
            "ui02CreateManualPaymentThroughForm",
            "ui03EditAndClassifyUpdatesSummary",
            "ui04ExcludeAndRestorePersonalWithoutDeleting",
            "ui05InvalidAmountDoesNotSave",
            "ui06NegativeAdjustmentRequiresAndDisplaysReason",
            "ui07BackConfirmsOnlyDirtyForms",
            "ui08RecreationKeepsUnsavedValuesAndExitGuard",
            "ui09SearchCombinesWithClassificationFilter",
            "ui10CloseAndReopenDayWithoutChangingMoney",
            "ui11PlanDialogsSaveAndConfirmGoalRemoval",
        },
        "com.lyape.data.PersistenceRestartIntegrationTest": {
            "file01ReopenKeepsMovementsAndMonthlyPlan",
            "file02ReopenPreferencesKeepsCapturePause",
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
