package com.example.paymentflow.employer.dao;

import com.example.paymentflow.employer.entity.EmployerPaymentReceipt;
import com.example.paymentflow.common.sql.SqlTemplateLoader;
import com.shared.common.dao.BaseQueryDao;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DAO for Employer Payment Receipt read operations using custom queries.
 * Centralizes all read logic for better control and debugging.
 */
@Repository
public class EmployerPaymentReceiptQueryDao extends BaseQueryDao {
    
    private static final String BASE_SELECT_TEMPLATE = "sql/employer/employer_payment_receipts_base_select.sql";
    private static final String BASE_COUNT_TEMPLATE = "sql/employer/employer_payment_receipts_count.sql";
    private static final String PENDING_VALIDATION_TEMPLATE = "sql/employer/employer_payment_receipts_pending_validation.sql";
    private static final String PENDING_VALIDATION_COUNT_TEMPLATE = "sql/employer/employer_payment_receipts_pending_validation_count.sql";

    private final DSLContext dsl;
    private final SqlTemplateLoader sqlTemplates;

    public EmployerPaymentReceiptQueryDao(DSLContext dsl, SqlTemplateLoader sqlTemplates) {
        this.dsl = dsl;
        this.sqlTemplates = sqlTemplates;
    }
    
    private String baseSelect() {
        return sqlTemplates.load(BASE_SELECT_TEMPLATE);
    }

    private String baseCount() {
        return sqlTemplates.load(BASE_COUNT_TEMPLATE);
    }

    /**
     * Find by ID with custom query
     */
    public Optional<EmployerPaymentReceipt> findById(Long id) {
        String sql = baseSelect() + " WHERE id = :id";
        Map<String, Object> params = Map.of("id", id);
        return queryForObject(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by employer receipt number
     */
    public Optional<EmployerPaymentReceipt> findByEmployerReceiptNumber(String employerReceiptNumber) {
        String sql = baseSelect() + " WHERE employer_receipt_number = :empRef";
        Map<String, Object> params = Map.of("empRef", employerReceiptNumber);
        return queryForObject(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by worker receipt number
     */
    public Optional<EmployerPaymentReceipt> findByWorkerReceiptNumber(String workerReceiptNumber) {
        String sql = baseSelect() + " WHERE worker_receipt_number = :workerRef";
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
        
        String baseSql = baseSelect() + whereClause + " ORDER BY validated_at DESC";
        String countSql = baseCount() + whereClause;
        
        return queryForPage(baseSql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by status with pagination
     */
    public PageResult<EmployerPaymentReceipt> findByStatus(String status, int page, int size) {
        String sql = baseSelect() + " WHERE status = :status ORDER BY validated_at DESC";
        String countSql = baseCount() + " WHERE status = :status";
        Map<String, Object> params = Map.of("status", status);
        
        return queryForPage(sql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by validator with pagination
     */
    public PageResult<EmployerPaymentReceipt> findByValidatedBy(String validatedBy, int page, int size) {
        String sql = baseSelect() + " WHERE validated_by = :validatedBy ORDER BY validated_at DESC";
        String countSql = baseCount() + " WHERE validated_by = :validatedBy";
        Map<String, Object> params = Map.of("validatedBy", validatedBy);
        
        return queryForPage(sql, countSql, params, page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by transaction reference for MT940 reconciliation
     */
    public List<EmployerPaymentReceipt> findByTransactionReference(String transactionReference) {
        String sql = baseSelect() + " WHERE transaction_reference = :txnRef ORDER BY validated_at DESC";
        Map<String, Object> params = Map.of("txnRef", transactionReference);
        return queryForList(sql, params, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find by date range with pagination
     */
    public PageResult<EmployerPaymentReceipt> findByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                            int page, int size) {
        String sql = baseSelect() + " WHERE validated_at BETWEEN :startDate AND :endDate ORDER BY validated_at DESC";
        String countSql = baseCount() + " WHERE validated_at BETWEEN :startDate AND :endDate";
        
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
        String sql = sqlTemplates.load("sql/employer/employer_validation_statistics.sql");
        Map<String, Object> result = dsl.resultQuery(sql, startDate, endDate).fetchOneMap();
        return result != null ? result : Map.of();
    }
    
    /**
     * Get status distribution
     */
    public Map<String, Long> getStatusDistribution() {
        String sql = sqlTemplates.load("sql/employer/employer_status_distribution.sql");
        return dsl.resultQuery(sql)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get("status", String.class),
                        record -> {
                            Number count = record.get("count", Number.class);
                            return count != null ? count.longValue() : 0L;
                        },
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ));
    }
    
    /**
     * Get receipts pending validation
     */
    public PageResult<EmployerPaymentReceipt> findPendingValidation(int page, int size) {
        String sql = sqlTemplates.load(PENDING_VALIDATION_TEMPLATE);
        String countSql = sqlTemplates.load(PENDING_VALIDATION_COUNT_TEMPLATE);
        
        return queryForPage(sql, countSql, Map.of(), page, size, this::mapEmployerPaymentReceipt);
    }
    
    /**
     * Find receipts validated by user in date range
     */
    public PageResult<EmployerPaymentReceipt> findValidatedByUserInDateRange(String validatedBy, 
                                                                           LocalDateTime startDate, 
                                                                           LocalDateTime endDate,
                                                                           int page, int size) {
        String sql = baseSelect() + """ 
            WHERE validated_by = :validatedBy 
            AND validated_at BETWEEN :startDate AND :endDate 
            ORDER BY validated_at DESC
            """;
        String countSql = baseCount() + """ 
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
