package io.github.tachikomako.xhsknowledge.metadata;

import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MetadataService {

    private final JdbcTemplate jdbcTemplate;

    public MetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CategoryView> listCategories() {
        return jdbcTemplate.query("""
                SELECT c.id, c.name, c.parent_id, c.sort_order,
                       COUNT(CASE WHEN i.lifecycle_status = 'ACTIVE' THEN 1 END) AS item_count
                FROM categories c
                LEFT JOIN knowledge_items i ON i.category_id = c.id
                GROUP BY c.id, c.name, c.parent_id, c.sort_order
                ORDER BY c.sort_order, lower(c.name)
                """, (result, row) -> new CategoryView(
                result.getString("id"),
                result.getString("name"),
                result.getString("parent_id"),
                result.getInt("sort_order"),
                result.getLong("item_count")
        ));
    }

    public List<TagView> listTags() {
        return jdbcTemplate.query("""
                SELECT t.id, t.name,
                       COUNT(CASE WHEN i.lifecycle_status = 'ACTIVE' THEN 1 END) AS item_count
                FROM tags t
                LEFT JOIN knowledge_item_tags it ON it.tag_id = t.id
                LEFT JOIN knowledge_items i ON i.id = it.item_id
                GROUP BY t.id, t.name
                ORDER BY item_count DESC, lower(t.name)
                """, (result, row) -> new TagView(
                result.getString("id"),
                result.getString("name"),
                result.getLong("item_count")
        ));
    }

    @Transactional
    public CategoryView createCategory(CategoryRequest request) {
        String name = cleanName(request.name(), false);
        String parentId = trimToNull(request.parentId());
        validateParent(parentId, null);
        ensureUniqueCategory(name, parentId, null);
        String id = UUID.randomUUID().toString();
        String timestamp = now();
        jdbcTemplate.update(
                "INSERT INTO categories(id, name, parent_id, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, name, parentId, request.sortOrder() == null ? 0 : request.sortOrder(), timestamp, timestamp
        );
        return findCategory(id);
    }

    @Transactional
    public CategoryView updateCategory(String id, CategoryRequest request) {
        requireCategory(id);
        String name = cleanName(request.name(), false);
        String parentId = trimToNull(request.parentId());
        validateParent(parentId, id);
        ensureUniqueCategory(name, parentId, id);
        jdbcTemplate.update(
                "UPDATE categories SET name = ?, parent_id = ?, sort_order = ?, updated_at = ? WHERE id = ?",
                name, parentId, request.sortOrder() == null ? 0 : request.sortOrder(), now(), id
        );
        return findCategory(id);
    }

    @Transactional
    public void deleteCategory(String id) {
        requireCategory(id);
        Integer childCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE parent_id = ?", Integer.class, id
        );
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_items WHERE category_id = ?", Integer.class, id
        );
        if ((childCount != null && childCount > 0) || (itemCount != null && itemCount > 0)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CATEGORY_IN_USE",
                    "Category must have no child categories or knowledge items before deletion"
            );
        }
        jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
    }

    @Transactional
    public TagView createTag(TagRequest request) {
        String name = cleanName(request.name(), true);
        String normalized = normalizeTag(name);
        ensureUniqueTag(normalized, null);
        String id = UUID.randomUUID().toString();
        String timestamp = now();
        jdbcTemplate.update(
                "INSERT INTO tags(id, name, normalized_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, name, normalized, timestamp, timestamp
        );
        return findTag(id);
    }

    @Transactional
    public TagView updateTag(String id, TagRequest request) {
        requireTag(id);
        String name = cleanName(request.name(), true);
        String normalized = normalizeTag(name);
        ensureUniqueTag(normalized, id);
        jdbcTemplate.update(
                "UPDATE tags SET name = ?, normalized_name = ?, updated_at = ? WHERE id = ?",
                name, normalized, now(), id
        );
        return findTag(id);
    }

    @Transactional
    public void deleteTag(String id) {
        requireTag(id);
        jdbcTemplate.update("DELETE FROM knowledge_item_tags WHERE tag_id = ?", id);
        jdbcTemplate.update("DELETE FROM tags WHERE id = ?", id);
    }

    @Transactional
    public TagView mergeTag(String sourceTagId, String targetTagId) {
        if (!StringUtils.hasText(targetTagId) || sourceTagId.equals(targetTagId)) {
            throw badRequest("INVALID_TAG_MERGE", "targetTagId must be a different existing tag");
        }
        requireTag(sourceTagId);
        requireTag(targetTagId);
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO knowledge_item_tags(item_id, tag_id)
                SELECT item_id, ? FROM knowledge_item_tags WHERE tag_id = ?
                """, targetTagId, sourceTagId);
        jdbcTemplate.update("DELETE FROM knowledge_item_tags WHERE tag_id = ?", sourceTagId);
        jdbcTemplate.update("DELETE FROM tags WHERE id = ?", sourceTagId);
        return findTag(targetTagId);
    }

    private CategoryView findCategory(String id) {
        return listCategories().stream()
                .filter(category -> category.id().equals(id))
                .findFirst()
                .orElseThrow(() -> notFound("CATEGORY_NOT_FOUND", "Category not found"));
    }

    private TagView findTag(String id) {
        return listTags().stream()
                .filter(tag -> tag.id().equals(id))
                .findFirst()
                .orElseThrow(() -> notFound("TAG_NOT_FOUND", "Tag not found"));
    }

    private void requireCategory(String id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) throw notFound("CATEGORY_NOT_FOUND", "Category not found");
    }

    private void requireTag(String id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) throw notFound("TAG_NOT_FOUND", "Tag not found");
    }

    private void validateParent(String parentId, String categoryId) {
        if (parentId == null) return;
        if (parentId.equals(categoryId)) {
            throw badRequest("INVALID_CATEGORY_PARENT", "Category cannot be its own parent");
        }
        List<String> parents = jdbcTemplate.queryForList(
                "SELECT parent_id FROM categories WHERE id = ?", String.class, parentId
        );
        if (parents.isEmpty()) throw badRequest("UNKNOWN_CATEGORY_PARENT", "Parent category does not exist");
        if (parents.getFirst() != null) {
            throw badRequest("CATEGORY_DEPTH_EXCEEDED", "Only two category levels are supported");
        }
        if (categoryId != null) {
            Integer children = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM categories WHERE parent_id = ?", Integer.class, categoryId
            );
            if (children != null && children > 0) {
                throw badRequest("CATEGORY_HAS_CHILDREN", "A category with children must remain at the root level");
            }
        }
    }

    private void ensureUniqueCategory(String name, String parentId, String excludedId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM categories
                WHERE lower(name) = lower(?)
                  AND ((parent_id IS NULL AND ? IS NULL) OR parent_id = ?)
                  AND (? IS NULL OR id <> ?)
                """, Integer.class, name, parentId, parentId, excludedId, excludedId);
        if (count != null && count > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_ALREADY_EXISTS", "Category already exists");
        }
    }

    private void ensureUniqueTag(String normalized, String excludedId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE normalized_name = ? AND (? IS NULL OR id <> ?)",
                Integer.class, normalized, excludedId, excludedId
        );
        if (count != null && count > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "TAG_ALREADY_EXISTS", "Tag already exists");
        }
    }

    private String cleanName(String value, boolean stripHash) {
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (stripHash) cleaned = cleaned.replaceFirst("^#+", "").trim();
        if (!StringUtils.hasText(cleaned) || cleaned.length() > 50) {
            throw badRequest("INVALID_METADATA_NAME", "Name must contain 1 to 50 characters");
        }
        return cleaned;
    }

    private String normalizeTag(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
