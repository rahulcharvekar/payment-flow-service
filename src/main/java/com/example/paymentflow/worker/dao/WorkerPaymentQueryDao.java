package com.example.paymentflow.worker.dao;

import com.shared.common.dao.BaseQueryDao;
import com.example.paymentflow.worker.entity.WorkerPayment;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO for Worker Payment read operations using custom queries.
 * All read logic is centralized here for better control and debugging.
 */
@Repository
public class WorkerPaymentQueryDao extends BaseQueryDao {
    
    // Base SQL for worker payments
    private static final String BASE_SELECT = """
        SELECT id, worker_reference, registration_id, worker_name, employer_id, toli_id, toli, 
               aadhar, pan, bank_account, payment_amount, request_reference_number, 
               receipt_number, status, file_id, uploaded_file_ref, created_at
        FROM worker_payments
        """;
    
    private static final String BASE_COUNT = "SELECT COUNT(*) FROM worker_payments";
    
    /**
     * Find worker payment by ID
     */
    public Optional<WorkerPayment> findById(Long id) {
        String sql = BASE_SELECT + " WHERE id = :id";
        Map<String, Object> params = Map.of("id", id);
        return queryForObject(sql, params, this::mapWorkerPayment);
    }
    
    /**
     * Find worker payments with filters and pagination
     */
    public PageResult<WorkerPayment> findWithFilters(String status, String receiptNumber, 
                                                    String fileId, LocalDateTime startDate, 
                                                    LocalDateTime endDate, int page, int size) {
        
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        
        // Build dynamic where clause
        if (status != null && !status.trim().isEmpty()) {
            whereClause.append(" AND status = :status");
            params.put("status", status);
        }
        
        if (receiptNumber != null && !receiptNumber.trim().isEmpty()) {
            whereClause.append(" AND receipt_number = :receiptNumber");
            params.put("receiptNumber", receiptNumber);
        }
        
        if (fileId != null && !fileId.trim().isEmpty()) {
            whereClause.append(" AND file_id = :fileId");
            params.put("fileId", fileId);
        }
        
        if (startDate != null) {
            whereClause.append(" AND created_at >= :startDate");
            params.put("startDate", startDate);
        }
        
        if (endDate != null) {
            whereClause.append(" AND created_at <= :endDate");
            params.put("endDate", endDate);
        }
        
        String baseSql = BASE_SELECT + whereClause + " ORDER BY created_at DESC";
        String countSql = BASE_COUNT + whereClause;
        
        return queryForPage(baseSql, countSql, params, page, size, this::mapWorkerPayment);
    }
    
    /**
     * Find by status with pagination
     */
    public PageResult<WorkerPayment> findByStatus(String status, int page, int size) {
        String sql = BASE_SELECT + " WHERE status = :status ORDER BY created_at DESC";
        String countSql = BASE_COUNT + " WHERE status = :status";
        Map<String, Object> params = Map.of("status", status);
        
        return queryForPage(sql, countSql, params, page, size, this::mapWorkerPayment);
    }
    
    /**
     * Find by receipt number
     */
    public List<WorkerPayment> findByReceiptNumber(String receiptNumber) {
        String sql = BASE_SELECT + " WHERE receipt_number = :receiptNumber ORDER BY created_at DESC";
        Map<String, Object> params = Map.of("receiptNumber", receiptNumber);
        return queryForList(sql, params, this::mapWorkerPayment);
    }
    
    /**
     * Find by file ID with pagination
     */
    public PageResult<WorkerPayment> findByFileId(String fileId, int page, int size) {
        String sql = BASE_SELECT + " WHERE file_id = :fileId ORDER BY created_at DESC";
        String countSql = BASE_COUNT + " WHERE file_id = :fileId";
        Map<String, Object> params = Map.of("fileId", fileId);
        
        return queryForPage(sql, countSql, params, page, size, this::mapWorkerPayment);
    }
    
    /**
     * Get status counts for a file
     */
    public Map<String, Long> getStatusCountsByFileId(String fileId) {
        String sql = """
            SELECT status, COUNT(*) as count 
            FROM worker_payments 
            WHERE file_id = :fileId 
            GROUP BY status
            """;
        
        Map<String, Object> params = Map.of("fileId", fileId);
        List<Map<String, Object>> results = namedParameterJdbcTemplate.queryForList(sql, params);
        
        Map<String, Long> statusCounts = new HashMap<>();
        for (Map<String, Object> row : results) {
            statusCounts.put((String) row.get("status"), ((Number) row.get("count")).longValue());
        }
        
        return statusCounts;
    }
    
    /**
     * Find payments by request reference number prefix
     */
    public List<WorkerPayment> findByRequestReferenceNumberStartingWith(String prefix) {
        String sql = BASE_SELECT + " WHERE request_reference_number LIKE :prefix ORDER BY created_at DESC";
        Map<String, Object> params = Map.of("prefix", prefix + "%");
        return queryForList(sql, params, this::mapWorkerPayment);
    }
    
    /**
     * Find payments by date range
     */
    public PageResult<WorkerPayment> findByDateRange(LocalDateTime startDate, LocalDateTime endDate, 
                                                    int page, int size) {
        String sql = BASE_SELECT + " WHERE created_at BETWEEN :startDate AND :endDate ORDER BY created_at DESC";
        String countSql = BASE_COUNT + " WHERE created_at BETWEEN :startDate AND :endDate";
        
        Map<String, Object> params = Map.of(
            "startDate", startDate,
            "endDate", endDate
        );
        
        return queryForPage(sql, countSql, params, page, size, this::mapWorkerPayment);
    }
    
    /**
     * Get summary statistics
     */
    public Map<String, Object> getPaymentSummary(String fileId) {
        String sql = """
            SELECT 
                COUNT(*) as total_payments,
                SUM(amount) as total_amount,
                AVG(amount) as average_amount,
                MIN(amount) as min_amount,
                MAX(amount) as max_amount,
                COUNT(DISTINCT status) as status_count
            FROM worker_payments 
            WHERE file_id = :fileId
            """;
        
        Map<String, Object> params = Map.of("fileId", fileId);
        return namedParameterJdbcTemplate.queryForMap(sql, params);
    }
    
    /**
     * Map ResultSet to WorkerPayment entity
     */
    private WorkerPayment mapWorkerPayment(ResultSet rs, int rowNum) throws SQLException {
        WorkerPayment payment = new WorkerPayment();
        
        // Map all fields according to the entity structure
        payment.setWorkerRef(rs.getString("worker_reference"));
        payment.setRegId(rs.getString("registration_id"));
        payment.setName(rs.getString("worker_name"));
        payment.setEmployerId(rs.getString("employer_id"));
        payment.setToliId(rs.getString("toli_id"));
        payment.setToli(rs.getString("toli"));
        payment.setAadhar(rs.getString("aadhar"));
        payment.setPan(rs.getString("pan"));
        payment.setBankAccount(rs.getString("bank_account"));
        payment.setPaymentAmount(rs.getBigDecimal("payment_amount"));
        payment.setRequestReferenceNumber(rs.getString("request_reference_number"));
        payment.setReceiptNumber(rs.getString("receipt_number"));
        payment.setStatus(rs.getString("status"));
        payment.setFileId(rs.getString("file_id"));
        payment.setUploadedFileRef(rs.getString("uploaded_file_ref"));
        
        // Handle timestamps
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            payment.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return payment;
    }
}
