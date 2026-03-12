#!/usr/bin/env bash
#====================================================================
# pdpot-installer.sh – Install / uninstall pdpot‑tools & pdpot‑script
#
#   Usage:
#       ./pdpot-installer.sh [--mode MODE] [--install-dir DIR] [--uninstall] [--help]
#
#   Options
#       --mode MODE        one of: local | docker | podman   (default: local)
#       --install-dir DIR  directory where the wrappers and jars will be
#                          placed (default: $HOME/.local)
#       --uninstall        remove everything that was installed by this script.
#                          When used, --mode is ignored.
#       --help             display this help and exit
#
#   What the script does
#       * **Install**
#           - local   – builds the Java jar if missing, copies it,
#                       creates a tiny wrapper that runs `java -jar …`,
#                       and installs the Python tool via `uv tool install .`.
#           - docker  – builds a Docker image called pdpot‑container and
#                       installs two wrapper scripts that run `docker run …`.
#           - podman  – same as docker but using podman.
#
#       * **Uninstall**
#           - removes the wrapper scripts (`${INSTALL_DIR}/bin/pdpot-*`);
#           - removes the copied JAR directory (`${INSTALL_DIR}/pdpot-tools`);
#           - runs `uv tool uninstall pdpot-script` if `uv` is present;
#           - (optional) removes the container image `pdpot-container`.
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
JAR_SRC="java/pdpot-tools/target/pdpot-tools-0.0.1-SNAPSHOT-fatjar.jar"   # adjust if your jar name changes
JAR_NAME="$(basename "${JAR_SRC}")"
PYTHON_PROJECT_DIR="script"
WRAPPER_DIR="${INSTALL_DIR}/bin"
UNINSTALL=false

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
    cat <<'EOF'
pdpot-installer.sh – Install / uninstall pdpot‑tools & pdpot‑script

Usage:
    ./pdpot-installer.sh [--mode MODE] [--install-dir DIR] [--uninstall] [--help]

Options:
    --mode MODE        one of: local | docker | podman   (default: local)
    --install-dir DIR  directory where the wrappers and jars will be placed
                       (default: $HOME/.local)
    --uninstall        remove everything that was installed by this script.
                       When present, --mode is ignored.
    --help             display this help and exit

Examples:
    # Normal install (default = local)
    ./pdpot-installer.sh

    # Docker‑based install
    ./pdpot-installer.sh --mode docker

    # Uninstall everything
    ./pdpot-installer.sh --uninstall
EOF
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
        --uninstall)
            UNINSTALL=true
            shift
            ;;
        --help|-h)
            usage
            ;;
        *)
            die "Unknown option: $1 (use --help for usage)"
            ;;
    esac
done

# Normalise mode string (only matters if we are *installing*)
MODE="$(tr '[:upper:]' '[:lower:]' <<<"${MODE}")"
if ! $UNINSTALL; then
    [[ "${MODE}" != "local" && "${MODE}" != "docker" && "${MODE}" != "podman" ]] \
        && die "Invalid mode '${MODE}'. Choose from: local, docker, podman."
fi

# Ensure wrapper directory exists (or will be removed on uninstall)
mkdir -p "${WRAPPER_DIR}"

