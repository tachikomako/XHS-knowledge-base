package io.github.tachikomako.xhsknowledge.sync;

import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SyncRunService {

    private static final Set<String> SOURCES = Set.of("FAVORITE", "LIKED");
    private static final Set<String> STATUSES = Set.of("RUNNING", "COMPLETED", "PARTIAL_FAILED", "FAILED");

    private final JdbcTemplate jdbcTemplate;

    public SyncRunService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SyncRunView create(CreateSyncRunRequest request) {
        List<String> sources = request.requestedSources().stream()
                .map(String::trim)
                .distinct()
                .toList();
        if (sources.isEmpty() || !SOURCES.containsAll(sources)) {
            throw badRequest("INVALID_SYNC_SOURCES", "requestedSources must contain FAVORITE or LIKED");
        }
        String id = UUID.randomUUID().toString();
        String now = now();
        jdbcTemplate.update(
                """
                INSERT INTO sync_runs(
                  id, requested_sources, status, started_at
                ) VALUES (?, ?, 'RUNNING', ?)
                """,
                id,
                String.join(",", sources),
                now
        );
        return get(id);
    }

    @Transactional
    public SyncRunView update(String id, UpdateSyncRunRequest request) {
        get(id);
        String status = StringUtils.hasText(request.status()) ? request.status().trim() : "RUNNING";
        if (!STATUSES.contains(status)) {
            throw badRequest("INVALID_SYNC_STATUS", "Unsupported sync status");
        }
        String finishedAt = "RUNNING".equals(status) ? null : now();
        jdbcTemplate.update(
                """
                UPDATE sync_runs
                SET status = ?,
                    discovered_count = ?,
                    processed_count = ?,
                    created_count = ?,
                    updated_count = ?,
                    unchanged_count = ?,
                    content_completed_count = ?,
                    content_failed_count = ?,
                    ai_completed_count = ?,
                    ai_failed_count = ?,
                    finished_at = ?,
                    error_summary = ?
                WHERE id = ?
                """,
                status,
                value(request.discoveredCount()),
                value(request.processedCount()),
                value(request.createdCount()),
                value(request.updatedCount()),
                value(request.unchangedCount()),
                value(request.contentCompletedCount()),
                value(request.contentFailedCount()),
                value(request.aiCompletedCount()),
                value(request.aiFailedCount()),
                finishedAt,
                trimToNull(request.errorSummary()),
                id
        );
        return get(id);
    }

    public SyncRunView latest() {
        List<SyncRunView> runs = jdbcTemplate.query(
                "SELECT * FROM sync_runs ORDER BY started_at DESC LIMIT 1",
                (rs, rowNum) -> toView(rs)
        );
        return runs.isEmpty() ? null : runs.get(0);
    }

    private SyncRunView get(String id) {
        List<SyncRunView> runs = jdbcTemplate.query(
                "SELECT * FROM sync_runs WHERE id = ?",
                (rs, rowNum) -> toView(rs),
                id
        );
        if (runs.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SYNC_RUN_NOT_FOUND", "Sync run not found");
        }
        return runs.get(0);
    }

    private SyncRunView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SyncRunView(
                rs.getString("id"),
                rs.getString("requested_sources"),
                rs.getString("status"),
                rs.getInt("discovered_count"),
                rs.getInt("processed_count"),
                rs.getInt("created_count"),
                rs.getInt("updated_count"),
                rs.getInt("unchanged_count"),
                rs.getInt("content_completed_count"),
                rs.getInt("content_failed_count"),
                rs.getInt("ai_completed_count"),
                rs.getInt("ai_failed_count"),
                rs.getString("started_at"),
                rs.getString("finished_at"),
                rs.getString("error_summary")
        );
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
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
