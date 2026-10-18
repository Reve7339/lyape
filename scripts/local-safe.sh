#!/usr/bin/env bash
# Los límites del grupo incluyen memoria nativa y todos sus procesos hijos.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd -- "$script_dir/.." && pwd)
slice=lyape-local.slice
high_bytes=3221225472
max_bytes=4294967296
swap_bytes=1073741824

fail() { printf 'LYape: %s\n' "$*" >&2; exit 1; }

usage() {
    printf '%s\n' \
        'Uso: bash scripts/local-safe.sh <comando>' \
        '  check                  Comprueba límites reales sin compilar ni abrir un AVD.' \
        '  status                 Muestra el consumo y los límites del grupo.' \
        '  gradle <tareas...>      Gradle con un trabajador y sin daemons reutilizables.' \
        '  android-tests [args]   Mismos tests Android sin Gradle junto al AVD; genera JUnit XML.' \
        '  emulator <nombre-AVD>  Emulador sin ventana, con 1536 MB de RAM y 2 núcleos.' \
        '  stop                   Detiene solo las tareas iniciadas por este script.' \
        '' \
        'Grupo compartido: 3 GiB de umbral, 4 GiB de máximo de memoria y 1 GiB de swap.' \
        'Requiere Linux, cgroups v2 y systemd de usuario. Si no hay protección, no ejecuta la tarea.' \
        'La configuración del entorno (JDK/SDK) debe estar cargada antes de usar gradle/emulator.'
}

require_tools() {
    command -v systemctl >/dev/null || fail 'Falta systemctl; no se ejecutará sin límites.'
    command -v systemd-run >/dev/null || fail 'Falta systemd-run; no se ejecutará sin límites.'
    [[ -f /sys/fs/cgroup/cgroup.controllers ]] || fail 'Se necesita cgroups v2.'
}

inside_group() {
    local mode=${1:?}
    shift
    local membership control_group group_dir
    membership=$(awk -F: '$1 == "0" { print $3 }' /proc/self/cgroup)
    case "$membership" in
        */lyape-local.slice/lyape-local-*.scope) ;;
        *) fail 'La tarea no está dentro del grupo protegido; ejecución cancelada.' ;;
    esac

    # El límite vive en el PADRE, no solo en cada scope: Gradle + emulador comparten 4 GiB.
    systemctl --user set-property --runtime "$slice" \
        MemoryAccounting=yes MemoryHigh=3G MemoryMax=4G MemorySwapMax=1G \
        CPUQuota=200% TasksMax=512
    control_group=$(systemctl --user show "$slice" --property=ControlGroup --value)
    [[ "$control_group" == /*/lyape-local.slice ]] || fail 'No se pudo identificar el grupo protegido.'
    group_dir="/sys/fs/cgroup$control_group"
    [[ "$(<"$group_dir/memory.high")" == "$high_bytes" ]] || fail 'No se aplicó MemoryHigh.'
    [[ "$(<"$group_dir/memory.max")" == "$max_bytes" ]] || fail 'No se aplicó MemoryMax.'
    [[ "$(<"$group_dir/memory.swap.max")" == "$swap_bytes" ]] || fail 'No se aplicó MemorySwapMax.'
    printf 'LYape: límites del kernel verificados: 3 GiB / 4 GiB, swap máximo 1 GiB.\n'
    cd -- "$project_dir"
    case "$mode" in
        check)
            printf 'Grupo: %s\n' "$control_group"
            printf 'Memoria actual: %s bytes\n' "$(<"$group_dir/memory.current")"
            ;;
        gradle)
            exec ./gradlew "$@" --no-daemon --no-parallel --max-workers=1 \
                '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
                -Pkotlin.compiler.execution.strategy=in-process
            ;;
        android-tests)
            exec python3 -m scripts.run_android_tests "$@"
            ;;
        emulator)
            local emulator_bin
            if [[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
                emulator_bin="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/emulator/emulator"
            else
                emulator_bin=$(command -v emulator) || fail 'Configura ANDROID_HOME para encontrar el emulador.'
            fi
            [[ -x "$emulator_bin" ]] || fail 'No se encontró el ejecutable del emulador.'
            # -lowram evita que el emulador eleve la petición a 2560 MiB por el API del AVD.
            # El máximo del cgroup sigue incluyendo toda la memoria nativa del proceso.
            exec "$emulator_bin" -avd "$1" -lowram -memory 1536 -cores 2 \
                -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -feature -Vulkan
            ;;
        *) fail 'Comando interno no permitido.' ;;
    esac
}

mode=${1:---help}
shift || true
case "$mode" in
    --help|-h|help) usage; exit 0 ;;
    check) [[ $# -eq 0 ]] || { usage >&2; exit 2; } ;;
    gradle) [[ $# -gt 0 ]] || { usage >&2; exit 2; } ;;
    android-tests) ;;
    emulator) [[ $# -eq 1 && "$1" =~ ^[[:alnum:]_.-]+$ ]] || { usage >&2; exit 2; } ;;
    status|stop) [[ $# -eq 0 ]] || { usage >&2; exit 2; } ;;
    --inside)
        [[ $# -ge 1 ]] || fail 'Falta el comando interno.'
        inside_group "$@"
        exit 0
        ;;
    *) usage >&2; exit 2 ;;
esac
require_tools

case "$mode" in
    status)
        exec systemctl --user show "$slice" \
            --property=ActiveState --property=MemoryCurrent --property=MemoryPeak \
            --property=MemoryHigh --property=MemoryMax --property=MemorySwapCurrent \
            --property=MemorySwapMax --property=TasksMax
        ;;
    stop)
        # Nombres exactos y exclusivos: no detiene Java, ADB u otros proyectos globalmente.
        for scope in lyape-local-gradle.scope lyape-local-emulator.scope lyape-local-check.scope lyape-local-android-tests.scope; do
            if systemctl --user is-active --quiet "$scope"; then
                systemctl --user stop "$scope"
            fi
        done
        printf 'LYape: tareas protegidas detenidas; no se borró ningún dato.\n'
        exit 0
        ;;
esac

# No hay fallback a ejecución directa si falla systemd, D-Bus o el controlador de memoria.
exec systemd-run --user --scope --collect --quiet \
    --unit="lyape-local-$mode.scope" --slice="$slice" \
    --property=MemoryAccounting=yes --property=MemoryHigh=3G --property=MemoryMax=4G \
    --property=MemorySwapMax=1G --property=CPUQuota=200% --property=TasksMax=512 \
    -- "$BASH" "$script_dir/local-safe.sh" --inside "$mode" "$@"
