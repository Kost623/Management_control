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
Management_control/
├── pom.xml                  # базовий Maven-проєкт (parent, Java 21)
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
└── agent/                   # КЛІЄНТСЬКА частина (C++ / Windows) — у розробці
```
## Технології

- Мова програмування (сервер): Java 21, Spring Boot 3.5;
- Мова програмування (агент): C++ (Windows);
- База даних: MariaDB (міграції Flyway);
- Автентифікація: device-токен для агента (`mc_dev_…`), JWT для адміністратора.

## Залежності

Сервер: `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`,
`flyway-core` + `flyway-mysql`, `mariadb-java-client`, `jjwt`, `springdoc-openapi`.

## Запуск проєкту

### 1. Підняти базу даних

```bash
docker compose up -d db
```

або підключитися до наявного MariaDB і створити базу `management_control`.

### 2. Запустити сервер

```bash
cd server
mvn spring-boot:run
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

### 3. Swagger UI

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

## Ліцензія / автор

Kost623
