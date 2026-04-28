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

## Git push

Ветка `master` — первый push требует установки upstream:

```bash
git push --set-upstream origin master
```

Последующие пуши — просто `git push`.

Remote: `https://github.com/lexanderus/hcl-notes-mcp.git`

## Сборка JAR

**ВАЖНО**: Не пересобирать fat JAR через `jar -cfm` — это сломает Spring Boot (переупакует вложенные JAR с компрессией). Правильный способ:
1. Скопировать существующий fat JAR как базу
2. Добавить/обновить только изменённые `.class` файлы через `jar -uf`

## Параметры почтового сервера (из Python скриптов)

- Mail server: `eumail/iba` (DNS: eumail.ibagroup.eu, IP: 10.25.64.20)
- Mail DB: `mail7\ashevele.nsf`
- Локальная реплика: `D:/Program Files/Notes/Data/mail/ashevele.nsf` (4 GB)
- Notes data dir: `D:/Program Files/Notes/Data/`

---

## Каталог баз данных IBA (IS Applications)

**Путь**: `IBASRV/IBA!!iba\Application.nsf`  
**Заголовок**: IS Applications  
**Назначение**: Каталог всех 1086+ баз/приложений IBA Group с путями, серверами, владельцами.

### Как найти путь к нужной базе

1. `notesGetViewEntries("IBASRV/IBA!!iba\Application.nsf", "02. Приложение\Все\По наименованию", limit=300)`
2. Смотреть поля каждой записи:
   - `App_AppName` — название приложения (может быть в cp1251, отображается как ??)
   - `SAVED_APPNAME` — то же, иногда на английском
   - `App_AppDbServer` — сервер, например `CN=IBASRV/O=IBA`
   - `App_AppDbPath` — путь к NSF, например `iba\Empl3iG.nsf`
   - `SAVED_APPDBSERVER` / `SAVED_APPDBPATH` — то же, saved-копии
   - `App_AppStatusName` — статус (Access by request, Active, Lotus template…)

### Известные базы

| База | Сервер | Путь | Описание |
|------|--------|------|----------|
| HR Info IBA Group | IBASRV/IBA | `iba\Empl3iG.nsf` | Кадровая база (Инфо Кадры) |
| Employee Certificates | IBASRV/IBA | `iba\emplcert.nsf` | Сертификаты сотрудников |
| Personnel Records IBA Group | IBASRV/IBA | `ibagroup\Empl52-g.nsf` | Кадровые записи |
| Personal Data Processing Registry | IBASRV/IBA | `iba\PersDataeu.nsf` | Реестр персональных данных |
| Personnel Records Cz | IBASRV/IBA | `cz\Emplcz.nsf` | Кадры Чехия |
| Personnel Records BG | IBASRV/IBA | `BG\EmployBG.nsf` | Кадры Болгария |

### Структура базы HR Info IBA Group (`IBASRV/IBA!!iba\Empl3iG.nsf`)

**Использовать только эти виды** (остальные технические/не нужны):

```
Управленческая структура:
  По фамилиям
  По подразделениям
  By Departments
  По странам пребывания
  По подразделениям и стране

Совместители IBA Group
Контрактники МДА
Внештатные

Управленческая структура (отчёты):
  По подразделениям по месту
  По странам и площадкам
  Отчет по VPN
  VPN нарушения
  Possibility to change department
  По должностям\по должностям

Исполнение обязанностей
Образование:
  По ВУЗу
  По году окончания
  По типу образования
  По ученой степени

Статистика
Уволенные
```

### Каталог почтовых ящиков / mail-in баз

`notesGetViewEntries("eumail/iba!!names.nsf", "Server\Mail-In Databases and Resources", limit=111)`  
Поля: `FullName`, `MailServer`, `MailFile` (реальный путь к NSF), `InternetAddress`

---

## Domino серверы IBA (из names.nsf)

Вид `($Servers)` в `eumail/iba!!names.nsf` — 20 серверов:

| ServerName | Domain |
|-----------|--------|
| CN=apps14/O=IBA | IBA |
| CN=chis-serv/O=IBA | CHIS |
| CN=Domino-archive/O=IBA | IBA |
| CN=domino14/O=IBA | delete-iba |
| CN=domino14b/O=IBA | IBA |
| CN=DominoRest/O=IBA | IBA |
| CN=domserv145/O=IBA | IBA |
| CN=EU-APPS/O=IBA | IBA |
| CN=eu-input/O=IBA | IBA |
| CN=EU-LEI/O=IBA | IBA |
| CN=EUMAIL/O=IBA | IBA |
| CN=G-OUT/O=IBA | delete-IBA |
| CN=IBASRV/O=IBA | IBA |
| CN=IBASRV2/O=IBA | IBA |
| CN=ln-edc/O=IBA | IBA |
| CN=nomad-cd1/O=IBA | IBA |
| CN=nomad-cd2/O=IBA | IBA |
| CN=nomad-cd3/O=IBA | IBA |
| CN=tr-gate/O=IBA | IBA |
| CN=webmail/O=ibagomel/C=by | IBAGOMEL |
