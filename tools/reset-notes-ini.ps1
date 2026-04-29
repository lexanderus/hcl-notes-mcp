# reset-notes-ini.ps1
# Resets USING_LOCAL_SHARED_MEM and LOCAL_SHARED_MEM_SESSION_ID to 0 in notes.ini.
# Run automatically via Claude Code SessionStart hook before hcl-notes MCP server starts.

$notesIni = 'D:\Program Files\Notes\notes.ini'

(Get-Content $notesIni) `
    -replace 'USING_LOCAL_SHARED_MEM=1',    'USING_LOCAL_SHARED_MEM=0' `
    -replace 'LOCAL_SHARED_MEM_SESSION_ID=1','LOCAL_SHARED_MEM_SESSION_ID=0' |
    Set-Content $notesIni

Write-Host "Notes.ini reset: USING_LOCAL_SHARED_MEM=0"
