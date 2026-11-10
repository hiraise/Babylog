# S5b — Photos: pick, crop, share

## Port and implement

- `core/ui/photo/*` → `PhotosPicker` + camera via `UIImagePickerController`, no permissions beyond the
  camera usage string; JPEG limits as Android (`EventPhoto.MAX_SIDE`, 512×512 child photo).
- `PhotoCrop*`, `PhotoButton`, `FormPhotos`, `PhotoStrip`, `PhotosField`; share via `ShareLink`.
- Port the photo processing, cache, crop, and sharing tests.

## Additional ADRs

- [0024](../../docs/adr/0024-event-photos.md), [0046](../../docs/adr/0046-child-photo.md).
