CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS tags (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    normalized_name TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_items (
    id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL,
    source_item_id TEXT,
    canonical_url TEXT NOT NULL,
    original_url TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT,
    author TEXT,
    cover_url TEXT,
    image_urls_json TEXT NOT NULL DEFAULT '[]',
    capture_level TEXT NOT NULL,
    summary TEXT,
    user_note TEXT,
    category_id TEXT,
    ai_status TEXT NOT NULL DEFAULT 'NOT_REQUESTED',
    ai_confidence REAL,
    ai_last_error TEXT,
    lifecycle_status TEXT NOT NULL DEFAULT 'ACTIVE',
    manual_metadata_locked INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    source_updated_at TEXT NOT NULL,
    user_edited_at TEXT,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_item_source_id
    ON knowledge_items(source_type, source_item_id)
    WHERE source_item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_item_canonical_url
    ON knowledge_items(source_type, canonical_url);

CREATE INDEX IF NOT EXISTS idx_knowledge_item_status_updated
    ON knowledge_items(lifecycle_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_item_tags (
    item_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    PRIMARY KEY (item_id, tag_id),
    FOREIGN KEY (item_id) REFERENCES knowledge_items(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS import_batches (
    id TEXT PRIMARY KEY,
    client_batch_id TEXT NOT NULL UNIQUE,
    capture_mode TEXT NOT NULL,
    extractor_version TEXT NOT NULL,
    received INTEGER NOT NULL,
    created_count INTEGER NOT NULL,
    updated_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS item_ai_suggestions (
    item_id TEXT NOT NULL,
    suggestion_type TEXT NOT NULL,
    value TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (item_id, suggestion_type, value),
    FOREIGN KEY (item_id) REFERENCES knowledge_items(id) ON DELETE CASCADE
);
