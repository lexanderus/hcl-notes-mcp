@echo off
rem setup-deps.cmd — Install Notes.jar into local Maven repository.
rem Run once after cloning the project (Notes client must be installed).
rem Usage: tools\setup-deps.cmd [NOTES_DIR]

setlocal

set "NOTES_DIR=%~1"
if "%NOTES_DIR%"=="" set "NOTES_DIR=D:\Program Files\Notes"

set "PROJECT_DIR=%~dp0.."
set "NOTES_JAR=%NOTES_DIR%\ndext\Notes.jar"
set "CORBA_JAR=%PROJECT_DIR%\lib\corba-omgapi.jar"

echo.
echo === hcl-notes-mcp dependency setup ===
echo Notes dir    : %NOTES_DIR%
echo Notes.jar    : %NOTES_JAR%
echo corba-omgapi : %CORBA_JAR%
echo.

if not exist "%NOTES_JAR%" (
    rem Fallback: some Notes installations put it under jvm/lib/ext
    set "NOTES_JAR=%NOTES_DIR%\jvm\lib\ext\Notes.jar"
)
if not exist "%NOTES_JAR%" (
    echo ERROR: Notes.jar not found.
    echo   Tried: %NOTES_DIR%\ndext\Notes.jar
    echo   Tried: %NOTES_DIR%\jvm\lib\ext\Notes.jar
    echo   Adjust NOTES_DIR or pass path as first argument: setup-deps.cmd "C:\HCL\Notes"
    exit /b 1
)

echo Installing Notes.jar ...
call mvn install:install-file ^
    -Dfile="%NOTES_JAR%" ^
    -DgroupId=com.hcl.notes ^
    -DartifactId=notes ^
    -Dversion=14.0 ^
    -Dpackaging=jar ^
    -DgeneratePom=true

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to install Notes.jar.
    exit /b 1
)

echo Installing corba-omgapi.jar ...
call mvn install:install-file ^
    -Dfile="%CORBA_JAR%" ^
    -DgroupId=org.glassfish.corba ^
    -DartifactId=glassfish-corba-omgapi ^
    -Dversion=4.2.4 ^
    -Dpackaging=jar ^
    -DgeneratePom=true

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to install corba-omgapi.jar.
    exit /b 1
)

echo.
echo Done. Dependencies installed to local Maven repository.
echo You can now run: mvn package -DskipTests
endlocal
