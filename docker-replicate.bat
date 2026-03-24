@echo off
rem ==========================================================
rem run-pdpot.bat
rem   1. Verify that the Docker client is available.
rem   2. Verify that an image called "pdpot-container" exists.
rem      If it does not, build it from the Dockerfile in the
rem      current directory.
rem   3. Run the container, mounting the current folder at /data
rem      and executing the command "pdpot-replicate".
rem ==========================================================

rem -----------------------------------------------------------------
rem 1) Check that the Docker executable can be found
rem -----------------------------------------------------------------
where docker >NUL 2>&1
if errorlevel 1 (
    echo.
    echo ==============================
    echo ERROR: Docker is NOT installed or not in your PATH.
    echo Please install Docker Desktop for Windows and
    echo make sure "docker.exe" is reachable from a command prompt.
    echo ==============================
    goto :eof
)

rem -----------------------------------------------------------------
rem 2) Verify that the Docker daemon is running
rem -----------------------------------------------------------------
docker version >NUL 2>&1
if errorlevel 1 (
    echo.
    echo ==============================
    echo ERROR: Docker client cannot talk to the Docker daemon.
    echo Is Docker Desktop running?  Start it and try again.
    echo ==============================
    goto :eof
)

rem -----------------------------------------------------------------
rem 3) Does the image "pdpot-container" already exist?
rem -----------------------------------------------------------------
set "IMAGE_NAME=pdpot-container"
docker image inspect "%IMAGE_NAME%" >nul 2>&1
if errorlevel 1 (
    echo.
    echo "Image "%IMAGE_NAME%" NOT found – building it now..."
    echo -------------------------------------------------
    docker build -t %IMAGE_NAME% .
    if errorlevel 1 (
        echo.
        echo ==============================
        echo ERROR: Docker build failed.
        echo ==============================
        goto :eof
    )
    echo -------------------------------------------------
    echo Build finished.
) else (
    echo.
    echo "Image "%IMAGE_NAME%" already exists (ID=%IMG_ID%)"
)

rem -----------------------------------------------------------------
rem 4) Run the container
rem -----------------------------------------------------------------
rem   %cd% is the full path of the current folder.  It is quoted so that
rem   spaces are handled correctly.
rem   The container is started with "--rm" so it disappears when it
rem   exits.  The command inside the container is "pdpot-replicate".
echo.
echo Starting container "%IMAGE_NAME%" …
docker run --rm ^
    -v "%cd%":/data ^
    %IMAGE_NAME% pdpot-replicate

rem -----------------------------------------------------------------
rem 5) Done
rem -----------------------------------------------------------------
if errorlevel 1 (
    echo.
    echo ==============================
    echo ERROR: The container exited with a non-zero status.
    echo ==============================
) else (
    echo.
    echo Container finished successfully.
)

rem End of script