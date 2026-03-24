#!/usr/bin/env bash
#====================================================================
# run-pdpot.sh   –  works with Docker *or* Podman
#
# 1. Verify that a container engine (docker or podman) is available.
# 2. Verify that an image called "pdpot-container" exists.
#    If it does not, build it from the Dockerfile in the current dir.
# 3. Run the container, mounting the current folder at /data
#    and executing the command "pdpot-replicate".
#====================================================================

# -----------------------------------------------------------------
# Fail fast on unset variables, pipe failures and command errors.
# -----------------------------------------------------------------
set -euo pipefail

# -----------------------------------------------------------------
# Helper to print a formatted error block (mirrors the batch script).
# -----------------------------------------------------------------
error_block() {
    echo
    echo "=============================="
    echo "ERROR: $*"
    echo "=============================="
}

# -----------------------------------------------------------------
# Parse optional command‑line flags (only --engine for now)
# -----------------------------------------------------------------
ENGINE_OVERRIDE=""
while (( $# )); do
    case "$1" in
        --engine)
            ENGINE_OVERRIDE="${2:-}"
            shift 2
            ;;
        *)  # unknown argument – ignore (or you could `error_block` and exit)
            shift
            ;;
    esac
done

# -----------------------------------------------------------------
# Choose which container engine to use.
#   1) If the user forced one via $CONTAINER_ENGINE or --engine, honor it.
#   2) Otherwise, prefer docker if it exists, fall back to podman.
# -----------------------------------------------------------------
if [[ -n "${ENGINE_OVERRIDE:-}" ]]; then
    CONTAINER_ENGINE="${ENGINE_OVERRIDE}"
elif [[ -n "${CONTAINER_ENGINE:-}" ]]; then
    # already exported in the environment – keep it
    :
else
    if command -v docker >/dev/null 2>&1; then
        CONTAINER_ENGINE="docker"
    elif command -v podman >/dev/null 2>&1; then
        CONTAINER_ENGINE="podman"
    else
        error_block "Neither Docker nor Podman could be found in your PATH.
Install one of them (Docker Desktop, Docker Engine, or Podman) and try again."
        exit 1
    fi
fi

# -----------------------------------------------------------------
# Verify that the chosen engine can actually talk to its daemon.
# Both Docker and Podman expose a `version` sub‑command that exits
# with 0 only when the service is reachable.
# -----------------------------------------------------------------
if ! "$CONTAINER_ENGINE" version >/dev/null 2>&1; then
    echo
    error_block "$CONTAINER_ENGINE client cannot talk to its daemon.
Is the daemon running?  Start it and try again."
    exit 1
fi

# -----------------------------------------------------------------
# Image handling (same for Docker and Podman)
# -----------------------------------------------------------------
IMAGE_NAME="pdpot-container"

if ! "$CONTAINER_ENGINE" image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
    echo
    echo "Image \"$IMAGE_NAME\" NOT found – building it now..."
    echo "---------------------------------------------------"
    if ! "$CONTAINER_ENGINE" build -t "$IMAGE_NAME" .; then
        echo
        error_block "$CONTAINER_ENGINE build failed."
        exit 1
    fi
    echo "---------------------------------------------------"
    echo "Build finished."
else
    IMG_ID=$("$CONTAINER_ENGINE" images --quiet --no-trunc "$IMAGE_NAME" | head -n1)
    echo
    echo "Image \"$IMAGE_NAME\" already exists (ID=$IMG_ID)"
fi

# -----------------------------------------------------------------
# Run the container
# -----------------------------------------------------------------
echo
echo "Starting container \"$IMAGE_NAME\" via $CONTAINER_ENGINE …"

# Podman and Docker share the same CLI options for the subset we use.
"$CONTAINER_ENGINE" run --rm \
    -v "$(pwd)":/data \
    "$IMAGE_NAME" pdpot-replicate
RUN_STATUS=$?

# -----------------------------------------------------------------
# Report success / failure
# -----------------------------------------------------------------
if (( RUN_STATUS != 0 )); then
    echo
    error_block "The container exited with a non‑zero status."
    exit $RUN_STATUS
else
    echo
    echo "Container finished successfully."
fi

# End of script
