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

When AI is enabled and Qwen is configured, successfully created, updated, or restored items are saved first, then organized in a background task. AI failure never rolls back the import.

## Items

- `GET /items`: paginated search. Supports `q`, `categoryId`, `tagId`, `sourceType`, `captureLevel`, `lifecycleStatus`, `aiStatus`, `page`, `pageSize`, and `sort`. Filtering by a root category includes its direct child categories.
- `GET /items/{id}`: full saved item.
- `PATCH /items/{id}`: partial edit of `categoryId`, `tagIds`, `summary`, and `userNote`. JSON `null` clears a field.
- `POST /items/{id}/archive`: move to archive.
- `POST /items/{id}/trash`: hide locally while retaining the source tombstone.
- `POST /items/{id}/restore`: return to active items.
- `POST /items/bulk-trash`: move all non-trashed items, or one category subtree, to trash. Body: `{ "scope": "ALL" }` or `{ "scope": "CATEGORY", "categoryId": "..." }`.

## Settings

- `GET /settings`: returns `{ "aiEnabled": true, "aiConfigured": true, "model": "qwen-plus" }`.
- `PATCH /settings/ai`: updates the local AI switch. Body: `{ "aiEnabled": true }`.

`aiConfigured` only means `QWEN_API_KEY` is present on the backend. The API key is never returned by the API, stored in SQLite, or sent to the browser.

## AI organization

Qwen is configured with backend environment variables: `QWEN_API_KEY`, `QWEN_BASE_URL`, and `QWEN_MODEL`.

The AI result is accepted only when it fits the local knowledge base:

- `categoryId` must be an existing category ID, otherwise it is ignored.
- `tagIds` must be existing tag IDs, otherwise they are ignored.
- `suggestedTags` are saved as suggestions in `item_ai_suggestions`; they do not create tags automatically.
- `summary` is capped before saving.
- `confidence` is clamped to `0..1`.

If the user edits summary, category, or tags, `manualMetadataLocked` prevents later AI writes from overwriting that item.

## Categories and tags

- `GET /categories`, `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}`.
- `GET /tags`, `POST /tags`, `PUT /tags/{id}`, `POST /tags/{sourceTagId}/merge`, `DELETE /tags/{id}`.

Category requests use `{ "name": "技术", "parentId": null, "sortOrder": 0 }`. Only two category levels are supported. A category must have no children or assigned items before deletion.

Tag requests use `{ "name": "AI" }`. A leading `#` is removed and names are deduplicated case-insensitively. Merging a tag uses `{ "targetTagId": "..." }`, copies associations to the target, ignores duplicates, and deletes the source tag. Deleting a tag removes its item associations but never deletes knowledge items.

Errors use this shape:

```json
{
  "code": "ITEM_NOT_FOUND",
  "message": "Knowledge item not found",
  "requestId": "uuid",
  "timestamp": "2026-07-25T12:00:00Z"
}
```
