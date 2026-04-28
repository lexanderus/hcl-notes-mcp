@echo off
REM prepare-mcp-start.cmd
REM
REM Run BEFORE starting the hcl-notes MCP server (or after Notes.exe was used).
REM
REM What it does:
REM   1. Kills any stale java.exe that may hold the Notes ID lock
REM   2. Clears USING_LOCAL_SHARED_MEM in notes.ini so sinitThread() does not hang
REM
REM Usage:
REM   tools\prepare-mcp-start.cmd
REM   Then restart Claude Code (or the MCP client) to pick up a fresh server.

setlocal

set NOTES_INI=D:\Program Files\Notes\notes.ini

echo [1/2] Killing stale java.exe processes...
taskkill /F /IM java.exe 2>nul && echo   Done. || echo   No java.exe running.

echo [2/2] Clearing USING_LOCAL_SHARED_MEM in notes.ini...
powershell -NoProfile -Command ^
  "(Get-Content '%NOTES_INI%') ^
   -replace 'USING_LOCAL_SHARED_MEM=1','USING_LOCAL_SHARED_MEM=0' ^
   -replace 'LOCAL_SHARED_MEM_SESSION_ID=1','LOCAL_SHARED_MEM_SESSION_ID=0' ^
   | Set-Content '%NOTES_INI%'"
if %errorlevel%==0 (
    echo   Done. USING_LOCAL_SHARED_MEM set to 0.
) else (
    echo   WARNING: failed to update notes.ini. Check permissions.
)

echo.
echo Ready. Restart Claude Code now to start the MCP server cleanly.
endlocal
