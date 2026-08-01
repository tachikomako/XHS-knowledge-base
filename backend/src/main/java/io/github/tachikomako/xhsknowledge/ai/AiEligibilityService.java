package io.github.tachikomako.xhsknowledge.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiEligibilityService {

    private final JdbcTemplate jdbcTemplate;

    public AiEligibilityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> eligibleItemIds(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM knowledge_items
                WHERE lifecycle_status = 'ACTIVE'
                  AND content_status = 'COMPLETED'
                  AND manual_metadata_locked = 0
                  AND ai_status IN ('PENDING', 'FAILED')
                ORDER BY updated_at DESC
                LIMIT ?
                """, String.class, limit);
    }

    public int eligibleCount() {
        return count("""
                lifecycle_status = 'ACTIVE'
                AND content_status = 'COMPLETED'
                AND manual_metadata_locked = 0
                AND ai_status IN ('PENDING', 'FAILED')
                """);
    }

    public AiEligibilityStats stats() {
        int eligible = eligibleCount();
        int blockedByContent = count("""
                lifecycle_status = 'ACTIVE'
                AND content_status <> 'COMPLETED'
                AND manual_metadata_locked = 0
                AND ai_status IN ('PENDING', 'FAILED')
                """);
        int blockedByManualLock = count("""
                lifecycle_status = 'ACTIVE'
                AND content_status = 'COMPLETED'
                AND manual_metadata_locked = 1
                AND ai_status IN ('PENDING', 'FAILED')
                """);
        return new AiEligibilityStats(eligible, blockedByContent, blockedByManualLock);
    }

    private int count(String where) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_items WHERE " + where, Integer.class);
        return count == null ? 0 : count;
    }

    public record AiEligibilityStats(int eligible, int blockedByContent, int blockedByManualLock) {
    }
}
