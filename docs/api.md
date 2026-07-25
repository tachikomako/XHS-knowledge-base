# MVP API

Base path: `/api/v1`. JSON uses UTF-8. Import requests require `X-Extension-Token`; configure the same value in `XHS_EXTENSION_TOKEN` and the extension popup.

## Health

`GET /health` returns API compatibility, application version, and whether AI is configured. It never returns secrets.

## Xiaohongshu import

`POST /imports/xiaohongshu`

```json
{
  "clientBatchId": "extension-generated-uuid",
  "captureMode": "CURRENT_POST",
  "extractorVersion": "xhs-1",
  "items": [
    {
      "sourceItemId": "optional-stable-id",
      "url": "https://www.xiaohongshu.com/explore/example",
      "title": "Example title",
      "author": "Example author",
      "text": "Visible post text",
      "coverUrl": "https://example.invalid/cover.jpg",
      "imageUrls": [],
      "captureLevel": "DETAIL",
      "capturedAt": "2026-07-25T12:00:00+08:00"
    }
  ]
}
```

The server limits each batch to 50 items. Reusing `clientBatchId` replays the stored summary instead of importing again. Items are deduplicated by source ID and then canonical URL.

## Items

- `GET /items`: paginated search. Supports `q`, `categoryId`, `sourceType`, `captureLevel`, `lifecycleStatus`, `aiStatus`, `page`, `pageSize`, and `sort`.
- `GET /items/{id}`: full saved item.
- `PATCH /items/{id}`: partial edit of `categoryId`, `tagIds`, `summary`, and `userNote`. JSON `null` clears a field.
- `POST /items/{id}/archive`: move to archive.
- `POST /items/{id}/trash`: hide locally while retaining the source tombstone.
- `POST /items/{id}/restore`: return to active items.

Errors use this shape:

```json
{
  "code": "ITEM_NOT_FOUND",
  "message": "Knowledge item not found",
  "requestId": "uuid",
  "timestamp": "2026-07-25T12:00:00Z"
}
```
