#!/usr/bin/env bash
#====================================================================
# pdpot-installer.sh – Install pdpot‑tools and pdpot‑script
#
#   Usage:
#       ./pdpot-installer.sh [--mode MODE] [--install-dir DIR] [--help]
#
#   Options
#       --mode MODE        one of: local | docker | podman   (default: local)
#       --install-dir DIR  directory where the wrappers and jars will be
#                          placed (default: $HOME/.local)
#       --help             display this help and exit
#
#   What it does
#       * local   – builds the Java jar if it is missing, copies it,
#                  creates a tiny wrapper that runs `java -jar …`,
#                  and installs the Python tool via `uv tool install .`.
#       * docker  – builds a Docker image called pdpot‑container and
#                  installs two wrapper scripts that run `docker run …`.
#       * podman  – same as docker but using podman.
#
#   License: MIT (feel free to adapt)
#====================================================================

set -euo pipefail                # abort on errors, undefined vars, pipefails
IFS=$'\n\t'                     # sane field splitting

# -----------------------------------------------------------------
# Default values (can be overridden by CLI options)
# -----------------------------------------------------------------
MODE="local"
INSTALL_DIR="${HOME}/.local"
# NOTE: adjust the name if your build produces a different jar
JAR_SRC="java/pdpot-tools/target/pdpot-tools-0.0.1-SNAPSHOT-fatjar.jar"
JAR_NAME="$(basename "${JAR_SRC}")"
PYTHON_PROJECT_DIR="script"
WRAPPER_DIR="${INSTALL_DIR}/bin"

# -----------------------------------------------------------------
# Helper functions
# -----------------------------------------------------------------
die() {
    printf "ERROR: %s\n" "$*" >&2
    exit 1
}
info() {
    printf "INFO: %s\n" "$*"
}
usage() {
    sed -n '2,100p' "$0" | sed -e 's/^# //'
    exit 0
}

# -----------------------------------------------------------------
# Parse command‑line arguments
# -----------------------------------------------------------------
while (( $# )); do
    case "$1" in
        --mode)
            MODE="${2:-}"
            [[ -z "${MODE}" ]] && die "--mode requires an argument"
            shift 2
            ;;
        --install-dir)
            INSTALL_DIR="${2:-}"
            [[ -z "${INSTALL_DIR}" ]] && die "--install-dir requires an argument"
            shift 2
            ;;
        --help|-h)
            usage
            ;;
        *)
            die "Unknown option: $1 (use --help for usage)"
            ;;
    esac
done

# Normalise the mode string
MODE="$(tr '[:upper:]' '[:lower:]' <<<"${MODE}")"
[[ "${MODE}" != "local" && "${MODE}" != "docker" && "${MODE}" != "podman" ]] \
    && die "Invalid mode '${MODE}'. Choose from: local, docker, podman."

# Ensure the wrapper directory exists
mkdir -p "${WRAPPER_DIR}"

