package io.github.tachikomako.xhsknowledge.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.common.ApiException;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemEntity;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemMapper;
import io.github.tachikomako.xhsknowledge.settings.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AiOrganizationService {
    private static final int TASK_BATCH_SIZE = 15;
    private static final String SCOPE_SELECTED = "SELECTED";
    private static final String SCOPE_ALL_PENDING = "ALL_PENDING";

    private final KnowledgeItemMapper itemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final QwenClient qwenClient;
    private final AiEligibilityService aiEligibilityService;
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, AiTaskState> tasks = new ConcurrentHashMap<>();

    public AiOrganizationService(
            KnowledgeItemMapper itemMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            QwenClient qwenClient,
            AiEligibilityService aiEligibilityService
    ) {
        this.itemMapper = itemMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.qwenClient = qwenClient;
        this.aiEligibilityService = aiEligibilityService;
    }

    public void organizeManually(String itemId) {
        if (!qwenClient.configured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_NOT_CONFIGURED", "Qwen API key is not configured");
        }
        try {
            if (!markProcessing(itemId, true)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AI_ITEM_NOT_ELIGIBLE", "内容当前不可分类");
            }
            organizeNow(itemId);
        } catch (ApiException exception) {
            markFailed(itemId, exception);
            throw exception;
        } catch (Exception exception) {
            markFailed(itemId, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ORGANIZE_FAILED", "AI organization failed");
        }
    }

    public AiOrganizeBatchResponse organizePending() {
        AiEligibilityService.AiEligibilityStats stats = aiEligibilityService.stats();
        if (!settingsService.aiEnabled()) {
            return batchResponse(stats, 0, 0, 0, 0, "AI 整理尚未开启");
        }
        if (!qwenClient.configured()) {
            return batchResponse(stats, 0, 0, 0, 0, "请先在设置中配置并测试 Qwen API");
        }
        List<String> itemIds = aiEligibilityService.eligibleItemIds(50);
        if (itemIds.isEmpty()) {
            return batchResponse(stats, 0, 0, 0, 0, noEligibleMessage(stats));
        }
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();
        for (String itemId : itemIds) {
            processed++;
            try {
                if (!markProcessing(itemId, false)) {
                    failed++;
                    errors.add("%s: 处理中或已被手动锁定".formatted(itemId));
                    continue;
                }
                organizeNow(itemId);
                succeeded++;
            } catch (Exception exception) {
                failed++;
                markFailed(itemId, exception);
                errors.add("%s: %s".formatted(itemId, safeError(exception)));
            }
        }
        return batchResponse(
                stats,
                processed,
                succeeded,
                failed,
                Math.max(0, stats.eligible() - processed),
                errors,
                "已处理 %d 条，成功 %d 条，失败 %d 条".formatted(processed, succeeded, failed)
        );
    }

    public AiOrganizeTaskView startPendingTask() {
        if (!settingsService.aiEnabled()) return rejectedTask(SCOPE_ALL_PENDING, 0, "AI 整理尚未开启");
        if (!qwenClient.configured()) return rejectedTask(SCOPE_ALL_PENDING, 0, "请先在设置中配置并测试 Qwen API");
        List<String> itemIds = aiEligibilityService.eligibleItemIds(300);
        if (itemIds.isEmpty()) return rejectedTask(SCOPE_ALL_PENDING, 0, noEligibleMessage(aiEligibilityService.stats()));
        return startTask(SCOPE_ALL_PENDING, itemIds.size(), new ScheduledItems(itemIds, 0, List.of()));
    }

    public AiOrganizeTaskView startTask(List<String> requestedItemIds) {
        int requestedCount = requestedIds(requestedItemIds).size();
        if (!settingsService.aiEnabled()) return rejectedTask(SCOPE_SELECTED, requestedCount, "AI 整理尚未开启");
        if (!qwenClient.configured()) return rejectedTask(SCOPE_SELECTED, requestedCount, "请先在设置中配置并测试 Qwen API");
        ScheduledItems scheduled = scheduleSelectedItems(requestedItemIds);
        if (scheduled.itemIds().isEmpty()) {
            return rejectedTask(SCOPE_SELECTED, requestedCount, scheduled.skipped(), "所选帖子当前不可分类", scheduled.errors());
        }

        return startTask(SCOPE_SELECTED, requestedCount, scheduled);
    }

    private AiOrganizeTaskView startTask(String scope, int requestedCount, ScheduledItems scheduled) {
        AiTaskState state = new AiTaskState(
                UUID.randomUUID().toString(),
                scope,
                requestedCount,
                scheduled.itemIds().size(),
                scheduled.skipped(),
                scheduled.errors()
        );
        tasks.put(state.id, state);
        taskExecutor.submit(() -> runTask(state, scheduled.itemIds()));
        return state.view();
    }

    public AiOrganizeTaskView task(String id) {
        AiTaskState state = tasks.get(id);
        if (state == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AI_TASK_NOT_FOUND", "AI task not found");
        }
        return state.view();
    }

    public AiOrganizeTaskView cancelTask(String id) {
        AiTaskState state = tasks.get(id);
        if (state == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AI_TASK_NOT_FOUND", "AI task not found");
        }
        if (!state.terminal()) {
            state.cancelRequested = true;
            state.status = "CANCELLED";
            state.message = "AI 分类已中断";
        }
        return state.view();
    }

    @PreDestroy
    void shutdown() {
        taskExecutor.shutdownNow();
    }

    @Transactional
    public void organizeNow(String itemId) throws Exception {
        KnowledgeItemEntity item = itemMapper.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getManualMetadataLocked())) return;
        QwenAiResult result = qwenClient.organize(prompt(item));
        validateResult(result);
        item = itemMapper.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getManualMetadataLocked())) return;

        Set<String> categoryIds = Set.copyOf(jdbcTemplate.queryForList("SELECT id FROM categories", String.class));
        Set<String> tagIds = Set.copyOf(jdbcTemplate.queryForList("SELECT id FROM tags", String.class));
        String categoryId = null;
        if (StringUtils.hasText(result.categoryId())) {
            if (!categoryIds.contains(result.categoryId())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_CATEGORY_NOT_FOUND", "分类 ID 不存在");
            }
            categoryId = result.categoryId();
        }
        List<String> validTagIds = result.tagIds() == null ? List.of() : result.tagIds().stream()
                .filter(tagIds::contains)
                .distinct()
                .limit(20)
                .toList();

        String timestamp = now();
        item.setSummary(StringUtils.hasText(item.getContent()) ? limit(result.summary(), 500) : item.getSummary());
        item.setCategoryId(categoryId);
        item.setAiConfidence(Math.max(0, Math.min(1, result.confidence())));
        item.setAiStatus("COMPLETED");
        item.setAiLastError(null);
        item.setUpdatedAt(timestamp);
        itemMapper.updateById(item);
        replaceTags(itemId, validTagIds);
        saveSuggestions(itemId, result.suggestedTags(), timestamp);
    }

    private boolean markProcessing(String itemId, boolean allowCompleted) {
        String statuses = allowCompleted ? "'PENDING', 'FAILED', 'COMPLETED'" : "'PENDING', 'FAILED'";
        return jdbcTemplate.update("""
                UPDATE knowledge_items
                SET ai_status = 'PROCESSING', ai_last_error = NULL, updated_at = ?
                WHERE id = ?
                  AND lifecycle_status = 'ACTIVE'
                  AND trim(title) <> ''
                  AND manual_metadata_locked = 0
                  AND ai_status IN (%s)
                """.formatted(statuses), now(), itemId) == 1;
    }

    private void markFailed(String itemId, Exception exception) {
        jdbcTemplate.update("""
                UPDATE knowledge_items
                SET ai_status = 'FAILED', ai_last_error = ?, updated_at = ?
                WHERE id = ? AND manual_metadata_locked = 0
                """, safeAiError(exception), now(), itemId);
    }

    private void runTask(AiTaskState state, List<String> itemIds) {
        if (state.cancelRequested) return;
        state.status = "RUNNING";
        for (int start = 0; start < itemIds.size(); start += TASK_BATCH_SIZE) {
            for (String itemId : itemIds.subList(start, Math.min(start + TASK_BATCH_SIZE, itemIds.size()))) {
                if (state.cancelRequested) return;
                if (!markProcessing(itemId, !SCOPE_ALL_PENDING.equals(state.scope))) {
                    state.skipped++;
                    state.errors.add("%s: 处理中或已被手动锁定".formatted(itemId));
                } else {
                    try {
                        organizeNow(itemId);
                        state.succeeded++;
                    } catch (Exception exception) {
                        state.failed++;
                        markFailed(itemId, exception);
                        state.errors.add("%s: %s".formatted(itemId, safeAiError(exception)));
                    } finally {
                        state.processed++;
                    }
                }
                if (state.cancelRequested) return;
            }
        }
        state.status = state.failed > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
        state.message = "正在分类：%d / %d，成功：%d，失败：%d".formatted(
                state.processed, state.total, state.succeeded, state.failed
        );
    }

    private List<String> requestedIds(List<String> requestedItemIds) {
        if (requestedItemIds == null || requestedItemIds.isEmpty()) return List.of();
        return requestedItemIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(300)
                .toList();
    }

    private ScheduledItems scheduleSelectedItems(List<String> requestedItemIds) {
        List<String> ids = requestedIds(requestedItemIds);
        if (ids.isEmpty()) return new ScheduledItems(List.of(), 0, List.of());
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<RequestedItem> records = jdbcTemplate.query("""
                SELECT id, lifecycle_status, title, manual_metadata_locked, ai_status
                FROM knowledge_items
                WHERE id IN (%s)
                """.formatted(placeholders), ids.toArray(), (resultSet, rowNum) -> new RequestedItem(
                resultSet.getString("id"),
                resultSet.getString("lifecycle_status"),
                resultSet.getString("title"),
                resultSet.getInt("manual_metadata_locked"),
                resultSet.getString("ai_status")
        ));
        Map<String, RequestedItem> byId = new LinkedHashMap<>();
        records.forEach(record -> byId.put(record.id(), record));
        List<String> scheduled = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String id : ids) {
            RequestedItem record = byId.get(id);
            if (record == null) {
                errors.add(id + ": 记录不存在");
            } else if (!"ACTIVE".equals(record.lifecycleStatus())) {
                errors.add(id + ": 内容已删除");
            } else if (!StringUtils.hasText(record.title())) {
                errors.add(id + ": 标题为空");
            } else if (record.manualMetadataLocked() != 0) {
                errors.add(id + ": 已被手动锁定");
            } else if (!Set.of("PENDING", "FAILED", "COMPLETED").contains(record.aiStatus())) {
                errors.add(id + ": 正在处理中或状态不可重试");
            } else {
                scheduled.add(id);
            }
        }
        return new ScheduledItems(scheduled, ids.size() - scheduled.size(), errors);
    }

    private AiOrganizeTaskView rejectedTask(String scope, int requestedCount, String message) {
        return rejectedTask(scope, requestedCount, 0, message, List.of());
    }

    private AiOrganizeTaskView rejectedTask(String scope, int requestedCount, int skipped, String message, List<String> errors) {
        return new AiOrganizeTaskView(null, "REJECTED", scope, requestedCount, 0, 0, 0, 0, skipped, errors, message);
    }

    private void validateResult(QwenAiResult result) {
        if (result == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_PARSE_FAILED", "响应解析失败");
        }
    }

    private String safeAiError(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return switch (apiException.getCode()) {
                case "AI_CATEGORY_NOT_FOUND" -> "分类 ID 不存在";
                case "AI_RESPONSE_PARSE_FAILED" -> "响应解析失败";
                default -> safeError(exception);
            };
        }
        String name = exception.getClass().getSimpleName().toLowerCase();
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (name.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
            return "Qwen 请求超时";
        }
        if (exception instanceof JsonProcessingException) {
            return "响应解析失败";
        }
        if (exception instanceof RestClientException) {
            return "Qwen 请求失败";
        }
        return safeError(exception);
    }

    private AiOrganizeBatchResponse batchResponse(
            AiEligibilityService.AiEligibilityStats stats,
            int processed,
            int succeeded,
            int failed,
            int skipped,
            String message
    ) {
        return batchResponse(stats, processed, succeeded, failed, skipped, List.of(), message);
    }

    private AiOrganizeBatchResponse batchResponse(
            AiEligibilityService.AiEligibilityStats stats,
            int processed,
            int succeeded,
            int failed,
            int skipped,
            List<String> errors,
            String message
    ) {
        return new AiOrganizeBatchResponse(
                stats.eligible(),
                processed,
                succeeded,
                failed,
                stats.blockedByContent(),
                stats.blockedByManualLock(),
                skipped,
                errors,
                message
        );
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) return exception.getClass().getSimpleName();
        return limit(message, 200);
    }

    private String noEligibleMessage(AiEligibilityService.AiEligibilityStats stats) {
        if (stats.blockedByContent() > 0) {
            return "没有可整理内容，条目需要至少包含标题";
        }
        if (stats.blockedByManualLock() > 0) {
            return "待处理内容已被用户手动锁定";
        }
        return "当前没有需要 AI 整理的内容";
    }

    private String prompt(KnowledgeItemEntity item) throws JsonProcessingException {
        return """
                请整理下面的小红书收藏，返回 JSON：
                {"summary":"两三句话的简短摘要","categoryId":"existing-category-id","tagIds":["existing-tag-id"],"suggestedTags":["新标签建议"],"confidence":0.86}
                只能选择已有分类和已有标签；没有合适分类或标签时用 null 或空数组。正文未采集时，只能根据标题、作者和来源标签判断，不要编造正文细节。

                已有分类：
                %s

                已有标签：
                %s

                收藏内容：
                标题：%s
                作者：%s
                来源标签：%s
                正文：%s
                """.formatted(
                objectMapper.writeValueAsString(catalog("categories")),
                objectMapper.writeValueAsString(catalog("tags")),
                item.getTitle(),
                item.getAuthor(),
                objectMapper.writeValueAsString(sourceTags(item.getId())),
                StringUtils.hasText(item.getContent()) ? limit(item.getContent(), 4000) : "(content not captured)"
        );
    }

    private List<?> catalog(String table) {
        return jdbcTemplate.queryForList("SELECT id, name FROM " + table + " ORDER BY lower(name)");
    }

    private List<String> sourceTags(String itemId) {
        return jdbcTemplate.queryForList(
                "SELECT value FROM knowledge_item_source_tags WHERE item_id = ? ORDER BY lower(value)",
                String.class,
                itemId
        );
    }

    private void replaceTags(String itemId, List<String> tagIds) {
        jdbcTemplate.update("DELETE FROM knowledge_item_tags WHERE item_id = ?", itemId);
        for (String tagId : tagIds) {
            jdbcTemplate.update("INSERT INTO knowledge_item_tags(item_id, tag_id) VALUES (?, ?)", itemId, tagId);
        }
    }

    private void saveSuggestions(String itemId, List<String> suggestions, String timestamp) {
        jdbcTemplate.update("DELETE FROM item_ai_suggestions WHERE item_id = ? AND suggestion_type = 'TAG'", itemId);
        if (suggestions == null) return;
        for (String suggestion : new LinkedHashSet<>(suggestions)) {
            String value = limit(suggestion, 50);
            if (StringUtils.hasText(value)) {
                jdbcTemplate.update(
                        "INSERT OR IGNORE INTO item_ai_suggestions(item_id, suggestion_type, value, created_at) VALUES (?, 'TAG', ?, ?)",
                        itemId,
                        value.trim(),
                        timestamp
                );
            }
        }
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private static final class AiTaskState {
        private final String id;
        private final String scope;
        private final int requestedCount;
        private final int total;
        private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        private volatile String status = "QUEUED";
        private volatile int processed;
        private volatile int succeeded;
        private volatile int failed;
        private volatile int skipped;
        private volatile boolean cancelRequested;
        private volatile String message = "任务已创建";

        private AiTaskState(String id, String scope, int requestedCount, int total, int skipped, List<String> errors) {
            this.id = id;
            this.scope = scope;
            this.requestedCount = requestedCount;
            this.total = total;
            this.skipped = skipped;
            this.errors.addAll(errors);
        }

        private AiOrganizeTaskView view() {
            return new AiOrganizeTaskView(id, status, scope, requestedCount, total, processed, succeeded, failed, skipped, List.copyOf(errors), message);
        }

        private boolean terminal() {
            return List.of("COMPLETED", "COMPLETED_WITH_ERRORS", "REJECTED", "CANCELLED").contains(status);
        }
    }

    private record ScheduledItems(List<String> itemIds, int skipped, List<String> errors) {
    }

    private record RequestedItem(
            String id,
            String lifecycleStatus,
            String title,
            int manualMetadataLocked,
            String aiStatus
    ) {
    }

}
