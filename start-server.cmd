@echo off
rem start-server.cmd — Launch hcl-notes-mcp MCP server (used by Claude Code .claude.json)
rem Notes.jar is NOT inside the fat-jar (provided scope, ADR-1).
rem It must appear first on -cp so the Notes JVM bootclasspath takes precedence.

set "NOTES_DIR=D:\Program Files\Notes"
set "JAVA_EXE=%NOTES_DIR%\jvm\bin\java.exe"
set "NOTES_JAR=%NOTES_DIR%\jvm\lib\ext\Notes.jar"
set "JAR=D:\Alex\Claude\hcl-notes-mcp\target\hcl-notes-mcp-1.1.0.jar"

"%JAVA_EXE%" ^
  -Djava.library.path="%NOTES_DIR%" ^
  -Dnotes.ini="%NOTES_DIR%\notes.ini" ^
  -cp "%NOTES_JAR%;%JAR%" ^
  org.springframework.boot.loader.launch.JarLauncher
