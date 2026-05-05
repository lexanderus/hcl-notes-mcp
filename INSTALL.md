# HCL Notes MCP Server — Installation

MCP-сервер для HCL Notes / Domino: 17 инструментов (mail / calendar / tasks / databases) для LLM-агентов через MCP stdio.

## Требования

| Компонент | Версия |
|---|---|
| HCL Notes Client | 11.x / 12.x / 14.x (установлен локально) |
| Java | 21 (поставляется внутри Notes Client: `<NotesDir>/jvm/`) |
| Maven | 3.9+ (только для пересборки из исходников) |
| OS | Windows 10/11 (Linux/macOS — теоретически работают, не тестировались) |

> ⚠️ Сервер работает **только в режиме LOCAL JNI**. Без локально установленного Notes Client + ID файла он не запустится.

## Шаг 1 — Подготовить Notes Client

1. Установить HCL Notes (например, в `D:\Program Files\Notes\`).
2. Один раз залогиниться в Notes-клиенте, чтобы создать ID-файл и ввести пароль.
3. **Закрыть Notes.exe** (он держит lock на user.id; параллельно с MCP запускать нельзя).
4. В `notes.ini` (`<NotesDir>\notes.ini`) убедиться, что:
   ```
   USING_LOCAL_SHARED_MEM=0
   LOCAL_SHARED_MEM_SESSION_ID=0
   ```
   (`start-mcp.bat` чинит это автоматически перед каждым стартом.)

## Шаг 2 — Получить артефакты

### Вариант A — собрать из исходников

```
git clone https://github.com/lexanderus/hcl-notes-mcp.git
cd hcl-notes-mcp
```

Скопировать HCL-библиотеки из Notes Client в `lib/` (не лежат в git — лицензия HCL):
```
copy "D:\Program Files\Notes\jvm\lib\ext\Notes.jar" lib\
copy "D:\Program Files\Notes\jvm\lib\ext\corba-omgapi.jar" lib\
```

Собрать:
```
mvn clean package -DskipTests
```
Готовый fat-JAR: `target/hcl-notes-mcp-1.1.0.jar`.

### Вариант B — взять готовый

Скопировать с исходной машины:
- `target/hcl-notes-mcp-1.1.0.jar`
- `lib/Notes.jar`, `lib/corba-omgapi.jar`
- `start-mcp.bat`

## Шаг 3 — Поправить пути в `start-mcp.bat`

Открыть `start-mcp.bat` и подставить свои пути:
- `D:\Program Files\Notes` → путь к вашему Notes Client
- `D:\Alex\Claude\hcl-notes-mcp` → путь к репо/jar/lib

**Важно:** файл должен быть в **UTF-8 без BOM**. Иначе cmd.exe не распознает `@echo off` и начнёт эхать команды в stdout, что ломает MCP-протокол.

Проверка отсутствия BOM (PowerShell):
```powershell
(Get-Content start-mcp.bat -Encoding Byte -TotalCount 4) -join ' '
# Должно быть: 64 101 99 104  (а не 239 187 191 64)
```

## Шаг 4 — Пароль Notes ID

> **Никогда не храните пароль в config-файлах MCP-клиентов.** Все примеры в Шаге 5 рассчитаны на то, что пароль лежит в **User Environment Variable** Windows. Так его не видно в JSON/YAML, не утечёт при коммите конфига и не светится в скриншотах.

PowerShell (один раз, перманентно для пользователя):
```powershell
[Environment]::SetEnvironmentVariable('NOTES_PASSWORD','<ваш_пароль>','User')
```

Проверка:
```powershell
[Environment]::GetEnvironmentVariable('NOTES_PASSWORD','User')
```

**Важно:** новое значение env подхватывают только **новые** процессы. После `setx` нужно либо **перезайти в Windows-сессию**, либо перезапустить тот процесс-родитель, из которого стартует MCP-клиент (Goose/Antigravity/Cline/Claude Code). Уже работающие приложения старое окружение не обновят.

### Как это работает

`start-mcp.bat` запускается через `cmd.exe`, который наследует User env. `java.exe`, в свою очередь, наследует env от `cmd.exe`. Spring Boot читает `NOTES_PASSWORD` из системного env (см. `application.yml:notes.connection.password=${NOTES_PASSWORD:}`). Поэтому в `env`-блоке клиента эту переменную **указывать не нужно** — она и так дойдёт по цепочке.

### Удалить пароль из ранее заведённых конфигов

Если вы раньше клали пароль в `env`-блок MCP-клиента — уберите его. Поля типа `NOTES_CONNECTION_MODE: LOCAL` и `NOTES_MAIL_LOCAL_DB: ...` оставляйте, они не секретные.

### Альтернатива (если клиент изолирует env и не наследует User env)

Некоторые MCP-клиенты передают дочернему процессу **только** то, что явно указано в `env`-блоке, и не пробрасывают User env. Симптом: в логе `mcp-server.log` ошибка `Initialize failed` или `Wrong password`. В этом случае:

- либо вернуть `NOTES_PASSWORD` в `env`-блок именно этого клиента,
- либо добавить в начало `start-mcp.bat` перед запуском java:
  ```bat
  for /f "tokens=2*" %%a in ('reg query "HKCU\Environment" /v NOTES_PASSWORD 2^>nul ^| find "NOTES_PASSWORD"') do set "NOTES_PASSWORD=%%b"
  ```
  Это явно прочитает значение из реестра пользователя — независимо от родительского env.

## Шаг 5 — Подключить к MCP-клиенту

### Claude Code / Claude Desktop

`~/.claude.json`:
```json
{
  "mcpServers": {
    "hcl-notes": {
      "type": "stdio",
      "command": "cmd.exe",
      "args": ["/c", "C:\\path\\to\\hcl-notes-mcp\\start-mcp.bat"],
      "env": {
        "NOTES_CONNECTION_MODE": "LOCAL",
        "NOTES_MAIL_LOCAL_DB": "mail/<ваш_файл>.nsf"
      }
    }
  }
}
```

`NOTES_PASSWORD` берётся из user-env, в JSON НЕ пишем.

### Goose Desktop

`%APPDATA%\Block\goose\config\config.yaml`:
```yaml
extensions:
  hcl-notes:
    enabled: true
    type: stdio
    name: hcl-notes
    cmd: cmd.exe
    args:
      - /c
      - C:\path\to\hcl-notes-mcp\start-mcp.bat
    envs:
      NOTES_CONNECTION_MODE: LOCAL
      NOTES_MAIL_LOCAL_DB: mail/<ваш_файл>.nsf
    timeout: 240
```

### Google Antigravity (нативный MCP)

`~/.gemini/antigravity/mcp_config.json`:
```json
{
  "mcpServers": {
    "hcl-notes": {
      "command": "cmd.exe",
      "args": ["/c", "C:\\path\\to\\hcl-notes-mcp\\start-mcp.bat"],
      "env": {
        "NOTES_CONNECTION_MODE": "LOCAL",
        "NOTES_MAIL_LOCAL_DB": "mail/<ваш_файл>.nsf"
      }
    }
  }
}
```

Проверить через `Ctrl+Shift+P` → `Antigravity: Manage MCP Servers`.

### Cline (VS Code / Antigravity / Cursor)

`<vscode-user>/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json`:
```json
{
  "mcpServers": {
    "hcl-notes": {
      "type": "stdio",
      "command": "cmd.exe",
      "args": ["/c", "C:\\path\\to\\hcl-notes-mcp\\start-mcp.bat"],
      "env": {
        "NOTES_CONNECTION_MODE": "LOCAL",
        "NOTES_MAIL_LOCAL_DB": "mail/<ваш_файл>.nsf"
      },
      "disabled": false,
      "autoApprove": [],
      "timeout": 3600
    }
  }
}
```

Где конкретно `<vscode-user>` для популярных IDE:
- VS Code: `%APPDATA%\Code\User`
- Cursor: `%APPDATA%\Cursor\User`
- Antigravity: `%APPDATA%\Antigravity\User`

## Шаг 6 — Проверка

В клиенте должны появиться 17 инструментов: `notesGetInbox`, `notesSearchDocuments`, `notesGetDocument`, `notesListViews`, `notesGetViewEntries`, `notesOpenDatabase`, `notesGetCalendarEvents`, `notesCreateEvent`, `notesGetTasks`, `notesUpdateDocument`, `notesDeleteDocument`, `notesGetAttachment`, `notesMoveToFolder`, `notesFormulaSearch`, `notesCreateDocument`, `notesGetMailMessage`, `notesSendMail`.

Лог: `<repo>/logs/mcp-server.log`. При успешном старте увидите:
```
Notes session initialized on 'notes-jni-1': user=CN=...
Started McpServerApplication in X.X seconds
```

## Типовые проблемы

| Симптом | Причина / решение |
|---|---|
| `process quit before initialization: stderr =` | BOM в `.bat` или ConsoleAppender пишет в stdout. Проверить кодировку `.bat`, что `logback-spring.xml` в jar и нет блока `logging:` в `application.yml`. |
| `The ID file is locked by another process` | Notes.exe запущен или зависший java.exe. `taskkill /F /IM java.exe` + закрыть Notes.exe. |
| `NotesThread.sinitThread()` висит вечно | `USING_LOCAL_SHARED_MEM=1` в `notes.ini`. Обнулить (`start-mcp.bat` это делает автоматом). |
| Старт > 60 c (timeout в клиенте) | Поднять `timeout` в конфиге MCP-клиента до 240–600 секунд. JNI lazy init может занимать 90+ с при первом запуске. |

## Лицензия / proprietary компоненты

`lib/Notes.jar` и `lib/corba-omgapi.jar` — собственность HCL Software, **в репозиторий не коммитятся**. Каждый пользователь должен скопировать их из своего легально установленного Notes Client.
