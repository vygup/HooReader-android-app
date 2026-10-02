#!/usr/bin/env python3
"""Проверка raw серии и русский отчёт; не превращает эмулятор в аппаратную приёмку."""
import argparse
import json
import math
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('input', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
raw = json.loads(args.input.read_text())
assert raw['completedScenarios'] == raw['expectedScenarios']
assert raw['fullProfileCovered'] and raw['profileScenarioCount'] == 36
assert raw.get('cancellation') and not raw['cancellation']['obsoletePublished']
rows = []
all_runs = []
for series in raw['series']:
    assert series['profileId'] == raw['profileId'] and len(series['runs']) == 5
    for run in series['runs']:
        assert run['sourceSpoolPresentAtStart'] == (series['cache'] != 'cold_source')
        assert run['pageIndexPresentAtStart'] == (series['cache'] == 'warm')
        assert math.isfinite(run['readyFrameMs']) and run['readyFrameMs'] > 0
        assert 0 <= run['exactNumberByMs'] <= run['firstReadableFrameMs'] == run['readyFrameMs']
        assert run['pageNumber'] > 0 and run['residentFragments'] <= 128
    times = sorted(run['readyFrameMs'] for run in series['runs'])
    assert (times[0], times[2], times[-1]) == (series['minMs'], series['medianMs'], series['maxMs'])
    values = ', '.join(f'{run["readyFrameMs"]:.3f}' for run in series['runs'])
    rows.append(f'| {series["case"]} | {values} | {times[0]:.3f} | {times[2]:.3f} | {times[-1]:.3f} |')
    all_runs.extend(series['runs'])
peak = max(run['memory']['sampledPeakPssKb'] for run in all_runs)
resident = max(run['memory']['residentPssKb'] for run in all_runs)
maximum = max(run['readyFrameMs'] for run in all_runs)
text = f'''# Ранний срез пагинации — T012

## Стенд и статус

Профиль: `{raw['profileId']}`. [Профиль стенда](evidence/performance-profile.json),
[полные raw значения](evidence/{args.input.name}). Pixel_10 AVD, API 37;
физического устройства нет. Статус: **NOT_VERIFIED_DEVICE**.
36 сценариев, по пять повторов, всего 180 измерений. Модель/SoC/RAM/API/экран,
APK SHA-256, корпус/anchors и конфигурации зафиксированы до серии в профиле.
Оба формата ≥20 MB, начало/поздняя глава, cold/warm и cold_source, масштаб 2,0,
новая ориентация и реальный системный шрифт 200%. Тема прототипа LIGHT;
полная продуктовая матрица обеих тем относится к T061.

## Условия и исправление методики

Обычный cold имеет source spool до таймера, но не имеет целевого page index;
cold_source перед каждым повтором удаляет и spool, и страницы. Warm отдельно подготовлен
до серии. В каждом raw запуске проверены флаги наличия обоих кэшей.
Таймер включает открытие/построение source, измерение точного префикса и draw страницы;
точный номер и готовый кадр записаны отдельно. PreparingPages не засчитывается.
Ready отмечается после draw и следующего frame callback, с точным номером в отдельной
измеренной полосе. Это диагностический Compose прототип, не T061 настоящего reader.

Первоначальная серия сохраняется в `evidence/series-invalid-source-state/` с
**INVALID_MIXED_SOURCE_STATE**: первый обычный cold включал создание spool, остальные
повторы уже имели spool. Данные не удалены; для корректной оценки выполнена новая полная
серия. Выбросы новой серии не исключались, оценка использует максимум всех пяти значений.

## Готовая страница с точным номером (мс)

| Сценарий | Пять исходных значений | min | median | max |
|---|---|---:|---:|---:|
''' + '\n'.join(rows) + f'''

## Память, проходы и отмена

Максимальный sampled PSS: {peak} KB; максимальный resident PSS после готовности:
{resident} KB. Peak sampled с интервалом 16 мс; это не доказательство непрерывного
абсолютного пика. Raw также содержит Java heap/native allocation. Размер окна — до
128 исходных блоков; cache TextMeasurer — 8; страницы лежат на диске, resident fragments
отмечены отдельно. Размер одного крупнейшего блока остаётся самостоятельным риском:
текущий измеритель строит целый TextLayoutResult этого блока.

`sourcePasses` считает текстовые обходы: metadata open и orderedBlocks. IO открытий media
не включено в этот счётчик; измерение всех IO/источника необходимо учитывать в T013/T035.
Окно spool не вызывает новых текстовых проходов. Отмена начатого старого LayoutKey:
{raw['cancellation']['cancellationMs']:.3f} мс, устаревшая страница не опубликована,
незавершённых `.part` не осталось.

## Вывод и следующие действия

Максимум диагностической серии: {maximum:.3f} мс. Бюджет готовой страницы ≤1000 мс
на выбранном физическом стенде **не подтверждён**. Эмулятор, warm-only результаты
и усреднение не подтверждают SC-004. Релиз не принят.

Для T013: исключить лишнюю полную проверку spool и metadata parse при warm reopen,
сохранив обнаружение повреждения/пересоздание; уменьшить IO переключения при записи
границ страниц. Проверить память длинного блока и Unicode после оптимизации,
повторить измерения. Оставшийся холодный бюджет переносится как риск в T035/T061.
'''
args.output.write_text(text)
print(f'Отчёт: {args.output}; 180 проверенных значений; max {maximum:.3f} ms')
