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
      "sourceTags": ["AI", "效率工具"],
      "sourceRelation": "FAVORITE",
      "captureLevel": "DETAIL",
      "capturedAt": "2026-07-25T12:00:00+08:00"
    }
  ]
}
```

The server limits each batch to 50 items. Reusing `clientBatchId` replays the stored summary instead of importing again. Items are deduplicated by source ID and then canonical URL. Image URLs are not part of the import contract and are not displayed, proxied, or uploaded by the extension.

When AI is enabled and Qwen is configured, successfully created or updated items are saved first, then organized in a background task. AI failure never rolls back the import.

`sourceRelation` is optional and records whether a note came from favorites, likes, or both without duplicating the knowledge item.

`contentStatus` is optional on import and can be `DISCOVERED`, `COMPLETED`, or `FAILED`. List-card imports default to `DISCOVERED`; detail-page imports with text become `COMPLETED`; failures can be recorded with `contentLastError`.

`sourceTags` is optional and stores original Xiaohongshu hashtags separately from the user/AI tag library. AI organization must not overwrite these source tags.

## Manual sync runs

- `POST /sync-runs`: create a user-triggered sync task. Body: `{ "requestedSources": ["FAVORITE", "LIKED"] }`. Requires `X-Extension-Token`.
- `PATCH /sync-runs/{id}`: update task counters and final status. Requires `X-Extension-Token`.
- `GET /sync-runs/latest`: latest task result for the extension popup and website settings dialog.

Statuses: `RUNNING`, `COMPLETED`, `PARTIAL_FAILED`, `FAILED`.

## Items

- `GET /items`: paginated search. Supports `q`, `categoryId`, `tagId`, `sourceType`, `captureLevel`, `contentStatus`, `aiStatus`, `page`, `pageSize`, and `sort`. Filtering by a root category includes its direct child categories.
- `GET /items/{id}`: full saved item.
- `PATCH /items/{id}`: partial edit of `categoryId`, `tagIds`, `summary`, and `userNote`. JSON `null` clears a field.
- `POST /items/{id}/organize`: user-triggered Qwen organization for one item. Returns the updated item. It requires Qwen to be configured but does not expose provider errors or secrets.
- `DELETE /items/{id}`: physically delete the item and its tag/AI suggestion links. Categories and public tags are kept.
- `POST /items/clear`: physically clear the knowledge library. Body: `{ "confirmation": "清空知识库" }`. Deletes `knowledge_items`, `knowledge_item_tags`, `item_source_relations`, and `item_ai_suggestions`; keeps categories, tags, app settings, sync runs, and import batches. Returns `{ "deletedItems": 0 }`.

## Settings

- `GET /settings`: returns `{ "aiEnabled": true, "aiConfigured": true, "model": "qwen-plus", "pendingAiCount": 0, "failedAiCount": 0 }`.
- `PATCH /settings/ai`: updates the local AI switch. Body: `{ "aiEnabled": true }`.
- `POST /settings/ai/test`: tests the configured Qwen endpoint and returns `{ "success": true, "configured": true, "model": "qwen-plus", "message": "Qwen connection succeeded" }`. The response never includes the API key or raw provider payload.

`aiConfigured` only means `QWEN_API_KEY` is present on the backend. The API key is never returned by the API, stored in SQLite, or sent to the browser.

## AI organization

Qwen is configured with backend environment variables: `QWEN_API_KEY`, `QWEN_BASE_URL`, and `QWEN_MODEL`.

- `POST /ai/organize-pending`: user-triggered batch organization for up to 50 active items whose content is `COMPLETED`, metadata is not manually locked, and AI status is `PENDING`, `PROCESSING`, or `FAILED`. Returns `{ "processed": 0, "succeeded": 0, "failed": 0, "skipped": 0, "message": null }`.

AI status values are `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`. Startup migration maps older `NOT_REQUESTED` rows to `PENDING` and older `SUCCESS` rows to `COMPLETED`.

Automatic AI organization only happens after a user-triggered import/manual sync has committed content. There are no timer-based scheduled AI jobs.

The AI result is accepted only when it fits the local knowledge base:

- `categoryId` must be an existing category ID, otherwise it is ignored.
- `tagIds` must be existing tag IDs, otherwise they are ignored.
- `suggestedTags` are saved as suggestions in `item_ai_suggestions`; they do not create tags automatically.
- `summary` is capped before saving.
- `confidence` is clamped to `0..1`.

If the user edits summary, category, or tags, `manualMetadataLocked` prevents later AI writes from overwriting that item.

## Categories and tags

- `GET /categories`, `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}`.
- `GET /categories/source-tags`: top original Xiaohongshu hashtag frequencies.
- `POST /categories/suggestions`: ask Qwen for 7-10 root category suggestions from existing content and source tags. Suggestions are returned for review and are not saved automatically.
- `POST /categories/suggestions/confirm`: create user-confirmed root categories from edited suggestions.
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
