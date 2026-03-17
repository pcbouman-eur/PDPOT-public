@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem -----------------------------------------------------------------
rem 1️⃣ Parse arguments
rem -----------------------------------------------------------------
set "RUN_PHASE1="
set "RUN_PHASE2="
set "ARGS_PROVIDED="

:parse_args
if "%~1"=="" goto :args_done

set "ARGS_PROVIDED=1"

if /I "%~1"=="--phase1" (
    set "RUN_PHASE1=1"
) else if /I "%~1"=="--phase2" (
    set "RUN_PHASE2=1"
) else if /I "%~1"=="--all" (
    set "RUN_PHASE1=1"
    set "RUN_PHASE2=1"
) else (
    echo.
    echo *** ERROR: Unknown argument "%~1" ***
    goto :show_help
)

shift
goto :parse_args

:args_done

rem No arguments → show help
if not defined ARGS_PROVIDED goto :show_help

goto :args_ok

:show_help
echo.
echo Usage:
echo   pdpot-run.bat [--phase1] [--phase2] [--all]
echo.
echo Options:
echo   --phase1   Run Java XLSX generation
echo   --phase2   Run Python/uv LaTeX generation
echo   --all      Run both phases
echo.
exit /b 1

:args_ok

rem -----------------------------------------------------------------
rem 2️⃣ Paths
rem -----------------------------------------------------------------
set "SOLUTION_DIR=data\solutions"
set "SHEET_DIR=output\sheets"
set "TEX_DIR=output\tex"

rem -----------------------------------------------------------------
rem 3️⃣ Detect tools
rem -----------------------------------------------------------------
where java   >nul 2>&1 && set "JAVA_OK=1"
where uv     >nul 2>&1 && set "UV_OK=1"
where python >nul 2>&1 && set "PY_OK=1"
where docker >nul 2>&1 && set "DOCKER_OK=1"
where mvn    >nul 2>&1 && set "MVN_OK=1"

rem -----------------------------------------------------------------
rem 4️⃣ Find or build fat-jar (SAFE)
rem -----------------------------------------------------------------
set "FATJAR="

for %%F in ("java\pdpot-tools\target\*-fatjar.jar") do (
    if exist "%%~fF" if not defined FATJAR (
        set "FATJAR=%%~fF"
    )
)

if not defined FATJAR if defined MVN_OK (
    echo No fat-jar found – building with Maven...
    pushd "java\pdpot-tools"
    mvn -B package
    popd

    for %%F in ("java\pdpot-tools\target\*-fatjar.jar") do (
        if exist "%%~fF" if not defined FATJAR (
            set "FATJAR=%%~fF"
        )
    )
)

rem -----------------------------------------------------------------
rem 5️⃣ Build command strings (SAFE)
rem -----------------------------------------------------------------
set "JAR_CMD="
if defined FATJAR if defined JAVA_OK (
    set "JAR_CMD=java -jar ""%FATJAR%"""
)

set "SCRIPT_CMD="
if defined UV_OK (
    set "SCRIPT_CMD=uv run pdpot-script"
) else if defined PY_OK (
    set "SCRIPT_CMD=python -m pdpot_script"
)

rem Docker fallback
set "USING_DOCKER="

if not defined JAR_CMD if defined DOCKER_OK (
    set "JAR_CMD=docker run --rm -v ""%CD%"":/data pdpot-container pdpot-tools"
    set "USING_DOCKER=1"
)

if not defined SCRIPT_CMD if defined DOCKER_OK (
    set "SCRIPT_CMD=docker run --rm -v ""%CD%"":/data pdpot-container pdpot-script"
    set "USING_DOCKER=1"
)

if not defined JAR_CMD (
    echo *** ERROR: No Java or Docker available ***
    exit /b 1
)

if not defined SCRIPT_CMD (
    echo *** ERROR: No uv/python or Docker available ***
    exit /b 1
)

rem -----------------------------------------------------------------
rem 6️⃣ Ensure Docker image exists (SAFE)
rem -----------------------------------------------------------------
if defined USING_DOCKER (
    set "IMG_PRESENT="

    for /f "delims=" %%I in ('docker images -q pdpot-container 2^>nul') do (
        set "IMG_PRESENT=1"
    )

    if not defined IMG_PRESENT (
        echo Docker image not found – building...
        docker build -t pdpot-container . || (
            echo *** ERROR: Docker build failed ***
            exit /b 1
        )
    )
)

rem -----------------------------------------------------------------
rem 7️⃣ Phase 1
rem -----------------------------------------------------------------
:phase1
if not exist "%SHEET_DIR%" md "%SHEET_DIR%" >nul 2>&1

%JAR_CMD% spreadsheet ^
    -i "%SOLUTION_DIR%\large-regular-colgen.zip" ^
    -i "%SOLUTION_DIR%\large-regular-mip.zip" ^
    -o "%SHEET_DIR%\output-large.xlsx"

%JAR_CMD% spreadsheet ^
    -i "%SOLUTION_DIR%\small-regular-colgen.zip" ^
    -i "%SOLUTION_DIR%\small-regular-mip.zip" ^
    -o "%SHEET_DIR%\output-small.xlsx"

goto :eof

rem -----------------------------------------------------------------
rem 8️⃣ Phase 2
rem -----------------------------------------------------------------
:phase2
if not exist "%TEX_DIR%" md "%TEX_DIR%" >nul 2>&1

pushd "script"

%SCRIPT_CMD% tables ^
    -d "..\%SHEET_DIR%\output-small.xlsx:Small" ^
    -d "..\%SHEET_DIR%\output-large.xlsx:Large" ^
    --output-dir "..\%TEX_DIR%"

%SCRIPT_CMD% comparison ^
    -d "..\%SHEET_DIR%\output-forbid-small.xlsx:Small" ^
    -d "..\%SHEET_DIR%\output-forbid-large.xlsx:Large" ^
    --output-dir "..\%TEX_DIR%"

%SCRIPT_CMD% delta-table ^
    -d "..\%SHEET_DIR%\output-small.xlsx:Small" ^
    -d "..\%SHEET_DIR%\output-large.xlsx:Large" ^
    --output-dir "..\%TEX_DIR%"

%SCRIPT_CMD% plots ^
    -d "..\%SHEET_DIR%\output-small.xlsx:Small" ^
    --output-dir "..\%TEX_DIR%"

%SCRIPT_CMD% plots ^
    -d "..\%SHEET_DIR%\output-large.xlsx:Large" ^
    --output-dir "..\%TEX_DIR%"

%SCRIPT_CMD% latex ^
    --output-dir "..\%TEX_DIR%"

popd

pushd "%TEX_DIR%"
pdflatex main.tex >nul
pdflatex main.tex >nul
popd

goto :eof

rem -----------------------------------------------------------------
rem 9️⃣ Main
rem -----------------------------------------------------------------
:main

if defined RUN_PHASE1 (
    echo ----------------------------------------
    echo Running Phase 1 – ZIP to XLSX
    echo ----------------------------------------
    call :phase1
)

if defined RUN_PHASE2 (
    echo ----------------------------------------
    echo Running Phase 2 – XLSX to LaTeX
    echo ----------------------------------------
    call :phase2
)

echo.
echo ==============================
echo   All requested work is done
echo ==============================

exit /b 0
