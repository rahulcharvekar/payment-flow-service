package com.example.paymentflow.employer.dao;

import com.example.paymentflow.common.dao.BaseQueryDao;
import com.example.paymentflow.employer.entity.EmployerPaymentReceipt;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO for Employer Payment Receipt read operations using custom queries.
 * Centralizes all read logic for better control and debugging.
 */
@Repository
public class EmployerPaymentReceiptQueryDao extends BaseQueryDao {
    
    private static final String BASE_SELECT = """
        SELECT id, employer_receipt_number, worker_receipt_number, employer_id, toli_id,
               transaction_reference, validated_by, validated_at, total_records, 
               total_amount, status
        FROM employer_payment_receipts
        """;
    
    private static final String BASE_COUNT = "SELECT COUNT(*) FROM employer_payment_receipts";
    
    /**
     * Find by ID with custom query
     */
    public Optional<EmployerPaymentReceipt> findById(Long id) {
        String sql = BASE_SELECT + " WHERE id = :id";
        Map<String, Object> params = Map.of("id", id);
        return queryForObject(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by employer receipt number
     */
    public Optional<EmployerPaymentReceipt> findByEmployerReceiptNumber(String employerReceiptNumber) {
        String sql = BASE_SELECT + " WHERE employer_receipt_number = :empRef";
        Map<String, Object> params = Map.of("empRef", employerReceiptNumber);
        return queryForObject(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by worker receipt number
     */
    public Optional<EmployerPaymentReceipt> findByWorkerReceiptNumber(String workerReceiptNumber) {
        String sql = BASE_SELECT + " WHERE worker_receipt_number = :workerRef";
        Map<String, Object> params = Map.of("workerRef", workerReceiptNumber);
        return queryForObject(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find with comprehensive filters and pagination
     */
    public PageResult<EmployerPaymentReceipt> findWithFilters(String status, String employerReceiptNumber,
                                                            String validatedBy, LocalDateTime startDate, 
                                                            LocalDateTime endDate, int page, int size) {
        
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        
        if (status != null && !status.trim().isEmpty()) {
            whereClause.append(" AND status = :status");
            params.put("status", status);
        }
        
        if (employerReceiptNumber != null && !employerReceiptNumber.trim().isEmpty()) {
            whereClause.append(" AND employer_receipt_number = :empRef");
            params.put("empRef", employerReceiptNumber);
        }
        
        if (validatedBy != null && !validatedBy.trim().isEmpty()) {
            whereClause.append(" AND validated_by = :validatedBy");
            params.put("validatedBy", validatedBy);
        }
        
        if (startDate != null) {
            whereClause.append(" AND validated_at >= :startDate");
            params.put("startDate", startDate);
        }
        
        if (endDate != null) {
            whereClause.append(" AND validated_at <= :endDate");
            params.put("endDate", endDate);
        }
        
        String baseSql = BASE_SELECT + whereClause + " ORDER BY validated_at DESC";
        String countSql = BASE_COUNT + whereClause;
        
        return queryForPage(baseSql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by status with pagination
     */
    public PageResult<EmployerPaymentReceipt> findByStatus(String status, int page, int size) {
        String sql = BASE_SELECT + " WHERE status = :status ORDER BY validated_at DESC";
        String countSql = BASE_COUNT + " WHERE status = :status";
        Map<String, Object> params = Map.of("status", status);
        
        return queryForPage(sql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by validator with pagination
     */
    public PageResult<EmployerPaymentReceipt> findByValidatedBy(String validatedBy, int page, int size) {
        String sql = BASE_SELECT + " WHERE validated_by = :validatedBy ORDER BY validated_at DESC";
        String countSql = BASE_COUNT + " WHERE validated_by = :validatedBy";
        Map<String, Object> params = Map.of("validatedBy", validatedBy);
        
        return queryForPage(sql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by transaction reference for MT940 reconciliation
     */
    public List<EmployerPaymentReceipt> findByTransactionReference(String transactionReference) {
        String sql = BASE_SELECT + " WHERE transaction_reference = :txnRef ORDER BY validated_at DESC";
        Map<String, Object> params = Map.of("txnRef", transactionReference);
        return queryForList(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by date range with pagination
     */
    public PageResult<EmployerPaymentReceipt> findByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                            int page, int size) {
        String sql = BASE_SELECT + " WHERE validated_at BETWEEN :startDate AND :endDate ORDER BY validated_at DESC";
        String countSql = BASE_COUNT + " WHERE validated_at BETWEEN :startDate AND :endDate";
        
        Map<String, Object> params = Map.of(
            "startDate", startDate,
            "endDate", endDate
        );
        
        return queryForPage(sql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Get validation statistics
     */
    public Map<String, Object> getValidationStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT 
                COUNT(*) as total_receipts,
                SUM(total_amount) as total_amount_sum,
                SUM(validated_amount) as validated_amount_sum,
                AVG(total_amount) as average_total_amount,
                AVG(validated_amount) as average_validated_amount,
                COUNT(DISTINCT status) as unique_statuses,
                COUNT(DISTINCT validated_by) as unique_validators
            FROM employer_payment_receipts 
            WHERE validated_at BETWEEN :startDate AND :endDate
            """;
        
        Map<String, Object> params = Map.of(
            "startDate", startDate,
            "endDate", endDate
        );
        
        return namedParameterJdbcTemplate.queryForMap(sql, params);
    }
    
    /**
     * Get status distribution
     */
    public Map<String, Long> getStatusDistribution() {
        String sql = """
            SELECT status, COUNT(*) as count 
            FROM employer_payment_receipts 
            GROUP BY status 
            ORDER BY count DESC
            """;
        
        List<Map<String, Object>> results = namedParameterJdbcTemplate.queryForList(sql, Map.of());
        
        Map<String, Long> statusDistribution = new HashMap<>();
        for (Map<String, Object> row : results) {
            statusDistribution.put((String) row.get("status"), ((Number) row.get("count")).longValue());
        }
        
        return statusDistribution;
    }
    
    /**
     * Get receipts pending validation
     */
    public PageResult<EmployerPaymentReceipt> findPendingValidation(int page, int size) {
        String sql = BASE_SELECT + " WHERE status = 'PENDING_VALIDATION' ORDER BY created_at ASC";
        String countSql = BASE_COUNT + " WHERE status = 'PENDING_VALIDATION'";
        
        return queryForPage(sql, countSql, Map.of(), page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find receipts validated by user in date range
     */
    public PageResult<EmployerPaymentReceipt> findValidatedByUserInDateRange(String validatedBy, 
                                                                           LocalDateTime startDate, 
                                                                           LocalDateTime endDate,
                                                                           int page, int size) {
        String sql = BASE_SELECT + """ 
            WHERE validated_by = :validatedBy 
            AND validated_at BETWEEN :startDate AND :endDate 
            ORDER BY validated_at DESC
            """;
        String countSql = BASE_COUNT + """ 
            WHERE validated_by = :validatedBy 
            AND validated_at BETWEEN :startDate AND :endDate
            """;
        
        Map<String, Object> params = Map.of(
            "validatedBy", validatedBy,
            "startDate", startDate,
            "endDate", endDate
        );
        
        return queryForPage(sql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Map ResultSet to EmployerPaymentReceipt entity
     */
    private EmployerPaymentReceipt mapEmployerPaymentReceipt(ResultSet rs, int rowNum) throws SQLException {
        EmployerPaymentReceipt receipt = new EmployerPaymentReceipt();
        
        // Map all fields according to the actual entity structure
        receipt.setEmployerReceiptNumber(rs.getString("employer_receipt_number"));
        receipt.setWorkerReceiptNumber(rs.getString("worker_receipt_number"));
        receipt.setEmployerId(rs.getString("employer_id"));
        receipt.setToliId(rs.getString("toli_id"));
        receipt.setTransactionReference(rs.getString("transaction_reference"));
        receipt.setValidatedBy(rs.getString("validated_by"));
        receipt.setTotalRecords(rs.getInt("total_records"));
        receipt.setTotalAmount(rs.getBigDecimal("total_amount"));
        receipt.setStatus(rs.getString("status"));
        
        // Handle timestamps
        java.sql.Timestamp validatedAt = rs.getTimestamp("validated_at");
        if (validatedAt != null) {
            receipt.setValidatedAt(validatedAt.toLocalDateTime());
        }
        
        return receipt;
    }
}
