# Management Control

## Призначення

Проект призначений для відслідковування робота менеджера на своїй ноутбуці. По клієнт-серверній
архітектурі агент на ноутбуку збирає такі дані:

- Час користування;
- Перелік сайтів, які були активні за робочий день, і час їх використання (а не просто находження);
- Перелік програмного забезпечення, яке запускалося;
- Автоскріни в рандомний момент часу — мінімум 10 і до 35 на день;
- При потребі — блокування ноутбука;
- Відстежування місцезнаходження пристрою;
- Унеможливлення видалення програмного продукту з ноутбука менеджера;
- Надсилання всіх даних на сервер — на пристрої жодних даних не залишається.

## Структура проєкту

```
Management_control/
├── pom.xml                  # базовий Maven-проєкт (parent, Java 21)
├── mvnw / mvnw.cmd          # Maven Wrapper
├── docker-compose.yml       # MariaDB + сервер
├── server/                  # СЕРВЕРНА частина (Java / Spring Boot)
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/managementcontrol/
│       │   ├── common/      # ApiError, обробка винятків
│       │   ├── config/      # SecurityConfig, властивості, OpenAPI, bootstrap адміністратора
│       │   ├── domain/      # JPA-сутності (пристрій, сесії, скріни, локація, команди)
│       │   ├── repository/  # Spring Data JPA
│       │   ├── security/    # токени пристроїв, JWT, фільтр автентифікації
│       │   ├── service/     # бізнес-логіка
│       │   └── web/         # REST API: agent/, admin/, auth/
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__init.sql   # схема БД (Flyway)
├── agent/                   # КЛІЄНТСЬКА частина (C++ / Windows)
│   ├── CMakeLists.txt
│   ├── include/mc/          # json, http, config, platform, activity, agent, screenshot
│   ├── src/                 # реалізація + platform_win.cpp / platform_stub.cpp
│   └── tests/
└── scripts/smoke_test.sh    # наскрізна перевірка API
```

## Технології

- Мова програмування (сервер): Java 21, Spring Boot 3.5;
- Мова програмування (агент): C++20, CMake, Win32 API + GDI+;
- База даних: MariaDB (міграції Flyway);
- Автентифікація: device-токен для агента (`mc_dev_…`), JWT для адміністратора.

## Залежності

Сервер: `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`,
`flyway-core` + `flyway-mysql`, `mariadb-java-client`, `jjwt`, `springdoc-openapi`.

Агент: `libcurl` (Linux — `libcurl4-openssl-dev`, Windows — vcpkg).
Зовнішніх JSON-бібліотек немає — власний мінімальний парсер.

## Запуск проєкту

### 1. Підняти базу даних

```bash
docker compose up -d db
```

або підключитися до наявного MariaDB і створити базу `management_control`.

### 2. Запустити сервер

```bash
./mvnw -pl server spring-boot:run
```

