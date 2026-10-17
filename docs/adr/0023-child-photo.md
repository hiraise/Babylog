# ADR-0023: Фото ребёнка — кадрированный JPEG 512×512 в `children.photo`; камера и галерея — системные, без разрешений

- **Статус:** Принято
- **Дата:** 2026-10-17
- **Затрагивает:** `Child.photo` (`core/model/Child.kt`), `children.photo` (`core/data/room/`), `AndroidManifest.xml` (`FileProvider`, permissions), `res/xml/photo_paths.xml`, фото в `feature/children/` (`rememberPhotoPicker`, `PhotoCropSheet`), `Avatar`, `feature/transfer/`

## Контекст

Владелец хочет на аватаре фото ребёнка: снять камерой или выбрать из галереи и выбрать кадр.
У приложения нет разрешений и сети ([ADR-0002](0002-standalone-local-data.md)), данные — в одной
базе ([ADR-0008](0008-data-model-children.md)), всплывающие окна — листы ([ADR-0018](0018-bottom-sheets.md)).

## Решение

- Фото — `Child.photo: ChildPhoto?`: уже кадрированный квадратный JPEG `ChildPhoto.SIZE` (512 px).
  В базе — `children.photo` (BLOB, `NULL` — фото нет), миграция 5→6. Файлов фото вне базы нет.
- Храни только кадр. Снимок или фото из галереи — временный файл в `cache/photos/`; после
  кадрирования удаляй его (`deletePhotoSource`).
- Снимает системное приложение камеры (`ActivityResultContracts.TakePicture`) в файл, открытый через
  `FileProvider` (`${applicationId}.photos`, в `photo_paths.xml` — только `cache/photos/`). Permission
  `CAMERA` не объявляй: с ним, пока его не выдали, системная камера не запускается.
- Галерея — системный пикер (`PickVisualMedia`, `ImageOnly`); разрешения на чтение памяти
  (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`) не объявляй. Выбранное фото копируй в `cache/photos/`:
  ссылка из пикера не переживает перезапуск процесса.
- Кадр выбирают в листе `PhotoCropSheet`, а не на отдельном экране: фото под кругом, палец двигает,
  два пальца увеличивают до `MAX_CROP_ZOOM`, кадр не выходит за края фото (`CropFrame` в `PhotoCrop.kt`).
- Фото меняют в `ChildSheet` (лист «Фото»: «Сделать фото», «Выбрать из галереи», «Убрать фото»),
  сохраняется оно вместе с формой.
- Изображения обрабатывай платформой (`BitmapFactory`, `android.media.ExifInterface`); библиотеки
  загрузки и кадрирования (Coil, Glide, Picasso, uCrop) не подключай.
- Фото не входит в CSV ([ADR-0009](0009-csv-with-children.md)): экспорт его не пишет, импорт не трогает.
- `Avatar(sex, photo)` показывает фото, без фото — иконку по полу; у `photo` нет значения по умолчанию.

## Последствия

- После переноса на другой телефон через CSV фото ставят заново.
- Фото читается вместе со списком детей. Клади в базу только кадр 512 px (~50 КБ): Room читает
  строку целиком, а у `CursorWindow` предел 2 МБ.

## Проверка

- `MigrationTest` (5→6), `RoomDiaryRepositoryTest` (фото), `PhotoCropTest`, `PhotoBitmapsTest` (EXIF,
  размер), скриншот `child_photo.png`.
- `scripts/dev.sh rules`: в манифесте нет разрешений на камеру и память, в Gradle — библиотек фото.

## Альтернативы

- Файл фото в `files/` и путь в базе — уборка «осиротевших» файлов при удалении ребёнка и отмене формы.
- `CAMERA` и свой экран камеры (CameraX) — permission и новая библиотека; системная камера снимает лучше.
- uCrop — отдельная Activity в чужом оформлении и зависимость; кадр жестами — ~50 строк своего кода.
- `androidx.exifinterface`, как советует lint, — зависимость ради одного тега: уязвимости платформенного
  `ExifInterface` исправлены до Android 8 (minSdk 26).
