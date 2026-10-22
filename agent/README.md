# Management Control Agent

Клієнтська частина — агент, який працює на ноутбуку менеджера та передає дані на сервер.

## Мова та інструменти

- C++20, CMake ≥ 3.20;
- Windows: MSVC (Visual Studio 2022), Win32 API + GDI+;
- HTTPS: libcurl (Linux) / libcurl для Windows (vcpkg);
- Залежностей від сторонніх JSON-бібліотек немає — власний мінімальний парсер.

## Структура

```
agent/
├── CMakeLists.txt
├── include/mc/
│   ├── agent.hpp        # основний цикл агента
│   ├── activity.hpp     # трекер активності (програми, браузер, процеси)
│   ├── config.hpp       # конфігурація та збереження реєстрації
│   ├── http.hpp         # HTTP-клієнт (libcurl)
│   ├── json.hpp         # мінімальний JSON
│   ├── platform.hpp     # інтерфейс до ОС
│   └── screenshot.hpp   # планувальник автоскриншотів
├── src/
│   ├── platform_win.cpp # реалізація для Windows
│   ├── platform_stub.cpp# заглушка для Linux/macOS (для CI)
│   └── ...
└── tests/test_main.cpp
```

## Збірка

### Linux (переносима частина + тести)

```bash
cd agent
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
ctest --test-dir build --output-on-failure
```

На Linux збирається переносиме ядро (JSON, HTTP, конфіг, трекер, планувальник) із
заглушкою платформи — це дозволяє проганяти тести в CI.

### Windows

```bat
cd agent
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

У Windows-збірці використовується `platform_win.cpp`:
активне вікно (`GetForegroundWindow`), простоювання (`GetLastInputInfo`),
перелік процесів (`CreateToolhelp32Snapshot`), блокування (`LockWorkStation`),
скриншоти (GDI+ → PNG у пам'яті), захист токена (DPAPI) та плановий службовий
запуск через `schtasks` (захист від видалення).

## Конфігурація

Файл `mc-agent.conf` (поряд із виконуваним файлом або вказаний у `--config`):

```ini
server_url = https://mc.example.com
enrollment_code = 9U8N-746M-B5UC   # одноразовий, потрібен лише при першому запуску
state_path = C:\ProgramData\ManagementControl\agent.state
sample_interval_seconds = 5
idle_threshold_seconds = 120
heartbeat_seconds = 60
upload_interval_seconds = 30
location_interval_seconds = 300
min_screenshots_per_day = 10
max_screenshots_per_day = 35
verify_tls = true
tamper_protection = true
verbose = false
```

## Запуск

```bat
mc-agent.exe --config mc-agent.conf --enroll 9U8N-746M-B5UC --server https://mc.example.com
```

Після першої реєстрації агент зберігає `deviceId` і device-токен у файлі стану
(на Windows токен шифрується DPAPI) і надалі піднімається без `--enroll`.

## Що саме збирається

| Дані | Як |
|------|-----|
| Час використання програм | опитування активного вікна кожні `sample_interval_seconds` |
| Сайти | активний браузер + заголовок вкладки; URL витягується з заголовка, якщо там є посилання |
| Запущені програми | порівняння знімків переліку процесів |
| Скриншоти | випадкові моменти 10–35 на добу у робочий час (08:00–20:00) |
| Локація | геолокація за IP (точність до кількох кілометрів) |

Дані не зберігаються на пристрої: агент надсилає їх на сервер одразу після збору.

## Обмеження поточної версії

- Повний URL активної вкладки не читається: заголовок вікна Chrome/Edge містить
  назву сторінки, а не адресу. Реалізовано добування URL з заголовка; повноцінне
  читання адресного рядка потребує UI Automation — це наступний крок.
- Геолокація — за IP, а не за GPS/Wi-Fi.
- `UNLOCK` та `REBOOT` не реалізовані: розблокування потребує локального
  підтвердження, перезавантаження — окремий механізм.
