package io.github.tachikomako.xhsknowledge.importx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.common.ApiException;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemEntity;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemMapper;
import io.github.tachikomako.xhsknowledge.source.XiaohongshuUrlNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ImportService {

    private static final String SOURCE_TYPE = "XIAOHONGSHU";

    private final KnowledgeItemMapper itemMapper;
    private final ImportBatchMapper batchMapper;
    private final XiaohongshuUrlNormalizer urlNormalizer;
    private final ObjectMapper objectMapper;

    public ImportService(
            KnowledgeItemMapper itemMapper,
            ImportBatchMapper batchMapper,
            XiaohongshuUrlNormalizer urlNormalizer,
            ObjectMapper objectMapper
    ) {
        this.itemMapper = itemMapper;
        this.batchMapper = batchMapper;
        this.urlNormalizer = urlNormalizer;
        this.objectMapper = objectMapper;
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

        for (int index = 0; index < request.items().size(); index++) {
            XiaohongshuImportRequest.IncomingItem incoming = request.items().get(index);
            try {
                ImportResponse.ItemResult result = upsert(index, incoming);
                results.add(result);
                switch (result.status()) {
                    case "CREATED" -> created++;
                    case "UPDATED" -> updated++;
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
        if ("TRASHED".equals(existing.getLifecycleStatus())) {
            return new ImportResponse.ItemResult(index, existing.getId(), source.sourceItemId(), "SKIPPED", null);
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
        item.setImageUrlsJson(writeImages(incoming.imageUrls()));
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

        changed |= setIfDifferent(existing.getOriginalUrl(), incoming.url().trim(), existing::setOriginalUrl);
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

        String incomingImages = writeImages(incoming.imageUrls());
        if (incomingIsDetail && incomingImages.length() > existing.getImageUrlsJson().length()
                && !incomingImages.equals(existing.getImageUrlsJson())) {
            existing.setImageUrlsJson(incomingImages);
            changed = true;
        }
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

    private boolean setIfDifferent(String current, String next, java.util.function.Consumer<String> setter) {
        if (!Objects.equals(current, next)) {
            setter.accept(next);
            return true;
        }
        return false;
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

    private String writeImages(List<String> imageUrls) {
        List<String> normalized = imageUrls.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeMediaUrl)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid image URLs", exception);
        }
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
