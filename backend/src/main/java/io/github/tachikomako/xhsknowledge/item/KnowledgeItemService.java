package io.github.tachikomako.xhsknowledge.item;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeItemService {

    private static final Set<String> LIFECYCLE_STATUSES = Set.of("ACTIVE", "ARCHIVED", "TRASHED");
    private static final Set<String> CAPTURE_LEVELS = Set.of("CARD", "DETAIL");
    private static final Set<String> AI_STATUSES = Set.of("NOT_REQUESTED", "PENDING", "SUCCESS", "FAILED");

    private final KnowledgeItemMapper itemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeItemService(
            KnowledgeItemMapper itemMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.itemMapper = itemMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public PageResponse<KnowledgeItemView> search(
            String query,
            String categoryId,
            String tagId,
            String sourceType,
            String captureLevel,
            String lifecycleStatus,
            String aiStatus,
            int page,
            int pageSize,
            String sort
    ) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw badRequest("INVALID_PAGE", "page must be >= 1 and pageSize must be between 1 and 100");
        }
        validateOptional(captureLevel, CAPTURE_LEVELS, "captureLevel");
        validateOptional(aiStatus, AI_STATUSES, "aiStatus");
        validateOptional(lifecycleStatus, LIFECYCLE_STATUSES, "lifecycleStatus");
        if (StringUtils.hasText(query) && query.length() > 200) {
            throw badRequest("QUERY_TOO_LONG", "q must not exceed 200 characters");
        }

        LambdaQueryWrapper<KnowledgeItemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeItemEntity::getLifecycleStatus,
                StringUtils.hasText(lifecycleStatus) ? lifecycleStatus : "ACTIVE");
        if (StringUtils.hasText(categoryId)) {
            wrapper.and(nested -> nested
                    .eq(KnowledgeItemEntity::getCategoryId, categoryId)
                    .or().apply("category_id IN (SELECT id FROM categories WHERE parent_id = {0})", categoryId));
        }
        if (StringUtils.hasText(tagId)) {
            if (tagId.length() > 128) throw badRequest("INVALID_FILTER", "tagId is too long");
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM knowledge_item_tags kit WHERE kit.item_id = knowledge_items.id AND kit.tag_id = {0})",
                    tagId
            );
        }
        wrapper.eq(StringUtils.hasText(sourceType), KnowledgeItemEntity::getSourceType, sourceType);
        wrapper.eq(StringUtils.hasText(captureLevel), KnowledgeItemEntity::getCaptureLevel, captureLevel);
        wrapper.eq(StringUtils.hasText(aiStatus), KnowledgeItemEntity::getAiStatus, aiStatus);
        if (StringUtils.hasText(query)) {
            String term = query.trim();
            wrapper.and(nested -> nested
                    .like(KnowledgeItemEntity::getTitle, term)
                    .or().like(KnowledgeItemEntity::getContent, term)
                    .or().like(KnowledgeItemEntity::getSummary, term)
                    .or().like(KnowledgeItemEntity::getAuthor, term)
                    .or().like(KnowledgeItemEntity::getUserNote, term));
        }
        applySort(wrapper, sort);

        Page<KnowledgeItemEntity> result = itemMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<KnowledgeItemView> views = result.getRecords().stream().map(this::toView).toList();
        return new PageResponse<>(views, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public KnowledgeItemView get(String id) {
        return toView(requireItem(id));
    }

    @Transactional
    public KnowledgeItemView update(String id, UpdateKnowledgeItemRequest request) {
        KnowledgeItemEntity item = requireItem(id);
        boolean metadataChanged = false;
        boolean anyChanged = false;

        if (request.isCategoryIdPresent()) {
            String categoryId = trimToNull(request.getCategoryId());
            if (categoryId != null && !categoryExists(categoryId)) {
                throw badRequest("UNKNOWN_CATEGORY", "categoryId does not exist");
            }
            item.setCategoryId(categoryId);
            metadataChanged = true;
            anyChanged = true;
        }
        if (request.isSummaryPresent()) {
            String summary = validateLength(trimToNull(request.getSummary()), 2_000, "summary");
            item.setSummary(summary);
            metadataChanged = true;
            anyChanged = true;
        }
        if (request.isUserNotePresent()) {
            item.setUserNote(validateLength(trimToNull(request.getUserNote()), 20_000, "userNote"));
            anyChanged = true;
        }
        if (request.isTagIdsPresent()) {
            replaceTags(item.getId(), request.getTagIds());
            metadataChanged = true;
            anyChanged = true;
        }

        if (anyChanged) {
            String timestamp = now();
            item.setUserEditedAt(timestamp);
            item.setUpdatedAt(timestamp);
            if (metadataChanged) {
                item.setManualMetadataLocked(1);
            }
            itemMapper.updateById(item);
        }
        return toView(item);
    }

    @Transactional
    public KnowledgeItemView changeLifecycle(String id, String targetStatus) {
        if (!LIFECYCLE_STATUSES.contains(targetStatus)) {
            throw badRequest("INVALID_LIFECYCLE", "Unsupported lifecycle status");
        }
        KnowledgeItemEntity item = requireItem(id);
        if (!targetStatus.equals(item.getLifecycleStatus())) {
            item.setLifecycleStatus(targetStatus);
            item.setUpdatedAt(now());
            itemMapper.updateById(item);
        }
        return toView(item);
    }

    @Transactional
    public int bulkTrash(String scope, String categoryId) {
        String timestamp = now();
        if ("ALL".equals(scope)) {
            if (categoryId != null) {
                throw badRequest("INVALID_BULK_SCOPE", "categoryId must be omitted when scope is ALL");
            }
            return jdbcTemplate.update(
                    "UPDATE knowledge_items SET lifecycle_status = 'TRASHED', updated_at = ? "
                            + "WHERE lifecycle_status <> 'TRASHED'",
                    timestamp
            );
        }

        String normalizedCategoryId = trimToNull(categoryId);
        if (!"CATEGORY".equals(scope) || normalizedCategoryId == null) {
            throw badRequest("INVALID_BULK_SCOPE", "scope must be ALL or CATEGORY with a categoryId");
        }
        if (!categoryExists(normalizedCategoryId)) {
            throw badRequest("UNKNOWN_CATEGORY", "categoryId does not exist");
        }
        return jdbcTemplate.update(
                """
                UPDATE knowledge_items
                SET lifecycle_status = 'TRASHED', updated_at = ?
                WHERE lifecycle_status <> 'TRASHED'
                  AND (category_id = ? OR category_id IN (
                    SELECT id FROM categories WHERE parent_id = ?
                  ))
                """,
                timestamp,
                normalizedCategoryId,
                normalizedCategoryId
        );
    }

    private KnowledgeItemEntity requireItem(String id) {
        KnowledgeItemEntity item = itemMapper.selectById(id);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Knowledge item not found");
        }
        return item;
    }

    private void replaceTags(String itemId, List<String> requestedTagIds) {
        List<String> tagIds = requestedTagIds == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(requestedTagIds));
        if (tagIds.size() > 20 || tagIds.stream().anyMatch(id -> !StringUtils.hasText(id) || id.length() > 128)) {
            throw badRequest("INVALID_TAGS", "tagIds must contain at most 20 valid IDs");
        }
        for (String tagId : tagIds) {
            if (!tagExists(tagId)) {
                throw badRequest("UNKNOWN_TAG", "tagId does not exist: " + tagId);
            }
        }
        jdbcTemplate.update("DELETE FROM knowledge_item_tags WHERE item_id = ?", itemId);
        for (String tagId : tagIds) {
            jdbcTemplate.update(
                    "INSERT INTO knowledge_item_tags(item_id, tag_id) VALUES (?, ?)",
                    itemId,
                    tagId
            );
        }
    }

    private boolean categoryExists(String id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    private boolean tagExists(String id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    private List<String> findTagIds(String itemId) {
        return jdbcTemplate.queryForList(
                "SELECT tag_id FROM knowledge_item_tags WHERE item_id = ? ORDER BY tag_id",
                String.class,
                itemId
        );
    }

    private KnowledgeItemView toView(KnowledgeItemEntity item) {
        return new KnowledgeItemView(
                item.getId(),
                item.getSourceType(),
                item.getSourceItemId(),
                item.getCanonicalUrl(),
                item.getOriginalUrl(),
                item.getTitle(),
                item.getContent(),
                item.getAuthor(),
                item.getCoverUrl(),
                readImages(item.getImageUrlsJson()),
                item.getCaptureLevel(),
                item.getSummary(),
                item.getUserNote(),
                item.getCategoryId(),
                findTagIds(item.getId()),
                item.getAiStatus(),
                item.getAiConfidence(),
                item.getAiLastError(),
                item.getLifecycleStatus(),
                Integer.valueOf(1).equals(item.getManualMetadataLocked()),
                item.getCreatedAt(),
                item.getSourceUpdatedAt(),
                item.getUserEditedAt(),
                item.getUpdatedAt()
        );
    }

    private List<String> readImages(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void applySort(LambdaQueryWrapper<KnowledgeItemEntity> wrapper, String sort) {
        String normalized = StringUtils.hasText(sort) ? sort : "updatedAt,desc";
        switch (normalized) {
            case "updatedAt,asc" -> wrapper.orderByAsc(KnowledgeItemEntity::getUpdatedAt);
            case "createdAt,desc" -> wrapper.orderByDesc(KnowledgeItemEntity::getCreatedAt);
            case "createdAt,asc" -> wrapper.orderByAsc(KnowledgeItemEntity::getCreatedAt);
            case "updatedAt,desc" -> wrapper.orderByDesc(KnowledgeItemEntity::getUpdatedAt);
            default -> throw badRequest("INVALID_SORT", "sort must use createdAt or updatedAt with asc/desc");
        }
    }

    private void validateOptional(String value, Set<String> allowed, String field) {
        if (StringUtils.hasText(value) && !allowed.contains(value)) {
            throw badRequest("INVALID_FILTER", "Unsupported " + field);
        }
    }

    private String validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw badRequest("FIELD_TOO_LONG", field + " must not exceed " + max + " characters");
        }
        return value;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
