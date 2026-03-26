#!/usr/bin/env bash
# ------------------------------------------------------------
# Bash version of the original Windows .bat file
# ------------------------------------------------------------
# Fail fast on errors, treat unset variables as errors and
# propagate failures through pipelines.
set -euo pipefail

# ------------------------------------------------------------
#  Configurable command names (change them here if you need)
# ------------------------------------------------------------
TOOL_COMMAND="pdpot-tools"
SCRIPT_COMMAND="pdpot-script"
TEX_COMMAND="pdflatex"          # <- new variable

# ------------------------------------------------------------
#  Directory layout (same logical names as the .bat file)
# ------------------------------------------------------------
SOLUTION_DIR="data/solutions"
SHEET_DIR="output/sheets"
TEX_DIR="output/tex"

# ------------------------------------------------------------
#  Helper: print an error and exit
# ------------------------------------------------------------
die() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

# ------------------------------------------------------------
#  Verify that the required external commands exist
# ------------------------------------------------------------
missing=0

for cmd in "$TOOL_COMMAND" "$SCRIPT_COMMAND" "$TEX_COMMAND"; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        printf 'Required program not found: %s\n' "$cmd" >&2
        missing=$((missing + 1))
    fi
done

if (( missing > 0 )); then
    cat >&2 <<'EOF'

One or more required programs are missing. Please install them,
or run the workflow inside Docker using the provided helper script:

    ./replicate-docker.sh

EOF
    exit 1
fi

# ------------------------------------------------------------
#  Create output directories (‑p = no error if they already exist)
# ------------------------------------------------------------
mkdir -p "$SHEET_DIR"
mkdir -p "$TEX_DIR"

# ------------------------------------------------------------
#  Run the spreadsheet generator (four calls)
# ------------------------------------------------------------
"$TOOL_COMMAND" spreadsheet \
    -i "$SOLUTION_DIR/large-regular-colgen.zip" \
    -i "$SOLUTION_DIR/large-regular-mip.zip" \
    -o "$SHEET_DIR/output-large.xlsx"

"$TOOL_COMMAND" spreadsheet \
    -i "$SOLUTION_DIR/small-regular-colgen.zip" \
    -i "$SOLUTION_DIR/small-regular-mip.zip" \
    -o "$SHEET_DIR/output-small.xlsx"

"$TOOL_COMMAND" spreadsheet \
    -i "$SOLUTION_DIR/small-regular-mip.zip" \
    -i "$SOLUTION_DIR/small-forbid-mip.zip" \
    -o "$SHEET_DIR/output-forbid-small.xlsx"

"$TOOL_COMMAND" spreadsheet \
    -i "$SOLUTION_DIR/large-regular-mip.zip" \
    -i "$SOLUTION_DIR/large-forbid-mip.zip" \
    -o "$SHEET_DIR/output-forbid-large.xlsx"

# ------------------------------------------------------------
#  Run the various pdpot‑script sub‑commands
# ------------------------------------------------------------
"$SCRIPT_COMMAND" tables \
    -d "$SHEET_DIR/output-small.xlsx:Small" \
    -d "$SHEET_DIR/output-large.xlsx:Large" \
    --output-dir "$TEX_DIR"

"$SCRIPT_COMMAND" comparison \
    -d "$SHEET_DIR/output-forbid-small.xlsx:Small" \
    -d "$SHEET_DIR/output-forbid-large.xlsx:Large" \
    --output-dir "$TEX_DIR"

"$SCRIPT_COMMAND" delta-table \
    -d "$SHEET_DIR/output-small.xlsx:Small" \
    -d "$SHEET_DIR/output-large.xlsx:Large" \
    --output-dir "$TEX_DIR"

"$SCRIPT_COMMAND" plots \
    -d "$SHEET_DIR/output-small.xlsx:Small" \
    --output-dir "$TEX_DIR"

"$SCRIPT_COMMAND" plots \
    -d "$SHEET_DIR/output-large.xlsx:Large" \
    --output-dir "$TEX_DIR"

"$SCRIPT_COMMAND" latex \
    --output-dir "$TEX_DIR"

# ------------------------------------------------------------
#  Build the LaTeX document (run twice, as the .bat did)
# ------------------------------------------------------------
pushd "$TEX_DIR" > /dev/null
"$TEX_COMMAND" main.tex
"$TEX_COMMAND" main.tex
popd > /dev/null
