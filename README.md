# GameHub — Redis Homework #1

Бэкенд для платформы игровых турниров «GameHub» на Redis
как единственном хранилище.

Реализовано:

- 6 типов структур Redis: Hash, String, Sorted Set, Set, Stream, Lua.
- Кеширование профилей (Cache-Aside + TTL 60 сек).
- Очередь уведомлений через Redis Streams с группой потребителей.
- Конвейеризация (pipeline) для массовой загрузки профилей.
- Отказоустойчивая инфраструктура: 1 мастер, 2 реплики, 3 Sentinel.
- Защита от split-brain через min-replicas-to-write.

---

## Архитектура

Компоненты:

- master — принимает записи. RDB + AOF.
  maxmemory 256mb, volatile-lru, min-replicas-to-write 1.
- replica-1, replica-2 — реплики мастера.
  replica-read-only yes, те же настройки персистентности.
- sentinel-1, sentinel-2, sentinel-3 — мониторят мастера.
  Кворум 2, down-after-milliseconds 5000.
  Рабочий конфиг каждого Sentinel хранится в собственном volume и сохраняется
  между перезапусками. Исходный конфиг копируется только при первом запуске.
- app — Spring Boot REST API.
  Подключается к Redis через Sentinel (Lettuce).

Связи:

- app к sentinel-1/2/3 — получает адрес мастера
- app к master — все записи, чтение профилей и их кеша, работа со Streams
- app к replica — чтение лидерборда и достижений (REPLICA_PREFERRED;
  при отсутствии доступных реплик чтение выполняется с мастера)
- replica-1/2 к master (репликация)
- sentinel-1/2/3 к master, replica-1/2 (мониторинг)

Все узлы имеют статические IP в подсети 192.168.0.0/24:

- master — 192.168.0.2
- replica-1 — 192.168.0.3
- replica-2 — 192.168.0.4
- sentinel-1 — 192.168.0.5
- sentinel-2 — 192.168.0.6
- sentinel-3 — 192.168.0.7

Статические IP нужны, чтобы Sentinel работал по IP,
а не по DNS-именам. Docker DNS не резолвит имена
остановленных контейнеров, что приводит к tilt mode
и отсутствию failover.

---

## Структура проекта

Корень:

- docker-compose.yml — все сервисы.
- README.md — этот файл.

Конфиги Redis:

- redis/redis.conf — общий для мастера и реплик.

Конфиги Sentinel:

- sentinel/sentinel1.conf
- sentinel/sentinel2.conf
- sentinel/sentinel3.conf

Приложение:

- hw1-redis/Dockerfile
- hw1-redis/pom.xml
- hw1-redis/src/main/java/com/martynov/hw1_redis/Hw1RedisApplication.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/infrastructure/config/RedisConfiguration.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/api/controllers/PlayerController.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/api/controllers/LeaderboardController.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/api/controllers/AchievementController.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/application/service/PlayerService.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/application/service/LeaderboardService.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/application/service/AchievementService.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/application/service/NotificationConsumer.java
- hw1-redis/src/main/java/com/martynov/hw1_redis/application/service/NotificationCleanupJob.java
- hw1-redis/src/main/resources/application.properties

---

## Запуск

Поднять всё:

    docker compose up -d --build

Подождать ~20 секунд, проверить:

    docker compose ps

Все 7 контейнеров должны быть Up:
master, replica-1, replica-2,
sentinel-1, sentinel-2, sentinel-3, app.

Проверка:

    # Репликация
    docker exec -it master redis-cli INFO replication
    # role:master, connected_slaves:2

    # Sentinel знает мастера
    docker exec -it sentinel-1 redis-cli -p 26379 \
      SENTINEL get-master-addr-by-name mymaster
    # 192.168.0.2 6379

    # Приложение
    curl http://localhost:8080/api/players/p1

---

## API

Базовый URL: http://localhost:8080

Профили:

- POST /api/players/{id} — создать/обновить профиль.
- GET /api/players/{id} — получить профиль (с кешем).
- PATCH /api/players/{id}/level — обновить уровень.
- POST /api/players/{id}/login — зафиксировать вход.
- POST /api/players/batch — массовое создание.

