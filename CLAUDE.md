# CLAUDE.md — hcl-notes-mcp

## Критически важно: убивать Java процессы после тестов

После каждого тестового запуска Java (JNI Notes) **обязательно** убивать все java.exe процессы:

```bash
taskkill //F //IM java.exe
```

**Почему**: Notes JNI устанавливает программный lock на `user.id` через shared memory. Даже после закрытия Notes.exe, зависший java.exe с инициализированным NotesThread продолжает держать lock. Это приводит к ошибке `The ID file is locked by another process` во всех последующих попытках (COM, JNI, Python win32com).

**Диагностика зависших процессов**:
```bash
wmic process where "name='java.exe'" get ProcessId,CommandLine
```

**Очистка**:
```bash
taskkill //F //PID <pid>
# или убить все Java разом:
taskkill //F //IM java.exe
```

---

## Архитектура

- **Java 21 / Spring Boot 3.4** MCP сервер
- **Notes JNI** (локальный режим через `NotesThread.sinitThread()` + `NotesFactory.createSession()`)
- **15 MCP инструментов**: mail, calendar, tasks, database

## Запуск MCP сервера

Конфиг в `C:/Users/ashevelev/.claude.json`:
```json
"command": "D:/Program Files/Notes/jvm/bin/java.exe",
"args": [
  "-Djava.library.path=D:/Program Files/Notes",
  "-Dnotes.ini=D:/Program Files/Notes/notes.ini",
  "-cp",
  "D:/Alex/Claude/hcl-notes-mcp/target/hcl-notes-mcp-1.0.4.jar;...",
  "org.springframework.boot.loader.launch.JarLauncher"
]
```

## Требования для работы Notes API

1. **Notes клиент должен быть ЗАКРЫТ** (или не запускался) — иначе ID файл заблокирован
2. **Нет зависших java.exe** — см. выше
3. **Пароль Notes ID** — `Initialize("")` не работает если ID защищён паролем; нужен `Initialize("password")`

## Notes lock — механизм

- `user.id` физически НЕ блокируется на уровне ОС (файл readable)
- Lock программный — через Windows shared memory / named mutex
- `~notes.lck` файл создаётся Notes.exe, но java.exe с JNI тоже удерживает lock через Notes C API internals
- `USING_LOCAL_SHARED_MEM=1` в notes.ini — флаг выставляется Notes при старте

## Структура кода

```
src/main/java/com/hcl/notes/mcp/
  adapter/     — MailAdapter, CalendarAdapter, TaskAdapter, DatabaseAdapter
  connection/  — NotesSessionPool, NotesConnectionFactory
  config/      — McpServerConfig
  tools/       — MailTools, CalendarTools, TaskTools, DatabaseTools
  model/       — MailMessage, CalendarEvent, NotesTask, NotesDocument
```

## Сборка JAR

**ВАЖНО**: Не пересобирать fat JAR через `jar -cfm` — это сломает Spring Boot (переупакует вложенные JAR с компрессией). Правильный способ:
1. Скопировать существующий fat JAR как базу
2. Добавить/обновить только изменённые `.class` файлы через `jar -uf`

## Параметры почтового сервера (из Python скриптов)

- Mail server: `eumail/iba` (DNS: eumail.ibagroup.eu, IP: 10.25.64.20)
- Mail DB: `mail7\ashevele.nsf`
- Локальная реплика: `D:/Program Files/Notes/Data/mail/ashevele.nsf` (4 GB)
- Notes data dir: `D:/Program Files/Notes/Data/`
