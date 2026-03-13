package com.reajason.noone.server.project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProjectSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        backfillDeletedFlag();
        ensurePartialUniqueIndex();
    }

    private void backfillDeletedFlag() {
        jdbcTemplate.update("UPDATE projects SET deleted = FALSE WHERE deleted IS NULL");
    }

    private void ensurePartialUniqueIndex() {
        try {
            jdbcTemplate.execute("ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_code_key");
            jdbcTemplate.execute("DROP INDEX IF EXISTS projects_code_key");
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_projects_code_active_unique");
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_code_active_unique
                    ON projects (code)
                    WHERE deleted IS NOT TRUE
                    """);
        } catch (Exception exception) {
            log.warn("Failed to ensure project partial unique index", exception);
        }
    }
}
