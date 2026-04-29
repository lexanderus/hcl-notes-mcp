@echo off
rem verify-baseline.cmd — Run unit tests and verify baseline before/after changes.
rem Usage: tools\verify-baseline.cmd

setlocal

set "NOTES_JVM=D:\Program Files\Notes\jvm\bin\java.exe"
set "NOTES_LIB=D:\Program Files\Notes"

echo.
echo === hcl-notes-mcp baseline verification ===
echo.

rem Kill any stale java.exe before tests (see CLAUDE.md — Notes JNI lock)
echo Killing stale java.exe processes...
taskkill /F /IM java.exe 2>nul
timeout /t 1 /nobreak >nul

echo Running unit tests (no Notes JNI)...
call mvn test -DskipITs 2>&1
if %ERRORLEVEL% neq 0 (
    echo FAIL: Unit tests failed.
    exit /b 1
)

echo.
echo Unit tests PASSED.
echo.
echo To run integration tests against sandbox (requires sandbox init):
echo   mvn verify -P sandbox-it
echo.
echo Kill java.exe after tests to release Notes ID lock:
echo   taskkill /F /IM java.exe

endlocal
