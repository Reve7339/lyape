"""Runner local ligero: mismos APK y AndroidJUnitRunner, sin Gradle junto al AVD."""

import argparse
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

from scripts.verify_test_reports import REQUIRED_SCENARIOS, verify_reports


def parse_results(text):
    fields, totals, cases, seen, started = {}, set(), [], set(), set()
    final_code = None
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if separator:
                fields[key] = value
                if key == "numtests":
                    totals.add(int(value))
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(":", 1)[1])
            if code == 1:
                identity = (fields.get("class"), fields.get("test"))
                if not all(identity) or identity in started or identity in seen:
                    raise ValueError("Inicio de caso sin identidad o repetido.")
                started.add(identity)
            if code not in (1, 2):
                if code not in (0, -1, -2, -3, -4):
                    raise ValueError(f"Estado de test desconocido: {code}")
                identity = (fields.get("class"), fields.get("test"))
                if not all(identity) or identity in seen:
                    raise ValueError("Caso sin identidad o repetido.")
                if code == 0 and identity not in started:
                    raise ValueError("Final de test sin su inicio; protocolo incompleto.")
                started.discard(identity)
                seen.add(identity)
                case = ET.Element("testcase", classname=identity[0], name=identity[1])
                if code in (-1, -2):
                    ET.SubElement(case, "failure").text = fields.get("stack", "Fallo del runner")
                elif code in (-3, -4):
                    ET.SubElement(case, "skipped").text = fields.get("stack", "Omitido")
                cases.append(case)
            fields = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            final_code = int(line.split(":", 1)[1])

    if not cases or started or totals != {len(cases)} or final_code != -1:
        raise ValueError("Ejecución vacía, interrumpida o con recuento incompleto.")
    failures = sum(case.find("failure") is not None for case in cases)
    skipped = sum(case.find("skipped") is not None for case in cases)
    if not failures:
        summary = re.search(r"(?m)^OK \((\d+) tests?\)\s*$", text)
        if summary is None or (not skipped and int(summary.group(1)) != len(cases)):
            raise ValueError("Falta la confirmación final de JUnit.")
    root = ET.Element("testsuite", name="AndroidJUnitRunner", tests=str(len(cases)),
                      failures=str(failures), errors="0", skipped=str(skipped))
    root.extend(cases)
    return root


def require_protected_group():
    membership = next(line[3:] for line in Path("/proc/self/cgroup").read_text().splitlines()
                      if line.startswith("0::"))
    if not membership.endswith("/lyape-local.slice/lyape-local-android-tests.scope"):
        raise ValueError("Usa bash scripts/local-safe.sh android-tests: no se ejecuta fuera del límite.")
    parent = Path("/sys/fs/cgroup" + membership).parent
    if (parent / "memory.max").read_text().strip() != "4294967296":
        raise ValueError("El máximo compartido no es 4 GiB; ejecución cancelada.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--class", dest="selection")
    args = parser.parse_args(argv)
    try:
        require_protected_group()
        if not re.fullmatch(r"emulator-\d+", args.serial):
            raise ValueError("Solo se permite un serial de emulador explícito.")
        if args.selection and not re.fullmatch(r"com\.lyape\.[\w.$]+(?:#[\w$]+)?", args.selection):
            raise ValueError("Filtro de clase/método no válido.")

        directory = Path("app/build/outputs/androidTest-results") / (
            "direct-selected" if args.selection else "direct")
        directory.mkdir(parents=True, exist_ok=True)
        report = directory / "TEST-android.xml"
        # Nunca dejar un XML de éxito anterior como si perteneciera a un intento fallido.
        report.unlink(missing_ok=True)

        adb = ["adb", "-s", args.serial]

        def call(arguments, timeout=60):
            result = subprocess.run(adb + arguments, capture_output=True, text=True, timeout=timeout)
            if result.returncode:
                raise ValueError(result.stderr or result.stdout or "Falló adb")
            return result.stdout

        if call(["shell", "getprop", "ro.hardware"]).strip() not in ("ranchu", "goldfish"):
            raise ValueError("El destino no es un AVD: no se instalará ningún APK.")
        if call(["shell", "getprop", "sys.boot_completed"]).strip() != "1":
            raise ValueError("El AVD no ha terminado de arrancar.")
        for apk in ("app/build/outputs/apk/debug/app-debug.apk",
                    "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
            if not Path(apk).is_file():
                raise ValueError("Compila primero los APK con el lanzador protegido, sin emulador.")
            call(["install", "--user", "0", "-r", "-t", apk], timeout=90)

        command = ["shell", "am", "instrument", "-w", "-r"]
        if args.selection:
            command += ["-e", "class", args.selection]
        command += ["com.lyape.test/com.lyape.testing.EmulatorTestRunner"]
        print(f"Ejecutando AndroidJUnitRunner en {args.serial}; sin Gradle en el host.", flush=True)
        try:
            output = call(command, timeout=600)
        except subprocess.TimeoutExpired:
            call(["shell", "am", "force-stop", "com.lyape"], timeout=15)
            raise ValueError("Tiempo límite: instrumentación detenida; no se genera un pase.")
        (directory / "instrumentation.txt").write_text(output, encoding="utf-8")
        root = parse_results(output)
        ET.ElementTree(root).write(report, encoding="utf-8", xml_declaration=True)
        required = REQUIRED_SCENARIOS["android"]
        if args.selection:
            class_name, _, method = args.selection.partition("#")
            required = {class_name: {method}} if method else {
                class_name: required.get(class_name, set())}
        for case in root:
            label = "FAIL" if case.find("failure") is not None else (
                "SKIP" if case.find("skipped") is not None else "OK")
            print(f"{label} {case.get('classname')}.{case.get('name')}")
        summary = verify_reports([report], required)
        print(f"{summary.tests} pruebas; {summary.scenarios} escenarios obligatorios; "
              f"{summary.skipped} omitidas. Reporte: {report}")
        return 0
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
