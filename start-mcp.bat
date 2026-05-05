@echo off
rem ============================================================
rem  HCL Notes MCP Server -- wrapper script
rem  IMPORTANT: Notes.exe must be CLOSED before client starts
rem ============================================================

rem 1. Kill all java.exe (silently)
taskkill /F /IM java.exe >nul 2>&1

rem 2. Fix notes.ini -- insert USING_LOCAL_SHARED_MEM=0 at line 2 (after [Notes])
powershell -NoProfile -Command "$f='D:\Program Files\Notes\notes.ini'; $lines=(Get-Content $f -Encoding Default) | Where-Object { $_ -notmatch '^(USING_LOCAL_SHARED_MEM|LOCAL_SHARED_MEM_SESSION_ID)=' }; $result=@($lines[0],'USING_LOCAL_SHARED_MEM=0','LOCAL_SHARED_MEM_SESSION_ID=0')+$lines[1..($lines.Count-1)]; Set-Content $f $result -Encoding Default" >nul 2>&1

rem 3. Start MCP server -- stdin/stdout inherited (MCP pipe)
"D:\Program Files\Notes\jvm\bin\java.exe" -Djava.library.path="D:\Program Files\Notes" -Dnotes.ini="D:\Program Files\Notes\notes.ini" -cp "D:\Alex\Claude\hcl-notes-mcp\target\hcl-notes-mcp-1.1.0.jar;D:\Alex\Claude\hcl-notes-mcp\lib\Notes.jar;D:\Alex\Claude\hcl-notes-mcp\lib\corba-omgapi.jar" -Dnotes.mail.local.db=mail/ashevele.nsf org.springframework.boot.loader.launch.JarLauncher