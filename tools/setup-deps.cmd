@echo off
rem setup-deps.cmd — Install Notes.jar into local Maven repository.
rem Run once after cloning the project (Notes client must be installed).
rem Usage: tools\setup-deps.cmd [NOTES_DIR]

setlocal

set "NOTES_DIR=%~1"
if "%NOTES_DIR%"=="" set "NOTES_DIR=D:\Program Files\Notes"

set "NOTES_JAR=%NOTES_DIR%\jvm\lib\ext\Notes.jar"

echo.
echo === hcl-notes-mcp dependency setup ===
echo Notes dir : %NOTES_DIR%
echo Notes.jar : %NOTES_JAR%
echo.

if not exist "%NOTES_JAR%" (
    echo ERROR: Notes.jar not found.
    echo   Expected: %NOTES_JAR%
    echo   Adjust NOTES_DIR or pass path as first argument: setup-deps.cmd "C:\HCL\Notes"
    exit /b 1
)

call mvn install:install-file ^
    -Dfile="%NOTES_JAR%" ^
    -DgroupId=com.hcl.notes ^
    -DartifactId=notes ^
    -Dversion=14.0 ^
    -Dpackaging=jar ^
    -DgeneratePom=true

if %ERRORLEVEL% neq 0 (
    echo ERROR: mvn install:install-file failed.
    exit /b 1
)

echo.
echo Done. Notes.jar 14.0 installed to local Maven repository.
echo You can now run: mvn package -DskipTests
endlocal
