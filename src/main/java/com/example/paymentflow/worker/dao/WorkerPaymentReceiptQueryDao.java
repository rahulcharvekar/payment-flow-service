package com.example.paymentflow.worker.dao;

import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class WorkerPaymentReceiptQueryDao {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private static final String BASE_SELECT = """
        SELECT wpr.id, wpr.receipt_number, wpr.employer_id, wpr.toli_id,
               wpr.created_at, wpr.total_records, wpr.total_amount, wpr.status
        FROM worker_payment_receipts wpr
        """;
    
    public List<WorkerPaymentReceipt> findAll() {
        String sql = BASE_SELECT + " ORDER BY wpr.created_at DESC";
        return jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper());
    }
    
    public Optional<WorkerPaymentReceipt> findById(Long id) {
        String sql = BASE_SELECT + " WHERE wpr.id = ?";
        List<WorkerPaymentReceipt> results = jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<WorkerPaymentReceipt> findByStatus(String status) {
        String sql = BASE_SELECT + " WHERE wpr.status = ? ORDER BY wpr.created_at DESC";
        return jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), status);
    }
    
    public Optional<WorkerPaymentReceipt> findByReceiptNumber(String receiptNumber) {
        String sql = BASE_SELECT + " WHERE wpr.receipt_number = ?";
        List<WorkerPaymentReceipt> results = jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), receiptNumber);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<WorkerPaymentReceipt> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = BASE_SELECT + " WHERE wpr.created_at BETWEEN ? AND ? ORDER BY wpr.created_at DESC";
        return jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), startDate, endDate);
    }
    
    public List<WorkerPaymentReceipt> findByStatusAndDateRange(String status, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = BASE_SELECT + " WHERE wpr.status = ? AND wpr.created_at BETWEEN ? AND ? ORDER BY wpr.created_at DESC";
        return jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), status, startDate, endDate);
    }
    
    public List<WorkerPaymentReceipt> findByEmployerId(String employerId) {
        String sql = BASE_SELECT + " WHERE wpr.employer_id = ? ORDER BY wpr.created_at DESC";
        return jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), employerId);
    }
    
    public List<WorkerPaymentReceipt> findByToliId(String toliId) {
        String sql = BASE_SELECT + " WHERE wpr.toli_id = ? ORDER BY wpr.created_at DESC";
        return jdbcTemplate.query(sql, new WorkerPaymentReceiptRowMapper(), toliId);
    }
    
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM worker_payment_receipts WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }
    
    public double getTotalAmountByStatus(String status) {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM worker_payment_receipts WHERE status = ?";
        Double total = jdbcTemplate.queryForObject(sql, Double.class, status);
        return total != null ? total : 0.0;
    }
    
    private static class WorkerPaymentReceiptRowMapper implements RowMapper<WorkerPaymentReceipt> {
        @Override
        public WorkerPaymentReceipt mapRow(ResultSet rs, int rowNum) throws SQLException {
            WorkerPaymentReceipt receipt = new WorkerPaymentReceipt();
            // Note: WorkerPaymentReceipt doesn't have setId method, id is auto-generated
            receipt.setReceiptNumber(rs.getString("receipt_number"));
            receipt.setEmployerId(rs.getString("employer_id"));
            receipt.setToliId(rs.getString("toli_id"));
            
            // Handle timestamps
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                receipt.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            receipt.setTotalRecords(rs.getInt("total_records"));
            receipt.setTotalAmount(rs.getBigDecimal("total_amount"));
            receipt.setStatus(rs.getString("status"));
            
            return receipt;
        }
    }
}
