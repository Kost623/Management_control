# API документація — Management Control

Базовий URL: `http://localhost:8080`

Інтерактивна версія (з можливістю виконати запит прямо з браузера):
`/swagger-ui.html`. Специфікація OpenAPI (JSON): `/v3/api-docs`.

## Автентифікація

Є два незалежні механізми:

| Тип | Заголовок | Отримання | Хто використовує |
|---|---|---|---|
| Device-токен | `Authorization: Bearer mc_dev_...` | `POST /api/v1/agent/enroll` | Агент (клієнт) |
| JWT | `Authorization: Bearer <jwt>` | `POST /api/v1/auth/login` | Адміністратор (панель керування) |

---

## Auth API

### POST /api/v1/auth/login

Вхід адміністратора в панель керування.

**Автентифікація:** не потрібна.

**Тіло запиту:**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Успішний вхід |
| 400 | Невалідне тіло запиту |
| 401 | Невірний логін або пароль |

**Приклад відповіді (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-31T13:00:00Z",
  "username": "admin",
  "fullName": "Admin",
  "role": "ADMIN"
}
```

### GET /api/v1/auth/me

Повертає дані адміністратора, чий JWT передано в запиті.

**Автентифікація:** JWT (`bearerAuth`).

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Дані поточного адміністратора |
| 401 | Відсутній або недійсний JWT |
| 404 | Користувача з токена не знайдено в БД |

**Приклад відповіді (200):**
```json
{
  "token": null,
  "tokenType": "Bearer",
  "expiresAt": null,
  "username": "admin",
  "fullName": "Admin",
  "role": "ADMIN"
}
```

---

## Agent API

Усі ендпоінти, окрім `/enroll`, вимагають `Authorization: Bearer mc_dev_...`.

### POST /api/v1/agent/enroll

Обмінює одноразовий код реєстрації на device-токен.

**Автентифікація:** не потрібна.

**Тіло запиту:**
```json
{
  "code": "ABC123",
  "deviceId": "device-uuid-1",
  "hostname": "work-pc",
  "osName": "Windows 11",
  "osVersion": "11.0",
  "agentVersion": "0.1.0",
  "macAddress": "00:11:22:33:44:55",
  "ipAddress": "192.168.1.10",
  "osUser": "manager"
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 201 | Пристрій зареєстровано, токен видано |
| 400 | Невалідне тіло запиту |
| 404 | Код не знайдено, вже використаний або протермінований |

**Приклад відповіді (201):**
```json
{
  "token": "mc_dev_a1b2c3...",
  "deviceDbId": 7
}
```

### POST /api/v1/agent/heartbeat

Періодичний сигнал живості агента.

**Автентифікація:** device-токен.

**Тіло запиту:**
```json
{
  "agentVersion": "0.1.0",
  "ipAddress": "192.168.1.10"
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Прийнято |
| 401 | Відсутній або недійсний device-токен |

### POST /api/v1/agent/usage-sessions

Пакет інтервалів використання програм.

**Автентифікація:** device-токен.

**Тіло запиту:**
```json
{
  "sessions": [
    {
      "processName": "chrome.exe",
      "windowTitle": "Приклад",
      "exePath": "C:\\chrome.exe",
      "startedAt": "2026-10-30T10:00:00Z",
      "endedAt": "2026-10-30T10:10:00Z",
      "durationSeconds": 600,
      "idle": false,
      "foreground": true
    }
  ]
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Пакет прийнято |
| 400 | Невалідні дані в одному чи кількох елементах |
| 401 | Відсутній або недійсний device-токен |

**Приклад відповіді (200):**
```json
{ "accepted": 1 }
```

### POST /api/v1/agent/browsing

Пакет активних відвідувань сайтів.

**Автентифікація:** device-токен.

**Тіло запиту:**
```json
{
  "activities": [
    {
      "url": "https://example.com/page",
      "domain": "example.com",
      "browserName": "chrome",
      "tabTitle": "Приклад",
      "startedAt": "2026-10-30T10:00:00Z",
      "endedAt": "2026-10-30T10:05:00Z",
      "durationSeconds": 300,
      "idle": false
    }
  ]
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Пакет прийнято |
| 400 | Невалідні дані в пакеті |
| 401 | Відсутній або недійсний device-токен |

### POST /api/v1/agent/processes

Пакет подій запуску/завершення процесів.

**Автентифікація:** device-токен.

**Тіло запиту:**
```json
{
  "events": [
    {
      "processName": "chrome.exe",
      "exePath": "C:\\chrome.exe",
      "commandLine": "chrome.exe --new-window",
      "occurredAt": "2026-10-30T10:00:00Z",
      "action": "STARTED"
    }
  ]
}
```

`action`: `STARTED` або `EXITED`.

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Пакет прийнято |
| 400 | Невалідні дані в пакеті |
| 401 | Відсутній або недійсний device-токен |

### POST /api/v1/agent/location

Поточні координати пристрою.

**Автентифікація:** device-токен.

**Тіло запиту:**
```json
{
  "latitude": 50.4501,
  "longitude": 30.5234,
  "accuracy": 15.0,
  "capturedAt": "2026-10-30T10:00:00Z",
  "source": "WIFI"
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Координати прийнято |
| 400 | Невалідні координати |
| 401 | Відсутній або недійсний device-токен |

### POST /api/v1/agent/screenshots

Завантаження скриншота (`multipart/form-data`).

**Автентифікація:** device-токен.

**Поля форми:**
| Поле | Тип | Обов'язкове | Опис |
|---|---|---|---|
| `file` | файл (PNG) | так | Зображення скриншота |
| `takenAt` | string (ISO-8601) | ні | Час знімання; за замовчуванням — поточний |
| `trigger` | string | ні | `AUTO_RANDOM` або `COMMAND` |
| `width` | int | ні | Ширина в пікселях |
| `height` | int | ні | Висота в пікселях |
| `note` | string | ні | Довільна примітка |

**Статуси:**
| Код | Опис |
|---|---|
| 201 | Скриншот збережено |
| 400 | Файл відсутній або не є валідним зображенням |
| 401 | Відсутній або недійсний device-токен |

### GET /api/v1/agent/commands

Long-poll: очікування команд від сервера.

**Автентифікація:** device-токен.

**Query-параметри:**
| Параметр | Тип | Обов'язковий | Опис |
|---|---|---|---|
| `timeoutSeconds` | long | ні | Таймаут очікування, 1–120 с |

**Статуси:**
| Код | Опис |
|---|---|
| 200 | Список команд (може бути порожнім) |
| 401 | Відсутній або недійсний device-токен |

**Приклад відповіді (200):**
```json
[
  {
    "id": 42,
    "type": "LOCK",
    "payload": null
  }
]
```

### POST /api/v1/agent/commands/{commandId}/ack

Підтвердження виконання команди.

**Автентифікація:** device-токен.

**Path-параметри:** `commandId` — ID команди.

**Тіло запиту:**
```json
{
  "commandId": 42,
  "status": "EXECUTED",
  "result": "OK"
}
```

**Статуси:**
| Код | Опис |
|---|---|
| 204 | Підтвердження прийнято |
| 400 | commandId у тілі не збігається з commandId у URL |
| 401 | Відсутній або недійсний device-токен |
| 404 | Команду не знайдено для цього пристрою |

---

## Admin API

Усі ендпоінти вимагають `Authorization: Bearer <jwt>`. Дії, позначені **[ADMIN]**, додатково вимагають ролі `ADMIN` (не доступні для `VIEWER`).

### GET /api/v1/admin/devices

Список пристроїв з пагінацією.

**Query-параметри:**
| Параметр | Тип | За замовчуванням | Опис |
|---|---|---|---|
| `status` | enum | — | Фільтр: `ACTIVE`, `LOCKED`, `OFFLINE`, `BLOCKED` |
| `page` | int | 0 | Номер сторінки |
| `size` | int | 50 | Розмір сторінки (1–200) |

**Статуси:** `200` OK, `401` немає/недійсний JWT.

### GET /api/v1/admin/devices/{id}

Дані одного пристрою. **Статуси:** `200`, `401`, `404`.

### POST /api/v1/admin/devices/{id}/lock **[ADMIN]**

Тимчасове блокування (екран + сесія). Query: `reason` (опційно).
**Статуси:** `200`, `401`, `403`, `404`.

### POST /api/v1/admin/devices/{id}/unlock **[ADMIN]**

Зняти блокування. **Статуси:** `200`, `401`, `403`, `404`.

### POST /api/v1/admin/devices/{id}/block **[ADMIN]**

Заблокувати назавжди (незворотно). Query: `reason` (опційно).
**Статуси:** `200`, `401`, `403`, `404`.

### POST /api/v1/admin/devices/{deviceId}/commands **[ADMIN]**

Створити команду для пристрою.

**Тіло запиту:**
```json
{
  "type": "LOCK",
  "payload": null,
  "ttl": "PT1H"
}
```

`type`: `LOCK`, `UNLOCK`, `TAKE_SCREENSHOT`, `SHOW_MESSAGE`, `KILL_PROCESS`, `RUN_COMMAND`, `REBOOT`.

**Статуси:** `200`, `400`, `401`, `403`, `404`.

### GET /api/v1/admin/devices/{deviceId}/commands

Історія команд одного пристрою. Query: `page`, `size` (1–200).
**Статуси:** `200`, `401`.

### GET /api/v1/admin/commands

Усі команди в системі. Query: `page`, `size`.
**Статуси:** `200`, `401`.

### GET /api/v1/admin/devices/{deviceId}/usage-sessions

Сирі сесії використання програм за період.

**Query-параметри:**
| Параметр | Тип | За замовчуванням | Опис |
|---|---|---|---|
| `from` | date | вчора | Початок періоду (YYYY-MM-DD) |
| `to` | date | завтра | Кінець періоду |
| `page` | int | 0 | — |
| `size` | int | 100 | 1–500 |

**Статуси:** `200`, `401`.

### GET /api/v1/admin/devices/{deviceId}/browsing

Сира активність браузера за період. Параметри ті самі, що й у `usage-sessions`.
**Статуси:** `200`, `401`.

### GET /api/v1/admin/reports/daily

Звіт за добу: активний час, кількість скриншотів, топ програм/сайтів.

**Query-параметри:**
| Параметр | Тип | Обов'язковий | Опис |
|---|---|---|---|
| `deviceId` | long | так | ID пристрою |
| `date` | date | ні | За замовчуванням — поточна дата (UTC) |

**Статуси:** `200`, `401`, `404`.

**Приклад відповіді (200):**
```json
{
  "date": "2026-10-30",
  "activeSeconds": 3600,
  "screenshotCount": 12,
  "topApps": [{ "name": "chrome.exe", "seconds": 1800 }],
  "topSites": [{ "name": "example.com", "seconds": 600 }]
}
```

### GET /api/v1/admin/reports/top-apps

Топ програм за період (за замовчуванням — останні 7 днів).

**Query-параметри:** `deviceId` (обов'язковий), `from`, `to`, `limit` (1–200, за замовчуванням 20).
**Статуси:** `200`, `400` (кінцева дата раніша за початкову), `401`, `404`.

### GET /api/v1/admin/reports/top-sites

Топ доменів за період. Параметри ті самі, що й у `top-apps`.
**Статуси:** `200`, `400`, `401`, `404`.

### GET /api/v1/admin/screenshots

Список скриншотів пристрою.

**Query-параметри:** `deviceId` (обов'язковий), `date` (опційно), `page`, `size` (1–200).
**Статуси:** `200`, `401`.

### GET /api/v1/admin/screenshots/{id}/file

Бінарний вміст PNG-файлу скриншота. Content-Type: `image/png`, кешується 1 годину.
**Статуси:** `200`, `401`, `404`.

### POST /api/v1/admin/enrollment-codes **[ADMIN]**

Створити код реєстрації.

**Тіло запиту:**
```json
{
  "label": "Для нового ноутбука",
  "assignedTo": "manager-1",
  "boundDeviceId": null,
  "ttl": "PT1H"
}
```

**Статуси:** `200`, `400`, `401`, `403`.

### GET /api/v1/admin/enrollment-codes

Список активних кодів реєстрації.
**Статуси:** `200`, `401`.

### GET /api/v1/admin/audit-logs

Журнал дій адміністраторів. Query: `page`, `size` (1–200).
**Статуси:** `200`, `401`.

**Приклад відповіді (200, скорочено):**
```json
{
  "content": [
    {
      "id": 1,
      "actor": "admin",
      "action": "DEVICE_LOCK",
      "targetType": "DEVICE",
      "targetId": "7",
      "createdAt": "2026-10-30T10:00:00Z"
    }
  ],
  "totalElements": 1
}
```