# -----------------------------------------------------------------
# 1️⃣  Local installation
# -----------------------------------------------------------------
install_local() {
    # -----------------------------------------------------------------
    # 1.1 Java tool – ensure we have a JAR (build it if missing)
    # -----------------------------------------------------------------
    info "▶ Installing pdpot‑tools (local mode)"

    # Check that a Java runtime (>=21) is present – needed for the wrapper later
    if ! command -v java >/dev/null; then
        die "Java runtime not found – please install Java 21 or newer."
    fi
    JAVA_VER=$(java -version 2>&1 | awk -F[\".] '/version/ {print $2}')
    (( JAVA_VER < 21 )) && die "Java version ${JAVA_VER} detected – Java 21+ required."

    # -------------------------------------------------------------
    # Does the JAR already exist?
    # -------------------------------------------------------------
    if [[ -f "${JAR_SRC}" ]]; then
        info "Found existing JAR at ${JAR_SRC}"
    else
        info "JAR not found – attempting to build it now."

        # Verify Maven (or the Maven wrapper) is available
        if [[ -x "java/mvnw" ]]; then
            MAVEN_CMD="./mvnw"
        elif command -v mvn >/dev/null; then
            MAVEN_CMD="mvn"
        else
            die "Neither Maven nor a Maven wrapper (mvnw) was found. Install Maven or add the wrapper."
        fi

        # Run Maven in the java/ directory (quiet, non‑interactive)
        pushd "java" >/dev/null
        info "Running '${MAVEN_CMD} -B package' to build the JAR..."
        "${MAVEN_CMD}" -B package
        popd >/dev/null

        # After the build, verify the JAR exists
        if [[ -f "${JAR_SRC}" ]]; then
            info "Build succeeded – JAR created at ${JAR_SRC}"
        else
            die "Maven finished but ${JAR_SRC} still does not exist."
        fi
    fi

    # -------------------------------------------------------------
    # 1.2 Copy JAR to the user‑local location
    # -------------------------------------------------------------
    JAR_DEST_DIR="${INSTALL_DIR}/pdpot-tools"
    mkdir -p "${JAR_DEST_DIR}"
    cp -a "${JAR_SRC}" "${JAR_DEST_DIR}/${JAR_NAME}"
    info "Copied ${JAR_SRC} → ${JAR_DEST_DIR}/${JAR_NAME}"

    # -------------------------------------------------------------
    # 1.3 Create the wrapper script for pdpot-tools
    # -------------------------------------------------------------
    TOOLS_WRAPPER="${WRAPPER_DIR}/pdpot-tools"
    cat >"${TOOLS_WRAPPER}" <<EOF
#!/usr/bin/env bash
# Wrapper for pdpot‑tools (installed by pdpot‑installer.sh)
# Executes the shipped jar with the user‑provided arguments.
exec java -jar "${JAR_DEST_DIR}/${JAR_NAME}" "\$@"
EOF
    chmod +x "${TOOLS_WRAPPER}"
    info "Installed wrapper → ${TOOLS_WRAPPER}"

    # -----------------------------------------------------------------
    # 1.4 Python script tool
    # -----------------------------------------------------------------
    info "▶ Installing pdpot‑script (local mode)"

    # Check Python ≥ 3.11
    if ! command -v python3 >/dev/null; then
        die "python3 not found – please install Python 3.11+."
    fi
    PY_VER_MAJOR=$(python3 -c 'import sys; print(sys.version_info[0])')
    PY_VER_MINOR=$(python3 -c 'import sys; print(sys.version_info[1])')
    (( PY_VER_MAJOR < 3 || (PY_VER_MAJOR == 3 && PY_VER_MINOR < 11) )) \
        && die "Python version ${PY_VER_MAJOR}.${PY_VER_MINOR} detected – Python 3.11+ required."

    # Check uv (the Python package manager)
    if ! command -v uv >/dev/null; then
        die "uv not found – install it first (https://github.com/astral-sh/uv)."
    fi

    # Run uv tool install – it puts the entry‑point script into ~/.local/bin
    pushd "${PYTHON_PROJECT_DIR}" >/dev/null
    uv tool install . --quiet
    popd >/dev/null
    info "uv tool installed – you should now have a \`pdpot-script\` command in ${HOME}/.local/bin"
}

# -----------------------------------------------------------------
# 2️⃣  Container (Docker/Podman) installation
# -----------------------------------------------------------------
install_container() {
    local engine="$1"          # "docker" or "podman"
    local img_name="pdpot-container"

    info "▶ Building ${engine} image '${img_name}'"
    if ! command -v "${engine}" >/dev/null; then
        die "${engine} not found – please install it or choose another mode."
    fi

    # Build the image from the Dockerfile that lives in the repo root.
    "${engine}" build -t "${img_name}" . || die "Failed to build the ${engine} image."

    # Create two tiny wrapper scripts that invoke the container
    for cmd in pdpot-tools pdpot-script; do
        local wrapper_path="${WRAPPER_DIR}/${cmd}"
        cat >"${wrapper_path}" <<EOF
#!/usr/bin/env bash
# ${engine} wrapper for ${cmd} – installed by pdpot‑installer.sh
# It mounts the current working directory into /data inside the container.
exec ${engine} run -v "\$PWD":/data --rm "${img_name}" ${cmd} "\$@"
EOF
        chmod +x "${wrapper_path}"
        info "Installed ${engine} wrapper → ${wrapper_path}"
    done
}

# -----------------------------------------------------------------
# 3️⃣  Main entry point
# -----------------------------------------------------------------
main() {
    info "=== pdpot installer ==="
    info "Mode          : ${MODE}"
    info "Install dir   : ${INSTALL_DIR}"
    info "Wrapper dir   : ${WRAPPER_DIR}"
    info "-------------------------------------"

    case "${MODE}" in
        local)
            install_local
            ;;
        docker)
            install_container docker
            ;;
        podman)
            install_container podman
            ;;
        *)
            die "Unsupported mode '${MODE}' (this should never happen)."
            ;;
    esac

    info "Installation completed successfully."
    info "Make sure ${WRAPPER_DIR} is on your PATH (e.g. add \"export PATH=\\\"\$PATH:${WRAPPER_DIR}\\\"\" to ~/.bashrc)."
}

main "$@"

