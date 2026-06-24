package com.photorestoration.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        addColumnIfMissing(
                "email_notification",
                "ALTER TABLE users ADD COLUMN email_notification TINYINT(1) NOT NULL DEFAULT 1"
        );
        addColumnIfMissing(
                "ws_notification",
                "ALTER TABLE users ADD COLUMN ws_notification TINYINT(1) NOT NULL DEFAULT 1"
        );
    }

    private void addColumnIfMissing(String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(sql);
            log.info("Added users.{} column", columnName);
        }
    }
}
