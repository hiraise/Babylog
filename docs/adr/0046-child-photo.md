# ADR-0046: Фото ребёнка — кадрированный JPEG 512×512 в `children.photo`; камера и галерея — системные, без разрешений

- **Статус:** Предложено
- **Дата:** 2026-10-22
- **Затрагивает:** `Child.photo` (`core/model/Child.kt`), `children.photo` (`core/data/room/`), `AndroidManifest.xml` (`FileProvider`, permissions), `res/xml/photo_paths.xml`, `core/ui/photo/` (`rememberPhotoPicker`, `loadUpright`), `PhotoCropSheet`, `Avatar`, `feature/transfer/`
- **Заменяет:** [ADR-0023](0023-child-photo.md) — сводный: его правила вместе с заменами из [ADR-0024](0024-event-photos.md) (`FileProvider`) и [ADR-0045](0045-export-archive-with-photos.md) (перенос фото)

## Контекст

Владелец хочет на аватаре фото ребёнка: снять камерой или выбрать из галереи и выбрать кадр.
У приложения нет разрешений и сети ([ADR-0002](0002-standalone-local-data.md)), данные — в одной
базе ([ADR-0008](0008-data-model-children.md)), всплывающие окна — листы ([ADR-0018](0018-bottom-sheets.md)).

## Решение

- Фото — `Child.photo: ChildPhoto?`: уже кадрированный квадратный JPEG `ChildPhoto.SIZE` (512 px).
  В базе — `children.photo` (BLOB, `NULL` — фото нет), миграция 5→6. Файлов фото вне базы нет.
- Храни только кадр. Снимок или фото из галереи — временный файл в `cache/photos/`; после
  кадрирования удаляй его (`deletePhotoFile`).
- Снимает системное приложение камеры (`ActivityResultContracts.TakePicture`) в файл, открытый через
  `FileProvider` (`${applicationId}.photos`). Permission `CAMERA` не объявляй: с ним, пока его не выдали,
  системная камера не запускается. Что открывает провайдер — [ADR-0024](0024-event-photos.md).
- Галерея — системный пикер (`PickVisualMedia`, `ImageOnly`); разрешения на чтение памяти
  (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`) не объявляй. Выбранное фото копируй в `cache/photos/`:
  ссылка из пикера не переживает перезапуск процесса.
- Кадр выбирают в листе `PhotoCropSheet`, а не на отдельном экране: фото под кругом, палец двигает,
  два пальца увеличивают до `MAX_CROP_ZOOM`, кадр не выходит за края фото (`CropFrame` в `PhotoCrop.kt`).
- Фото меняют в `ChildSheet` (лист «Фото»: «Сделать фото», «Выбрать из галереи», «Убрать фото»),
  сохраняется оно вместе с формой.
- Изображения обрабатывай платформой (`BitmapFactory`, `android.media.ExifInterface`); библиотеки
  загрузки и кадрирования (Coil, Glide, Picasso, uCrop) не подключай.
- Фото переносится архивом экспорта ([ADR-0045](0045-export-archive-with-photos.md)): файл в колонке `photos`
  строки `PROFILE`; импорт ставит его, только если у ребёнка своего фото нет.
- `Avatar(sex, photo)` показывает фото, без фото — иконку по полу; у `photo` нет значения по умолчанию.

## Последствия

- CSV без архива фото не переносит: после такого переноса фото ставят заново или импортируют архив.
- Фото читается вместе со списком детей. Клади в базу только кадр 512 px (~50 КБ): Room читает
  строку целиком, а у `CursorWindow` предел 2 МБ.

## Проверка

- `MigrationTest` (5→6), `RoomDiaryRepositoryTest` (фото), `PhotoCropTest`, `PhotoBitmapsTest` (EXIF,
  размер), скриншот `child_photo.png`, `DiaryArchiveTest` (перенос).
- `scripts/dev.sh rules`: в манифесте нет разрешений на камеру и память, в Gradle — библиотек фото.

## Альтернативы

- Файл фото в `files/` и путь в базе — уборка «осиротевших» файлов при удалении ребёнка и отмене формы.
- `CAMERA` и свой экран камеры (CameraX) — permission и новая библиотека; системная камера снимает лучше.
- uCrop — отдельная Activity в чужом оформлении и зависимость; кадр жестами — ~50 строк своего кода.
- `androidx.exifinterface`, как советует lint, — зависимость ради одного тега: уязвимости платформенного
  `ExifInterface` исправлены до Android 8 (minSdk 26).