Лидерборд:

- POST /api/leaderboard/score — добавить очки.
- GET /api/leaderboard/top — топ игроков.
- GET /api/leaderboard/rank/{playerId} — место игрока.

Достижения:

- POST /api/players/{id}/achievements — добавить.
- GET /api/players/{id}/achievements/{name} — проверить.
- GET /api/players/{id1}/achievements/common/{id2} — общие.

Изменение уровня, фиксация входа, начисление очков и добавление достижения
требуют существующего профиля. Проверка выполняется на мастере; при отсутствии
игрока API возвращает 404 без записи данных.

---

## Примеры запросов

Профиль:

    curl -X POST http://localhost:8080/api/players/p1 \
      -H "Content-Type: application/json" \
      -d '{"name":"Alice","level":5,"region":"EU"}'

    curl http://localhost:8080/api/players/p1

    curl -X PATCH http://localhost:8080/api/players/p1/level \
      -H "Content-Type: application/json" \
      -d '{"delta":3}'

    curl -X POST http://localhost:8080/api/players/p1/login

Лидерборд:

    curl -X POST http://localhost:8080/api/leaderboard/score \
      -H "Content-Type: application/json" \
      -d '{"player_id":"p1","score":100}'

    curl http://localhost:8080/api/leaderboard/top

    curl http://localhost:8080/api/leaderboard/rank/p1

Достижения:

    curl -X POST http://localhost:8080/api/players/p1/achievements \
      -H "Content-Type: application/json" \
      -d '{"achievement_name":"FIRST_WIN"}'

    curl http://localhost:8080/api/players/p1/achievements/FIRST_WIN

    curl http://localhost:8080/api/players/p1/achievements/common/p2

Батч:

    curl -X POST http://localhost:8080/api/players/batch \
      -H "Content-Type: application/json" \
      -d '[
        {"id":"b1","name":"Bob1","level":1,"region":"EU"},
        {"id":"b2","name":"Bob2","level":2,"region":"EU"}
      ]'

---

## Структуры данных Redis

Ключ player:{id} — Hash — TTL бесконечно.
Ключ logins:{id} — String — TTL 24 часа.
Ключ cache:player:{id} — String (JSON) — TTL 60 сек.
Ключ tournament:main — Sorted Set — TTL бесконечно.
Ключ achievements:{id} — Set — TTL бесконечно.
Ключ notifications — Stream. Сообщения хранятся 7 дней с момента добавления.
Джоба удаления запускается раз в минуту и удаляет старые
сообщения командой XTRIM MINID.

---

## Проверка сценариев

Кеширование:

    # Первый запрос — MISS в логах
    curl http://localhost:8080/api/players/p1

    # Второй запрос — HIT в логах
    curl http://localhost:8080/api/players/p1

    # TTL кеша
    docker exec -it master redis-cli TTL cache:player:p1

Streams:

    # Триггерим уведомление
    curl -X PATCH http://localhost:8080/api/players/p1/level \
      -H "Content-Type: application/json" \
      -d '{"delta":1}'

    # Смотрим
    docker exec -it master redis-cli XLEN notifications
    docker exec -it master redis-cli XRANGE notifications - +
    docker exec -it master redis-cli XPENDING notifications notifications-group

Failover:

    # Кто мастер
    docker exec -it sentinel-1 redis-cli -p 26379 \
      SENTINEL get-master-addr-by-name mymaster

    # Останавливаем
    docker stop master
    sleep 15

    # Новый мастер
    docker exec -it sentinel-1 redis-cli -p 26379 \
      SENTINEL get-master-addr-by-name mymaster

    # Возвращаем
    docker start master

Split-brain:

    # Пауза реплик
    docker pause replica-1 replica-2

    # Мастер отклоняет записи
    docker exec -it master redis-cli SET test:key "value"
    # (error) NOREPLICAS Not enough good replicas to write.

    # Возврат
    docker unpause replica-1 replica-2

---

## Лицензия

Учебный проект. ДЗ №1 по курсу СУБД.
