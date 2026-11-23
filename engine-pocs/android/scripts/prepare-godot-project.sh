#!/usr/bin/env bash

set -euo pipefail

readonly GODOT_VERSION="4.7.2-stable"
readonly GODOT_ARCHIVE_NAME="Godot_v4.7.2-stable_linux.x86_64.zip"
readonly GODOT_ARCHIVE_SHA256="cadd3204e728a35d3f13adb7fd0d7902636b79f6b95c40c265eb73b6c35329e4"
readonly GODOT_DOWNLOAD_URL="https://github.com/godotengine/godot/releases/download/4.7.2-stable/${GODOT_ARCHIVE_NAME}"
readonly PROJECT_RELATIVE_PATH="engine-pocs/android/app/src/godot/assets"

workspace_root="$(pwd)"
temporary_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/wolf-godot-${GODOT_VERSION}"
archive_path="${temporary_root}/${GODOT_ARCHIVE_NAME}"
editor_directory="${temporary_root}/editor"
editor_path="${editor_directory}/Godot_v4.7.2-stable_linux.x86_64"
project_path="${workspace_root}/${PROJECT_RELATIVE_PATH}"
runtime_log="${temporary_root}/headless-runtime.log"

if [[ "$(uname -m)" != "x86_64" ]]; then
    printf 'Godot %s import requires a Linux x86_64 runner; found %s.\n' "${GODOT_VERSION}" "$(uname -m)" >&2
    exit 1
fi

for required_command in curl sha256sum unzip; do
    if ! command -v "${required_command}" >/dev/null 2>&1; then
        printf 'Required command is missing: %s\n' "${required_command}" >&2
        exit 1
    fi
done

if [[ ! -s "${project_path}/project.godot" ||
      ! -s "${project_path}/main.tscn" ||
      ! -s "${project_path}/main.gd" ]]; then
    printf 'The embedded Godot project is incomplete under %s.\n' "${project_path}" >&2
    exit 1
fi

mkdir -p "${temporary_root}" "${editor_directory}"

if [[ ! -s "${archive_path}" ]]; then
    curl --fail --location --retry 3 --retry-delay 2 --retry-all-errors \
        --output "${archive_path}" "${GODOT_DOWNLOAD_URL}"
fi

printf '%s  %s\n' "${GODOT_ARCHIVE_SHA256}" "${archive_path}" | sha256sum --check

if [[ ! -x "${editor_path}" ]]; then
    unzip -oq "${archive_path}" -d "${editor_directory}"
    chmod +x "${editor_path}"
fi

"${editor_path}" --version

printf '\nImporting the embedded Godot project using the pinned editor...\n'
"${editor_path}" --headless --editor --path "${project_path}" --import --quit

readonly SCRIPT_CLASS_CACHE="${project_path}/.godot/global_script_class_cache.cfg"
if [[ ! -f "${SCRIPT_CLASS_CACHE}" ]]; then
    printf 'Godot editor import did not create the required global script class cache: %s\n' "${SCRIPT_CLASS_CACHE}" >&2
    find "${project_path}/.godot" -maxdepth 3 -type f -print 2>/dev/null || true
    exit 1
fi

printf '\nStarting the imported project in headless mode to validate its main scene and scripts...\n'
if ! "${editor_path}" --headless --path "${project_path}" --quit-after 2 >"${runtime_log}" 2>&1; then
    cat "${runtime_log}" >&2
    exit 1
fi
cat "${runtime_log}"

if grep -E 'SCRIPT ERROR:|Parse Error:|Failed loading script' "${runtime_log}"; then
    printf 'The imported Godot project reported a script or scene load error.\n' >&2
    exit 1
fi

printf '\nGodot %s project import and headless scene validation passed.\n' "${GODOT_VERSION}"