Змінні оточення (усі необов'язкові, див. `server/src/main/resources/application.yml`):

| Змінна | За замовчуванням | Опис |
|--------|------------------|------|
| `MC_DB_URL` | `jdbc:mariadb://localhost:3307/management_control` | JDBC-URL бази |
| `MC_DB_USER` / `MC_DB_PASSWORD` | `mc_user` / `mc_secret_pass` | доступ до БД |
| `MC_PORT` | `8080` | порт сервера |
| `MC_JWT_SECRET` | `change-me-…` | секрет підпису JWT (≥ 32 байти) |
| `MC_ADMIN_USERNAME` / `MC_ADMIN_PASSWORD` | `admin` / `admin123` | початковий адміністратор |
| `MC_SCREENSHOTS_PATH` | `./data/screenshots` | каталог для скриншотів |

### 3. Зібрати агента

```bash
cd agent
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
ctest --test-dir build --output-on-failure
```

Детальніше — у [agent/README.md](agent/README.md).

### 4. Swagger UI

<http://localhost:8080/swagger-ui.html> — повний опис REST API.

## REST API (скорочено)

### Агент (`/api/v1/agent`, авторизація: `Bearer mc_dev_…`)

| Метод | Шлях | Опис |
|-------|------|------|
| POST | `/enroll` | обмін коду реєстрації на device-токен |
| POST | `/heartbeat` | підтвердження живості пристрою |
| POST | `/usage-sessions` | пакет інтервалів використання програм |
| POST | `/browsing` | пакет активних відвідувань сайтів |
| POST | `/processes` | пакет подій запуску/завершення процесів |
| POST | `/location` | координати пристрою |
| POST | `/screenshots` | завантаження скриншота (multipart) |
| GET | `/commands` | long-poll команд (LOCK, TAKE_SCREENSHOT, …) |
| POST | `/commands/{id}/ack` | підтвердження виконання команди |

### Адміністратор (`/api/v1/admin`, авторизація: `Bearer <JWT>`)

| Метод | Шлях | Опис |
|-------|------|------|
| POST | `/auth/login` | вхід, отримання JWT |
| GET | `/devices` | список пристроїв |
| POST | `/devices/{id}/lock` \| `/unlock` \| `/block` | керування блокуванням |
| POST | `/devices/{id}/commands` | надіслати команду пристрою |
| GET | `/devices/{id}/usage-sessions` \| `/browsing` | сирі дані за період |
| GET | `/screenshots` \| `/screenshots/{id}/file` | галерея скриншотів |
| GET | `/reports/daily` \| `/top-apps` \| `/top-sites` | звітність |
| POST | `/enrollment-codes` | створити код реєстрації |
| GET | `/audit-logs` | журнал дій адміністраторів |

## Перевірка

```bash
# наскрізний тест API (потрібен запущений сервер)
./scripts/smoke_test.sh http://localhost:8080

# тести сервера
./mvnw test

# тести агента
ctest --test-dir agent/build --output-on-failure
```
## Статус проєкту

Проєкт перебуває на стадії активної розробки. Базова структура (сервер на Spring Boot,
агент на C++, схема БД) готова, API частково реалізовано. Наступні кроки:

- Реалізувати логіку збору скриншотів на боці агента;
- Додати обробку команд блокування пристрою;
- Покрити ключову бізнес-логіку тестами.

## Вимоги до проєкту

## Структура бази даних

База даних MariaDB, схема розгортається через Flyway-міграції
(`server/src/main/resources/db/migration/`).

### Таблиці

| Таблиця | Призначення | Зв'язок з `devices` |
|---|---|---|
| `devices` | Зареєстровані пристрої (ноутбуки менеджерів) | — |
| `usage_sessions` | Інтервали використання програм | 1:N |
| `browsing_activities` | Відвідування сайтів | 1:N |
| `process_events` | Події запуску/завершення процесів | 1:N |
| `screenshots` | Метадані зроблених скриншотів | 1:N |
| `location_pings` | Геолокація пристрою | 1:N |
| `remote_commands` | Команди адміністратора (блокування, скриншот тощо) | 1:N |
| `enrollment_codes` | Коди реєстрації нових пристроїв | N:1 (через `device_id`) |
| `admin_users` | Облікові записи адміністраторів | N:1 (через `assigned_manager_id`) |
| `audit_logs` | Журнал дій адміністраторів | — |

### Первинні та зовнішні ключі

- Усі таблиці мають сурогатний первинний ключ `id BIGINT AUTO_INCREMENT`.
- `usage_sessions`, `browsing_activities`, `process_events`, `screenshots`,
  `location_pings`, `remote_commands` посилаються на `devices.id`
  через `device_id` з `ON DELETE CASCADE` — видалення пристрою видаляє
  всю пов'язану телеметрію.
- `enrollment_codes.bound_device_id` та `used_by_device_id` посилаються
  на `devices.device_id` (бізнес-ключ, `UNIQUE`).
- `devices.assigned_manager_id` посилається на `admin_users.id`.

### Обмеження

- `NOT NULL` — на всіх обов'язкових полях (ідентифікатори, часові мітки, статуси).
- `UNIQUE` — `devices.device_id`, `enrollment_codes.code`, `admin_users.username`.
- `DEFAULT` — булеві прапорці (`tamper_protection`, `active`, `enabled` тощо)
  та лічильники (`duration_seconds`, `size_bytes`).
- `CHECK` — значення статусів і типів обмежені відповідними enum-набором
  (наприклад, `devices.status` лише `ACTIVE`/`LOCKED`/`OFFLINE`/`BLOCKED`),
  географічні координати обмежені коректним діапазоном,
  числові лічильники — невід'ємні.

### Перевірка структури

Підняти базу та застосувати міграції:

```bash
docker compose up -d db
./mvnw -pl server spring-boot:run
```

Переглянути застосовані міграції:

```bash
docker compose exec db mariadb -u mc_user -pmc_secret_pass management_control \
  -e "SELECT version, description, success FROM flyway_schema_history;"
```

Переглянути список таблиць:

```bash
docker compose exec db mariadb -u mc_user -pmc_secret_pass management_control \
  -e "SHOW TABLES;"
```

Переглянути визначення таблиці разом з обмеженнями:

```bash
docker compose exec db mariadb -u mc_user -pmc_secret_pass management_control \
  -e "SHOW CREATE TABLE devices\G"
```

Перевірити, що `CHECK`-обмеження реально відхиляє некоректні дані:

```bash
docker compose exec db mariadb -u mc_user -pmc_secret_pass management_control \
  -e "INSERT INTO devices (device_id, hostname, status, enrolled_at, token_hash) VALUES ('test', 'test-pc', 'INVALID_STATUS', NOW(), 'x');"
```

Очікуваний результат — помилка `CONSTRAINT chk_devices_status failed`, а не успішний запис.

### Функціональні вимоги

- Агент збирає час активного використання ноутбука;
- Агент фіксує перелік відвіданих сайтів та час активного перебування на них;
- Агент фіксує перелік запущеного програмного забезпечення;
- Агент робить автоматичні скріншоти в рандомні моменти часу (10–35 на день);
- Адміністратор може дистанційно заблокувати ноутбук;
- Сервер відстежує геолокацію пристрою;
- Агента неможливо видалити без прав адміністратора;
- Усі зібрані дані передаються на сервер; локально на пристрої нічого не зберігається.

### Нефункціональні вимоги

- Передача даних виконується через захищене з'єднання (HTTPS);
- Автентифікація агента — через унікальний device-токен;
- Автентифікація адміністратора — через JWT;
- Сервер має витримувати одночасну роботу з кількома пристроями;
- Агент не повинен суттєво навантажувати систему користувача (фонова робота).

## Ліцензія / автор

Kost623
