package io.github.tachikomako.xhsknowledge.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("knowledge_items", "content_status", "TEXT NOT NULL DEFAULT 'DISCOVERED'");
        addColumnIfMissing("knowledge_items", "content_last_error", "TEXT");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        List<String> columns = jdbcTemplate.query(
                "PRAGMA table_info(" + table + ")",
                (rs, rowNum) -> rs.getString("name")
        );
        if (!columns.contains(column)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
