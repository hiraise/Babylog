# ADR-0024: Фото записей — таблица `event_photos` (JPEG до 1600 px); «Поделиться» — системным меню, только по нажатию

- **Статус:** Принято
- **Дата:** 2026-10-17
- **Затрагивает:** `core/model/EventPhoto.kt`, `event_photos` и `DiaryDao` (`core/data/room/`), `DiaryRepository` (`setEventPhotos`), `res/xml/photo_paths.xml`, `core/ui/photo/` (`prepareEventPhoto`, `shareWithPhotos`), формы записей в `feature/editor/` (`FormPhoto`), `feature/transfer/`
- **Заменяет:** пункт [ADR-0023](0023-child-photo.md) о том, что открывает `FileProvider`

## Контекст

Владелец хочет прикладывать к подгузнику фото — одно или несколько — и отправлять запись с фото
врачу в Telegram или другой мессенджер. Данные — только на телефоне ([ADR-0002](0002-standalone-local-data.md)),
камера и галерея — системные, без разрешений ([ADR-0023](0023-child-photo.md)).

## Решение

- Фото записи — `EventPhoto`: JPEG не больше `EventPhoto.MAX_SIDE` (1600 px) по длинной стороне,
  до `EventPhoto.MAX_PER_EVENT` (6) на запись. Оригинал не храни.
- В базе — таблица `event_photos` (`eventId` → `events` с `ON DELETE CASCADE`, `position`, `jpeg`),
  миграция 6→7. Фото не входят в `BabyEvent`: список записей грузится без них, фото одной записи —
  `observeEventPhotos`, отметка в истории — `observePhotoCounts` (без байтов).
- Записи меняй только `UPDATE` (`@Update`), не `REPLACE`: замена строки удалила бы фото каскадом.
- Фото пишет только `setEventPhotos` — вместе с записью, в одной транзакции (`DiaryViewModel.saveEvent`).
- До «Сохранить» новое фото — файл в `cache/photos/`; в сохранённом состоянии формы — только id и
  пути (`FormPhotosSaver`), не байты: Bundle больше 1 МБ роняет приложение, когда оно уходит в фон.
  «Отмена» удаляет эти файлы; не удалённое — `sweepPhotoCache` при запуске, старше суток.
- Строка базы с фото должна уместиться в окно курсора (2 МБ): `prepareEventPhoto` сжимает
  сильнее, если JPEG вышел больше 1,2 МБ.
- «Поделиться» — системное меню (`Intent.createChooser`, `ACTION_SEND`/`ACTION_SEND_MULTIPLE`):
  подпись и фото уходят только по нажатию и только в приложение, которое выбрал пользователь.
  Фото копируются в `cache/share/` и отдаются через `FileProvider` только на чтение. Своей сети
  у приложения нет.
- `FileProvider` открывает только `cache/photos/` (камере) и `cache/share/` (для «Поделиться»).
- Фото в CSV не входят ([ADR-0009](0009-csv-with-children.md)): экспорт их не пишет, импорт не трогает.
- Сейчас фото — только у подгузника (`DiaperSheet`). Другой тип записи с фото: `PhotoStrip` и
  `PhotoEdit` в его форме — таблица и репозиторий общие.
  Фото есть и у заметки — общим блоком формы `PhotosField`, `PhotoSheets` ([ADR-0031](0031-notes.md)).

## Последствия

- После переноса на другой телефон через CSV фото записей не переносятся.
- Подпись к фото — `diaperShareText`; несколько записей одним сообщением — следующий шаг.

## Проверка

- `MigrationTest` (6→7), `RoomDiaryRepositoryTest` (порядок, правка записи, удаление записи и
  ребёнка), `DiaryViewModelTest`, `FormPhotosTest`, `PhotoBitmapsTest`, скриншот `diaper_photos.png`.
- `scripts/dev.sh rules`: в `DiaryDao.kt` нет `OnConflictStrategy.REPLACE`.

## Альтернативы

- Файлы фото в `files/` и пути в базе — удаление файлов вне транзакции и уборка «осиротевших»;
  ради 0,3–0,6 МБ на фото не стоит.
- Фото как есть с камеры (3–8 МБ) — база растёт быстро, строка не помещается в окно курсора,
  а мессенджеры всё равно сжимают до похожего размера.
