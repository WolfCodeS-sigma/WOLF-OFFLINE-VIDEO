#!/usr/bin/env bash

set -uo pipefail

ENGINE_FAILURES=0
ENGINE_RESULTS=()

collect_engine_diagnostics() {
    local engine_name="$1"

    printf '\n===== %s: attached Android devices =====\n' "$engine_name"
    adb devices -l 2>&1 || true

    printf '\n===== %s: resumed Android activities =====\n' "$engine_name"
    adb shell dumpsys activity activities 2>&1         | grep -E 'mResumedActivity|topResumedActivity|Resumed:'         | tail -n 20 || true

    printf '\n===== %s: native and graphics logcat diagnostics =====\n' "$engine_name"
    adb logcat -d -b all -v threadtime 2>&1         | grep -E 'Godot|godot|Filament|filament|libgdx|Lottie|AndroidRuntime|libgodot|libgdx\.so|EGL|GLES|OpenGL|Vulkan|FATAL|Fatal signal|SIGSEGV|SIGABRT|ERROR|Exception|ActivityManager|SurfaceView|Instrumentation'         | tail -n 300 || true

    printf '\n===== %s: instrumentation XML result files =====\n' "$engine_name"
    find engine-pocs/android/app/build/outputs/androidTest-results/connected         -type f         -name '*.xml'         -print 2>/dev/null         | sort || true
}

run_engine_regression() {
    local engine_name="$1"
    local gradle_task="$2"
    local task_status=0

    printf '\n============================================================\n'
    printf 'WOLF isolated Android engine regression: %s\n' "$engine_name"
    printf 'Gradle task: %s\n' "$gradle_task"
    printf '============================================================\n'

    if gradle --no-daemon --max-workers=2 -p engine-pocs/android "$gradle_task"; then
        printf '\nRESULT: %s PASSED\n' "$engine_name"
        ENGINE_RESULTS+=("$engine_name: PASSED")
    else
        task_status=$?
        ENGINE_FAILURES=$((ENGINE_FAILURES + 1))
        ENGINE_RESULTS+=("$engine_name: FAILED (exit code $task_status)")

        printf '\nRESULT: %s FAILED (exit code %s)\n' "$engine_name" "$task_status"
        collect_engine_diagnostics "$engine_name"
        printf '\n===== End of %s diagnostics =====\n' "$engine_name"
    fi
}

run_engine_regression "Godot" ":app:connectedGodotDebugAndroidTest"
run_engine_regression "Filament" ":app:connectedFilamentDebugAndroidTest"
run_engine_regression "libGDX" ":app:connectedLibgdxDebugAndroidTest"
run_engine_regression "Lottie" ":app:connectedLottieDebugAndroidTest"

printf '\n============================================================\n'
printf 'WOLF isolated Android engine regression summary\n'
printf '============================================================\n'

for result in "${ENGINE_RESULTS[@]}"; do
    printf '%s\n' "$result"
done

if [ "$ENGINE_FAILURES" -gt 0 ]; then
    printf '\n%d isolated engine regression group(s) failed.\n' "$ENGINE_FAILURES"
    exit 1
fi

printf '\nAll four isolated Android engine regression groups passed.\n'
