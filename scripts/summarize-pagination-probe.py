#!/usr/bin/env python3
"""Проверка raw серии и русский отчёт; не превращает эмулятор в аппаратную приёмку."""
import argparse
import json
import math
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('input', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--task', default='T012', choices=['T012', 'T013', 'T035'])
args = parser.parse_args()
raw = json.loads(args.input.read_text())
assert raw['completedScenarios'] == raw['expectedScenarios']
assert raw['fullProfileCovered'] and raw['profileScenarioCount'] == 36
assert raw.get('cancellation') and not raw['cancellation']['obsoletePublished']
assert not raw['cancellation']['stagingFilesRemain']
rows = []
all_runs = []
for series in raw['series']:
    assert series['profileId'] == raw['profileId'] and len(series['runs']) == 5
    for run in series['runs']:
        if args.task == 'T035':
            assert run['pipeline'] == 'ReaderViewModel/ReaderScreen/PageIndexStore'
            assert 0 < run['openingMs'] <= run['exactNumberByMs']
            assert run['layoutCacheCapacity'] == 8 and run['contentWindowCapacity'] == 128
        assert run['sourceSpoolPresentAtStart'] == (series['cache'] != 'cold_source')
        assert run['pageIndexPresentAtStart'] == (series['cache'] == 'warm')
        assert math.isfinite(run['readyFrameMs']) and run['readyFrameMs'] > 0
        assert 0 <= run['exactNumberByMs'] <= run['firstReadableFrameMs'] == run['readyFrameMs']
        assert run['pageNumber'] > 0 and run['residentFragments'] <= 128
        if args.task in ('T013', 'T035'):
            assert run['mediaSourcePasses'] >= 0 and run['sourcePasses'] >= 0
            assert run['totalSourceOperations'] == run['sourcePasses'] + run['mediaSourcePasses']
    times = sorted(run['readyFrameMs'] for run in series['runs'])
    assert (times[0], times[2], times[-1]) == (series['minMs'], series['medianMs'], series['maxMs'])
    values = ', '.join(f'{run["readyFrameMs"]:.3f}' for run in series['runs'])
    rows.append(f'| {series["case"]} | {values} | {times[0]:.3f} | {times[2]:.3f} | {times[-1]:.3f} |')
    all_runs.extend(series['runs'])
peak = max(run['memory']['sampledPeakPssKb'] for run in all_runs)
resident = max(run['memory']['residentPssKb'] for run in all_runs)
maximum = max(run['readyFrameMs'] for run in all_runs)
profile_file = f'performance-profile-{raw["profileId"]}.json'
if not (args.input.parent / profile_file).exists():
    profile_file = 'performance-profile.json'
text = f'''# Ранний срез пагинации — {args.task}

## Стенд и статус

Профиль: `{raw['profileId']}`. [Профиль стенда](evidence/{profile_file}),
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
if args.task == 'T013':
    text = text.replace(
        'IO открытий media\nне включено в этот счётчик; измерение всех IO/источника необходимо учитывать в T013/T035.',
        '`mediaSourcePasses` отдельно считает открытия media-ресурсов источника; '
        '`totalSourceOperations` — их сумму с текстовыми обходами. Это операции parser, '
        'не число всех системных IO-вызовов.')
    text = text.replace(
        'Для T013: исключить лишнюю полную проверку spool и metadata parse при warm reopen,\n'
        'сохранив обнаружение повреждения/пересоздание; уменьшить IO переключения при записи\n'
        'границ страниц. Проверить память длинного блока и Unicode после оптимизации,\n'
        'повторить измерения. Оставшийся холодный бюджет переносится как риск в T035/T061.',
        'В T013 SHA-256 объединён с проверкой записей spool: один файловый проход вместо двух. '
        'Проверка валидной подмены текста сохраняется. Запись страниц выполняется на IO '
        'через очередь ёмкостью 1 (не более трёх одновременно находящихся в pipeline slices); '
        'отмена ожидает завершение writer перед закрытием файлов. Metadata parse при reopen '
        'пока остаётся, как и layout целого крупнейшего блока.\n\n'
        '[Исходная серия T012](pagination-probe-results.md) сохранена для сравнения. '
        'Оставшийся бюджет и стоимость reopen переносятся в T035/T061; критерий не изменён.')
if args.task == 'T035':
    opening = max(run['openingMs'] for run in all_runs)
    passes = sorted(set(run['sourcePasses'] for run in all_runs))
    text = f'''# Production пагинация — T035

## Стенд и статус

Профиль `{raw['profileId']}`: [снимок стенда](evidence/{profile_file}),
[все исходные значения](evidence/{args.input.name}). Pixel_10 AVD, API 37, debug;
физического устройства нет. **NOT_VERIFIED_DEVICE**. 36 сценариев ×5 повторов =180
измерений, EPUB/FB2 ≥20 MB, начало/поздняя глава, cold/warm/cold_source,
новые scale/ориентация/реальный системный шрифт 200%. SHA-256 APK/корпуса и commit
зафиксированы в профиле. Theme LIGHT; полная продуктовая матрица относится к T058/T061.

## Границы измерения

Таймер запускается перед созданием настоящего ReaderViewModel: включает Room lookup,
открытие source/spool, восстановление позиции, production ReaderPaginationController,
PageIndexStore, измерение native Compose и draw настоящего ReaderScreen с точным номером.
Подготовка отдельной тестовой книги/Room и warm layout выполняется вне таймера.
Перед каждым cold нет page index нужного ключа; cold_source также не имеет source spool.
Флаги обоих cache states проверены в каждом raw повторе. Это эквивалент события начала
подготовки layout, допустимый диагностический срез протокола; actual settings input и
аппаратный first-presented кадр остаются T061.

Ready — callback кадра после native draw актуальной страницы и номера в постоянной полосе.
Это диагностическая верхняя граница до следующего Compose frame, не доказательство
физического presentation. PreparingPages не засчитан. Номер получен точным измерением
префикса; оценка номера не используется. Outliers не исключались, критерий по максимуму.

## Готовая страница с точным номером (мс)

| Сценарий | Пять исходных значений | min | median | max |
|---|---|---:|---:|---:|
''' + '\n'.join(rows) + f'''

## Проходы, память и отмена

Максимальная стадия opening: {opening:.3f} ms; sampled peak PSS {peak} KB;
resident PSS после готовности до {resident} KB. Sampling interval 16 ms,
не непрерывный абсолютный пик. Все heap/native values сохранены в raw.
Source text passes: {passes}; отдельно сохранены mediaSourcePasses и их сумма
с текстовыми проходами (операции parser, не все OS IO). Окно до 128 блоков,
LRU 8 layouts/512000 UTF-16 chars, текущая/две соседние страницы; PageIndex на диске.
Самый большой отдельный TextLayoutResult и повторная metadata scan FB2 при warm open
остаются рисками. Отмена прежнего LayoutKey {raw['cancellation']['cancellationMs']:.3f} ms:
obsoletePublished=false, stagingFilesRemain=false.

## Критерий релиза

Максимум серии {maximum:.3f} ms. **SC-004 ≤1000 ms НЕ ПРОЙДЕН; release blocker**.
Пять успешных instrumentation повторов подтверждают корректность измерения, а не порог.
Без физического устройства аппаратный результат остаётся NOT_VERIFIED_DEVICE.
Нельзя считать US2 checkpoint аппаратной приёмкой или объявлять релиз готовым.

T035 устраняет SHA/string allocations на каждом block measure, вычисляет directory/hash
PageIndex один раз на owner и освобождает отдельное vertical окно в PAGINATED.
Долговечные anchors, проверки SHA cache corruption, prefix counts и native line packing
сохранены. [Production baseline](evidence/pagination-production-baseline.json) и
[прежняя оптимизированная prototype серия](pagination-probe-optimized-results.md)
сохранены; различающиеся APK/commit профили не объединяются в одну acceptance серию.
T061: оптимизировать metadata/spool reopen и измерение позднего prefix, повторить полный
профиль на физическом стенде, сохранив ≤1000 ms и все исходные повторы.
'''
args.output.write_text(text)
print(f'Отчёт: {args.output}; 180 проверенных значений; max {maximum:.3f} ms')