# -----------------------------------------------------------------
# ----------  INSTALL PATH  ----------------------------------------
# -----------------------------------------------------------------
install_local() {
    # -------- Java tool – ensure we have a JAR (build it if missing) --------
    info "▶ Installing pdpot‑tools (local mode)"

    # Java runtime (>=21) – needed for the wrapper later
    if ! command -v java >/dev/null; then
        die "Java runtime not found – please install Java 21 or newer."
    fi
    JAVA_VER=$(java -version 2>&1 | awk -F[\".] '/version/ {print $2}')
    (( JAVA_VER < 21 )) && die "Java version ${JAVA_VER} detected – Java 21+ required."

    # Does the JAR already exist?
    if [[ -f "${JAR_SRC}" ]]; then
        info "Found existing JAR at ${JAR_SRC}"
    else
        info "JAR not found – attempting to build it now."

        # Choose Maven command (wrapper or system Maven)
        if [[ -x "java/mvnw" ]]; then
            MAVEN_CMD="./mvnw"
        elif command -v mvn >/dev/null; then
            MAVEN_CMD="mvn"
        else
            die "Neither Maven nor a Maven wrapper (mvnw) was found. Install Maven or add the wrapper."
        fi

        pushd "java" >/dev/null
        info "Running '${MAVEN_CMD} -B package' to build the JAR..."
        "${MAVEN_CMD}" -B package
        popd >/dev/null

        if [[ -f "${JAR_SRC}" ]]; then
            info "Build succeeded – JAR created at ${JAR_SRC}"
        else
            die "Maven finished but ${JAR_SRC} still does not exist."
        fi
    fi

    # Copy JAR to user‑local location
    JAR_DEST_DIR="${INSTALL_DIR}/pdpot-tools"
    mkdir -p "${JAR_DEST_DIR}"
    cp -a "${JAR_SRC}" "${JAR_DEST_DIR}/${JAR_NAME}"
    info "Copied ${JAR_SRC} → ${JAR_DEST_DIR}/${JAR_NAME}"

    # Wrapper script for pdpot-tools
    TOOLS_WRAPPER="${WRAPPER_DIR}/pdpot-tools"
    cat >"${TOOLS_WRAPPER}" <<EOF
#!/usr/bin/env bash
# Wrapper for pdpot‑tools (installed by pdpot‑installer.sh)
exec java -jar "${JAR_DEST_DIR}/${JAR_NAME}" "\$@"
EOF
    chmod +x "${TOOLS_WRAPPER}"
    info "Installed wrapper → ${TOOLS_WRAPPER}"

    # -------- Python tool --------------------------------------------------
    info "▶ Installing pdpot‑script (local mode)"

    # Python >=3.11
    if ! command -v python3 >/dev/null; then
        die "python3 not found – please install Python 3.11+."
    fi
    PY_VER_MAJOR=$(python3 -c 'import sys; print(sys.version_info[0])')
    PY_VER_MINOR=$(python3 -c 'import sys; print(sys.version_info[1])')
    (( PY_VER_MAJOR < 3 || (PY_VER_MAJOR == 3 && PY_VER_MINOR < 11) )) \
        && die "Python version ${PY_VER_MAJOR}.${PY_VER_MINOR} detected – Python 3.11+ required."

    # uv (Python package manager)
    if ! command -v uv >/dev/null; then
        die "uv not found – install it first (https://github.com/astral-sh/uv)."
    fi

    pushd "${PYTHON_PROJECT_DIR}" >/dev/null
    uv tool install . --quiet
    popd >/dev/null
    info "uv tool installed – you should now have a \`pdpot-script\` command in ${HOME}/.local/bin"
}

install_container() {
    local engine="$1"          # "docker" or "podman"
    local img_name="pdpot-container"

    info "▶ Building ${engine} image '${img_name}'"
    if ! command -v "${engine}" >/dev/null; then
        die "${engine} not found – please install it or choose another mode."
    fi

    "${engine}" build -t "${img_name}" . || die "Failed to build the ${engine} image."

    # Create wrapper scripts that invoke the container
    for cmd in pdpot-tools pdpot-script; do
        local wrapper_path="${WRAPPER_DIR}/${cmd}"
        cat >"${wrapper_path}" <<EOF
#!/usr/bin/env bash
# ${engine} wrapper for ${cmd} – installed by pdpot‑installer.sh
exec ${engine} run -v "\$PWD":/data --rm "${img_name}" ${cmd} "\$@"
EOF
        chmod +x "${wrapper_path}"
        info "Installed ${engine} wrapper → ${wrapper_path}"
    done
}

# -----------------------------------------------------------------
# ----------  UNINSTALL PATH  ---------------------------------------
# -----------------------------------------------------------------
uninstall_all() {
    info "▶ Starting uninstall process"

    # 1) Remove wrapper scripts (if they exist)
    for wrapper in pdpot-tools pdpot-script; do
        if [[ -f "${WRAPPER_DIR}/${wrapper}" ]]; then
            rm -f "${WRAPPER_DIR}/${wrapper}"
            info "Removed wrapper ${WRAPPER_DIR}/${wrapper}"
        fi
    done

    # 2) Remove the copied JAR directory (local mode only)
    if [[ -d "${INSTALL_DIR}/pdpot-tools" ]]; then
        rm -rf "${INSTALL_DIR}/pdpot-tools"
        info "Removed directory ${INSTALL_DIR}/pdpot-tools"
    fi

    # 3) Remove the uv‑installed Python entry point
    if command -v uv >/dev/null; then
        if uv tool list | grep -q '^pdpot-script$'; then
            uv tool uninstall pdpot-script --quiet
            info "uv tool 'pdpot-script' uninstalled"
        else
            info "uv tool 'pdpot-script' not found – nothing to uninstall"
        fi
    else
        info "uv not installed – cannot remove uv‑tool, you may need to delete manually."
    fi

    # 4) Optionally remove container images (docker / podman)
    if command -v docker >/dev/null && docker image inspect pdpot-container >/dev/null 2>&1; then
        docker rmi -f pdpot-container >/dev/null 2>&1 && \
            info "Removed Docker image pdpot-container"
    fi
    if command -v podman >/dev/null && podman image inspect pdpot-container >/dev/null 2>&1; then
        podman rmi -f pdpot-container >/dev/null 2>&1 && \
            info "Removed Podman image pdpot-container"
    fi

    # 5) Clean up empty wrapper directory (if now empty)
    if [[ -d "${WRAPPER_DIR}" && -z "$(ls -A "${WRAPPER_DIR}")" ]]; then
        rmdir "${WRAPPER_DIR}"
        info "Removed empty wrapper directory ${WRAPPER_DIR}"
    fi

    info "Uninstall completed."
}

# -----------------------------------------------------------------
#  MAIN
# -----------------------------------------------------------------
main() {
    if $UNINSTALL; then
        uninstall_all
        exit 0
    fi

    info "=== pdpot installer ==="
    info "Mode          : ${MODE}"
    info "Install dir   : ${INSTALL_DIR}"
    info "Wrapper dir   : ${WRAPPER_DIR}"
    info "--------------------------------------------"

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
