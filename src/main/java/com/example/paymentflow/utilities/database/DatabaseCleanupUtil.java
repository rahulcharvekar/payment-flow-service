package com.example.paymentflow.utilities.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Database cleanup utility for testing and development
 * WARNING: This will delete ALL data in the tables!
 */
@Component
public class DatabaseCleanupUtil {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseCleanupUtil.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Transactional
    public void cleanAllTables() {
        log.warn("Starting database cleanup - ALL DATA WILL BE DELETED!");
        
        try {
            // Disable foreign key checks
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            
            // Truncate all tables in the correct order
            String[] tables = {
                "board_receipts",
                "employer_payment_receipts", 
                "worker_payment_receipts",
                "worker_uploaded_data",
                "worker_payments",
                "uploaded_files"
            };
            
            for (String table : tables) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
                log.info("Truncated table: {}", table);
            }
            
            // Reset auto-increment counters
            for (String table : tables) {
                jdbcTemplate.execute("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
                log.info("Reset auto-increment for table: {}", table);
            }
            
            // Re-enable foreign key checks
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            
            log.warn("Database cleanup completed successfully!");
            
        } catch (Exception e) {
            log.error("Error during database cleanup", e);
            throw new RuntimeException("Database cleanup failed", e);
        }
    }
    
    public void verifyCleanup() {
        log.info("Verifying database cleanup...");
        
        String[] tables = {
            "worker_payments",
            "worker_uploaded_data",
            "uploaded_files",
            "worker_payment_receipts",
            "employer_payment_receipts",
            "board_receipts"
        };
        
        for (String table : tables) {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table, Long.class);
            log.info("Table {}: {} rows", table, count);
        }
    }
    
    public void dropUniqueConstraint() {
        try {
            log.warn("Dropping unique constraint on request_reference_number");
            jdbcTemplate.execute("ALTER TABLE worker_payments DROP INDEX UK_s4li48f0k5dgh680esuqitm7r");
            log.info("Successfully dropped unique constraint UK_s4li48f0k5dgh680esuqitm7r");
        } catch (Exception e) {
            log.warn("Failed to drop constraint (may not exist): {}", e.getMessage());
        }
    }
    
    public void showWorkerPaymentsSchema() {
        log.info("Worker payments table schema:");
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "DESCRIBE worker_payments");
        
        for (Map<String, Object> column : columns) {
            log.info("Column: {} | Type: {} | Null: {} | Key: {} | Default: {}", 
                column.get("Field"),
                column.get("Type"),
                column.get("Null"),
                column.get("Key"),
                column.get("Default"));
        }
    }
    
    public void updateStatusColumnSize() {
        try {
            log.warn("Updating worker_payments status column size to VARCHAR(40)");
            jdbcTemplate.execute("ALTER TABLE worker_payments MODIFY COLUMN status VARCHAR(40) NOT NULL");
            log.info("Successfully updated status column to VARCHAR(40)");
        } catch (Exception e) {
            log.error("Failed to update status column size: {}", e.getMessage());
            throw new RuntimeException("Status column update failed", e);
        }
    }
    
    public void addCreatedAtColumn() {
        try {
            log.warn("Adding created_at column to worker_payments table");
            
            // Check if column already exists
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'worker_payments' AND COLUMN_NAME = 'created_at'"
            );
            
            if (!columns.isEmpty()) {
                log.info("created_at column already exists in worker_payments table");
                return;
            }
            
            // Add the created_at column
            jdbcTemplate.execute(
                "ALTER TABLE worker_payments " +
                "ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
            );
            log.info("Successfully added created_at column");
            
            // Update existing records to have a created_at timestamp (handle invalid dates)
            try {
                int updatedRows = jdbcTemplate.update(
                    "UPDATE worker_payments " +
                    "SET created_at = CURRENT_TIMESTAMP " +
                    "WHERE created_at IS NULL"
                );
                log.info("Updated {} existing records with current timestamp", updatedRows);
            } catch (Exception e) {
                log.warn("Could not update existing records, they might already have valid timestamps: {}", e.getMessage());
            }
            
            // Add indexes for better query performance
            try {
                jdbcTemplate.execute("CREATE INDEX idx_worker_payments_created_at ON worker_payments(created_at)");
                log.info("Created index: idx_worker_payments_created_at");
            } catch (Exception e) {
                log.warn("Index idx_worker_payments_created_at might already exist: {}", e.getMessage());
            }
            
            try {
                jdbcTemplate.execute("CREATE INDEX idx_worker_payments_status_created_at ON worker_payments(status, created_at)");
                log.info("Created index: idx_worker_payments_status_created_at");
            } catch (Exception e) {
                log.warn("Index idx_worker_payments_status_created_at might already exist: {}", e.getMessage());
            }
            
            try {
                jdbcTemplate.execute("CREATE INDEX idx_worker_payments_receipt_created_at ON worker_payments(receipt_number, created_at)");
                log.info("Created index: idx_worker_payments_receipt_created_at");
            } catch (Exception e) {
                log.warn("Index idx_worker_payments_receipt_created_at might already exist: {}", e.getMessage());
            }
            
            log.info("Successfully completed created_at column migration");
            
        } catch (Exception e) {
            log.error("Failed to add created_at column: {}", e.getMessage());
            throw new RuntimeException("Created_at column migration failed", e);
        }
    }
}
