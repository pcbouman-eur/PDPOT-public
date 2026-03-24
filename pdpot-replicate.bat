@echo off
SET "JAR_COMMAND=java -jar java\pdpot-tools\target\pdpot-tools-0.0.1-SNAPSHOT-fatjar.jar"
SET "SCRIPT_COMMAND=uv run pdpot-script"
SET "SOLUTION_DIR=data\solutions"
SET "SHEET_DIR=output\sheets"
SET "TEX_DIR=output\tex"


rem ---- 1. Initialize flag (inside setlocal for safety in complex scripts)
setlocal EnableDelayedExpansion

where /Q java >nul 2>&1
if errorlevel 1 (
    echo "============================================================"
    echo " ERROR: Command "java" is missing!"
    echo " Please install Docker and run the Docker-based wrapper:"
    echo "     docker-replicate.bat"
    echo " (Docker will provide java, uv, and pdflatex in an isolated"
    echo "  container, so you don’t have to install them locally.)"
    echo "============================================================"
    exit /b 1
)

where /Q uv >nul 2>&1
if errorlevel 1 (
    echo "============================================================"
    echo " ERROR: Command "uv" is missing!"
    echo " Please install Docker and run the Docker-based wrapper:"
    echo "     docker-replicate.bat"
    echo " (Docker will provide java, uv, and pdflatex in an isolated"
    echo "  container, so you don’t have to install them locally.)"
    echo "============================================================"
    exit /b 1
)

where /Q java >nul 2>&1
if errorlevel 1 (
    echo "============================================================"
    echo " ERROR: Command "pdflatex" is missing!"
    echo " Please install Docker and run the Docker-based wrapper:"
    echo "     docker-replicate.bat"
    echo " (Docker will provide java, uv, and pdflatex in an isolated"
    echo "  container, so you don’t have to install them locally.)"
    echo "============================================================"
    exit /b 1
)
endlocal

echo "=========================================================="
echo "   All required tools found."
echo "   Phase 1 starts: creating spreadsheet files"
echo "=========================================================="

mkdir %SHEET_DIR% >nul 2>&1
%JAR_COMMAND% spreadsheet ^
   -i %SOLUTION_DIR%\large-regular-colgen.zip ^
   -i %SOLUTION_DIR%\large-regular-mip.zip ^
   -o %SHEET_DIR%\output-large.xlsx
%JAR_COMMAND% spreadsheet ^
   -i %SOLUTION_DIR%\small-regular-colgen.zip ^
   -i %SOLUTION_DIR%\small-regular-mip.zip ^
   -o %SHEET_DIR%\output-small.xlsx
%JAR_COMMAND% spreadsheet ^
   -i %SOLUTION_DIR%\small-regular-mip.zip ^
   -i %SOLUTION_DIR%\small-forbid-mip.zip ^
   -o %SHEET_DIR%\output-forbid-small.xlsx
%JAR_COMMAND% spreadsheet ^
   -i %SOLUTION_DIR%\large-regular-mip.zip ^
   -i %SOLUTION_DIR%\large-forbid-mip.zip ^
   -o %SHEET_DIR%\output-forbid-large.xlsx

echo "=========================================================="
echo "   Phase 1 is done."
echo "   Phase 2 starts: analysing spreadsheet files."
echo "=========================================================="


mkdir %TEX_DIR% >nul 2>&1
pushd script
%SCRIPT_COMMAND% tables ^
   -d ..\%SHEET_DIR%\output-small.xlsx:Small ^
   -d ..\%SHEET_DIR%\output-large.xlsx:Large ^
   --output-dir ..\%TEX_DIR%
%SCRIPT_COMMAND% comparison ^
   -d ..\%SHEET_DIR%\output-forbid-small.xlsx:Small ^
   -d ..\%SHEET_DIR%\output-forbid-large.xlsx:Large ^
   --output-dir ..\%TEX_DIR%                     
%SCRIPT_COMMAND% delta-table ^
   -d ..\%SHEET_DIR%\output-small.xlsx:Small ^
   -d ..\%SHEET_DIR%\output-large.xlsx:Large ^
   --output-dir ..\%TEX_DIR%
%SCRIPT_COMMAND% plots ^
   -d ..\%SHEET_DIR%\output-small.xlsx:Small ^
   --output-dir ..\%TEX_DIR%
%SCRIPT_COMMAND% plots ^
   -d ..\%SHEET_DIR%\output-large.xlsx:Large ^
   --output-dir ..\%TEX_DIR%
%SCRIPT_COMMAND% latex ^
   --output-dir ..\%TEX_DIR%
popd
echo "=========================================================="
echo "   Phase 2 is done."
echo "   Running LaTeX to compile final document."
echo "=========================================================="

pushd %TEX_DIR%
pdflatex main.tex
pdflatex main.tex
popd
