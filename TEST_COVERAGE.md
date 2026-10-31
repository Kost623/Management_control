# Звіт про покриття тестами (Test Coverage)

Дата: 2026-10-31
Інструмент: JaCoCo 0.8.12 (Maven plugin)
Команда генерації: `./mvnw -pl server clean test` (звіт створюється автоматично
у фазі `test`, результат — `server/target/site/jacoco/index.html`)

Усього тестів: **29**, усі пройшли успішно (0 falls, 0 errors).
Типи тестів: unit (`ReportServiceTest`, `TokenServiceTest`), exception testing
(`ExceptionHandlingTest`), mocking (`MockingInteractionTest`), E2E-сценарії
(`Unit_Test`), інтеграційні API+DB тести (`IntegrationApiDbTest`,
`AgentFlowIntegrationTest`).

## Загальний результат

| Метрика | Покрито | Усього | Відсоток |
|---|---|---|---|
| Інструкції (Instructions) | 3 647 | 5 343 | **68%** |
| Гілки (Branches) | 67 | 162 | **41%** |
| Рядки (Lines) | 894 | 1 252 | 71% |
| Методи (Methods) | 324 | 471 | 69% |
| Класи (Classes) | 71 | 77 | 92% |

## Покриття за пакетами

| Пакет | Покриття інструкцій | Покриття гілок | Класів |
|---|---|---|---|
| `config` | 97% | 50% | 7 |
| `web.agent.dto` | 90% | n/a | 13 |
| `security` | 83% | 66% | 6 |
| `web.agent` | 69% | 33% | 3 |
| `service` | 68% | 39% | 7 |
| `web.admin.dto` | 68% | n/a | 13 |
| `domain` | 66% | n/a | 14 |
| `web.auth` | 65% | 0% | 1 |
| `common` | 64% | n/a | 5 |
| `managementcontrol` (root) | 37% | n/a | 1 |
| **`web.admin`** | **32%** | **7%** | **7** |

## Компоненти з низьким рівнем покриття

1. **`com.managementcontrol.web.admin` (32%, гілки 7%)** — критична зона.
   8 адмінських контролерів (`DeviceAdminController`, `CommandAdminController`,
   `DeviceActivityController`, `ReportController`, `ScreenshotAdminController`,
   `EnrollmentController`, `AuditController`) покриті лише частково:
   перевірені окремі "щасливі шляхи" (успішний лістинг, блокування пристрою),
   але майже не перевірені негативні сценарії (403 для VIEWER, порожні
   результати, граничні значення пагінації, помилки валідації на кожному
   ендпоінті окремо).

2. **`com.managementcontrol.web.auth` (65%, гілки 0%)** — `AuthController`
   має лише 2 ендпоінти (`/login`, `/me`), перевірені основні сценарії
   (успішний логін, невірний пароль), але гілка з `GET /me` для
   неавторизованого запиту й для видаленого користувача не покрита.

3. **`com.managementcontrol` (root, 37%)** — `ManagementControlServerApplication`
   (точка входу `main()`), низьке покриття тут очікуване й не є проблемою.

## Що потребує додаткових тестів

- Негативні сценарії для решти адмінських ендпоінтів: доступ без токена,
  доступ з роллю VIEWER до дій, що вимагають ADMIN (`CommandAdminController`,
  `EnrollmentController`), запити неіснуючих сутностей (`ScreenshotAdminController`,
  `DeviceActivityController`).
- Граничні випадки пагінації: порожній результат, `page` за межами наявних даних,
  `size` на межі дозволеного діапазону (1 та 200/500).
- `ReportController.topApps`/`topSites` з порожнім періодом (пристрій без
  жодної телеметрії).

## Застереження

Високий відсоток покриття сам по собі не гарантує якості тестів: усі 29
тестів перевіряють не лише факт виклику коду, а й конкретну бізнес-поведінку
(коректність статусів, вміст відповіді, зміни в БД, взаємодію між компонентами
через `verify`/`ArgumentCaptor`). Пріоритетом для подальшої роботи є пакет
`web.admin`, оскільки саме там зосереджена основна логіка керування системою
з боку адміністратора, а покриття там найнижче.
