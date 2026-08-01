package io.github.tachikomako.xhsknowledge.importx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.tachikomako.xhsknowledge.ai.AiOrganizationService;
import io.github.tachikomako.xhsknowledge.common.ApiException;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemEntity;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemMapper;
import io.github.tachikomako.xhsknowledge.source.XiaohongshuUrlNormalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ImportService {

    private static final String SOURCE_TYPE = "XIAOHONGSHU";
    private static final Pattern XSEC_TOKEN = Pattern.compile("(?:^|&)xsec_token=[^&]+");
    private static final Pattern XSEC_SOURCE = Pattern.compile("(?:^|&)xsec_source=[^&]+");

    private final KnowledgeItemMapper itemMapper;
    private final ImportBatchMapper batchMapper;
    private final XiaohongshuUrlNormalizer urlNormalizer;
    private final AiOrganizationService aiOrganizationService;
    private final JdbcTemplate jdbcTemplate;

    public ImportService(
            KnowledgeItemMapper itemMapper,
            ImportBatchMapper batchMapper,
            XiaohongshuUrlNormalizer urlNormalizer,
            AiOrganizationService aiOrganizationService,
            JdbcTemplate jdbcTemplate
    ) {
        this.itemMapper = itemMapper;
        this.batchMapper = batchMapper;
        this.urlNormalizer = urlNormalizer;
        this.aiOrganizationService = aiOrganizationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ImportResponse importItems(XiaohongshuImportRequest request) {
        ImportBatchEntity previous = findBatch(request.clientBatchId());
        if (previous != null) {
            return fromPrevious(previous);
        }

        String batchId = UUID.randomUUID().toString();
        List<ImportResponse.ItemResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<String> aiItemIds = new ArrayList<>();

        for (int index = 0; index < request.items().size(); index++) {
            XiaohongshuImportRequest.IncomingItem incoming = request.items().get(index);
            try {
                ImportResponse.ItemResult result = upsert(index, incoming);
                results.add(result);
                switch (result.status()) {
                    case "CREATED" -> {
                        created++;
                        aiItemIds.add(result.itemId());
                    }
                    case "UPDATED" -> {
                        updated++;
                        aiItemIds.add(result.itemId());
                    }
                    default -> skipped++;
                }
            } catch (ApiException | IllegalArgumentException exception) {
                failed++;
                results.add(new ImportResponse.ItemResult(
                        index,
                        null,
                        incoming.sourceItemId(),
                        "FAILED",
                        safeError(exception)
                ));
            }
        }

        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setId(batchId);
        batch.setClientBatchId(request.clientBatchId());
        batch.setCaptureMode(request.captureMode());
        batch.setExtractorVersion(request.extractorVersion());
        batch.setReceived(request.items().size());
        batch.setCreatedCount(created);
        batch.setUpdatedCount(updated);
        batch.setSkippedCount(skipped);
        batch.setFailedCount(failed);
        batch.setCreatedAt(now());
        batchMapper.insert(batch);
        scheduleAi(aiItemIds);

        return new ImportResponse(
                batchId,
                false,
                request.items().size(),
                created,
                updated,
                skipped,
                failed,
                List.copyOf(results)
        );
    }

    private void scheduleAi(List<String> itemIds) {
        if (itemIds.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                itemIds.forEach(aiOrganizationService::organizeLater);
            }
        });
    }

    private ImportResponse.ItemResult upsert(
            int index,
            XiaohongshuImportRequest.IncomingItem incoming
    ) {
        XiaohongshuUrlNormalizer.NormalizedSource source =
                urlNormalizer.normalize(incoming.url(), incoming.sourceItemId());
        KnowledgeItemEntity existing = findExisting(source);
        if (existing == null) {
            KnowledgeItemEntity created = createEntity(incoming, source);
            itemMapper.insert(created);
            return new ImportResponse.ItemResult(index, created.getId(), source.sourceItemId(), "CREATED", null);
        }
        if (!"ACTIVE".equals(existing.getLifecycleStatus())) {
            jdbcTemplate.update("DELETE FROM knowledge_item_tags WHERE item_id = ?", existing.getId());
            jdbcTemplate.update("DELETE FROM item_ai_suggestions WHERE item_id = ?", existing.getId());
            itemMapper.deleteById(existing.getId());
            KnowledgeItemEntity created = createEntity(incoming, source);
            itemMapper.insert(created);
            return new ImportResponse.ItemResult(index, created.getId(), source.sourceItemId(), "CREATED", null);
        }

        boolean changed = mergeSourceFields(existing, incoming, source);
        if (changed) {
            itemMapper.updateById(existing);
            return new ImportResponse.ItemResult(index, existing.getId(), source.sourceItemId(), "UPDATED", null);
        }
        return new ImportResponse.ItemResult(index, existing.getId(), source.sourceItemId(), "SKIPPED", null);
    }

    private KnowledgeItemEntity createEntity(
            XiaohongshuImportRequest.IncomingItem incoming,
            XiaohongshuUrlNormalizer.NormalizedSource source
    ) {
        String timestamp = now();
        KnowledgeItemEntity item = new KnowledgeItemEntity();
        item.setId(UUID.randomUUID().toString());
        item.setSourceType(SOURCE_TYPE);
        item.setSourceItemId(source.sourceItemId());
        item.setCanonicalUrl(source.canonicalUrl());
        item.setOriginalUrl(incoming.url().trim());
        item.setTitle(incoming.title().trim());
        item.setContent(trimToNull(incoming.text()));
        item.setAuthor(trimToNull(incoming.author()));
        item.setCoverUrl(normalizeMediaUrl(incoming.coverUrl()));
        item.setImageUrlsJson("[]");
        item.setCaptureLevel(incoming.captureLevel());
        item.setAiStatus("NOT_REQUESTED");
        item.setLifecycleStatus("ACTIVE");
        item.setManualMetadataLocked(0);
        item.setCreatedAt(timestamp);
        item.setSourceUpdatedAt(timestamp);
        item.setUpdatedAt(timestamp);
        return item;
    }

    private boolean mergeSourceFields(
            KnowledgeItemEntity existing,
            XiaohongshuImportRequest.IncomingItem incoming,
            XiaohongshuUrlNormalizer.NormalizedSource source
    ) {
        boolean incomingIsDetail = "DETAIL".equals(incoming.captureLevel());
        boolean changed = false;

        String incomingUrl = incoming.url().trim();
        if (shouldReplaceOriginalUrl(existing.getOriginalUrl(), incomingUrl)) {
            existing.setOriginalUrl(incomingUrl);
            changed = true;
        }
        if (!StringUtils.hasText(existing.getSourceItemId()) && source.sourceItemId() != null) {
            existing.setSourceItemId(source.sourceItemId());
            changed = true;
        }
        if (incomingIsDetail && !Objects.equals(existing.getTitle(), incoming.title().trim())) {
            existing.setTitle(incoming.title().trim());
            changed = true;
        }
        changed |= setIfMoreComplete(existing.getContent(), incoming.text(), existing::setContent);
        changed |= setIfMoreComplete(existing.getAuthor(), incoming.author(), existing::setAuthor);
        changed |= setIfMoreComplete(
                existing.getCoverUrl(),
                normalizeMediaUrl(incoming.coverUrl()),
                existing::setCoverUrl
        );

        if (incomingIsDetail && "CARD".equals(existing.getCaptureLevel())) {
            existing.setCaptureLevel("DETAIL");
            changed = true;
        }

        if (changed) {
            String timestamp = now();
            existing.setSourceUpdatedAt(timestamp);
            existing.setUpdatedAt(timestamp);
        }
        return changed;
    }

    private boolean shouldReplaceOriginalUrl(String current, String candidate) {
        if (Objects.equals(current, candidate)) {
            return false;
        }
        int currentScore = accessUrlScore(current);
        int candidateScore = accessUrlScore(candidate);
        return candidateScore > currentScore || (candidateScore == currentScore && candidateScore > 0);
    }

    private int accessUrlScore(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            String query = URI.create(value).getRawQuery();
            if (query == null) {
                return 0;
            }
            int score = 0;
            if (XSEC_TOKEN.matcher(query).find()) score += 2;
            if (XSEC_SOURCE.matcher(query).find()) score += 1;
            return score;
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

    private boolean setIfMoreComplete(String current, String candidate, java.util.function.Consumer<String> setter) {
        String normalized = trimToNull(candidate);
        if (normalized != null && (!StringUtils.hasText(current) || normalized.length() > current.length())) {
            setter.accept(normalized);
            return true;
        }
        return false;
    }

    private KnowledgeItemEntity findExisting(XiaohongshuUrlNormalizer.NormalizedSource source) {
        if (source.sourceItemId() != null) {
            KnowledgeItemEntity byId = itemMapper.selectOne(new LambdaQueryWrapper<KnowledgeItemEntity>()
                    .eq(KnowledgeItemEntity::getSourceType, SOURCE_TYPE)
                    .eq(KnowledgeItemEntity::getSourceItemId, source.sourceItemId())
                    .last("LIMIT 1"));
            if (byId != null) {
                return byId;
            }
        }
        return itemMapper.selectOne(new LambdaQueryWrapper<KnowledgeItemEntity>()
                .eq(KnowledgeItemEntity::getSourceType, SOURCE_TYPE)
                .eq(KnowledgeItemEntity::getCanonicalUrl, source.canonicalUrl())
                .last("LIMIT 1"));
    }

    private ImportBatchEntity findBatch(String clientBatchId) {
        return batchMapper.selectOne(new LambdaQueryWrapper<ImportBatchEntity>()
                .eq(ImportBatchEntity::getClientBatchId, clientBatchId)
                .last("LIMIT 1"));
    }

    private ImportResponse fromPrevious(ImportBatchEntity batch) {
        return new ImportResponse(
                batch.getId(),
                true,
                batch.getReceived(),
                batch.getCreatedCount(),
                batch.getUpdatedCount(),
                batch.getSkippedCount(),
                batch.getFailedCount(),
                List.of()
        );
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Import failed" : message.substring(0, Math.min(200, message.length()));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMediaUrl(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid media URL");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Media URL must use HTTP or HTTPS");
        }
        return normalized;
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
