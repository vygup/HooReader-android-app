# Матрица SC-008 — T058

Pixel_10 AVD, API 37. 96 сочетаний, 48 tests, 576 PNG; все итоговые прогоны прошли.

| Системный шрифт | Масштаб чтения | Результат |
|---|---|---|
| 1.0 | 0.75 | [PASS, 16 сочетаний](system1.0-reading0.75.txt) |
| 1.0 | 2.0 | [PASS, 16 сочетаний](system1.0-reading2.0.txt) |
| 1.5 | 0.75 | [PASS, 16 сочетаний](system1.5-reading0.75.txt) |
| 1.5 | 2.0 | [PASS, 16 сочетаний](system1.5-reading2.0.txt) |
| 2.0 | 0.75 | [PASS, 16 сочетаний](system2.0-reading0.75.txt) |
| 2.0 | 2.0 | [PASS, 16 сочетаний](system2.0-reading2.0.txt) |

Имена PNG: `<формат>-<режим>-<тема>-system<scale>-reading<scale>-<orientation>-<экран>.png`.
Android orientation constants: 1 — портрет, 0 — альбом. Экраны: reading, controls, contents, settings, exit, app-settings.
Подготовительные ошибки harness — в attempts/; они не входят в финальные PASS.
Системный масштаб восстановлен; TalkBack вне scope.
