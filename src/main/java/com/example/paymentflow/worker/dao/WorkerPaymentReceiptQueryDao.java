package com.example.paymentflow.worker.dao;

import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import com.shared.common.dao.BaseQueryDao;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class WorkerPaymentReceiptQueryDao extends BaseQueryDao {

    private static final String BASE_SELECT = """
        SELECT wpr.id,
               wpr.receipt_number,
               wpr.employer_id,
               wpr.toli_id,
               wpr.created_at,
               wpr.total_records,
               wpr.total_amount,
               wpr.status
        FROM worker_payment_receipts wpr
        """;

    private static final String BASE_COUNT = "SELECT COUNT(*) FROM worker_payment_receipts wpr";

    public PageResult<WorkerPaymentReceipt> findAll(int page, int size) {
        String baseSql = BASE_SELECT + " ORDER BY wpr.created_at DESC";
        return queryForPage(baseSql, BASE_COUNT, Collections.emptyMap(), page, size, this::mapReceipt);
    }

    public PageResult<WorkerPaymentReceipt> findByStatus(String status, int page, int size) {
        Map<String, Object> params = Map.of("status", status);
        String baseSql = BASE_SELECT + " WHERE wpr.status = :status ORDER BY wpr.created_at DESC";
        String countSql = BASE_COUNT + " WHERE wpr.status = :status";
        return queryForPage(baseSql, countSql, params, page, size, this::mapReceipt);
    }

    public PageResult<WorkerPaymentReceipt> findByDateRange(LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        Map<String, Object> params = Map.of(
                "startDate", startDate,
                "endDate", endDate
        );
        String baseSql = BASE_SELECT + " WHERE wpr.created_at BETWEEN :startDate AND :endDate ORDER BY wpr.created_at DESC";
        String countSql = BASE_COUNT + " WHERE wpr.created_at BETWEEN :startDate AND :endDate";
        return queryForPage(baseSql, countSql, params, page, size, this::mapReceipt);
    }

    public PageResult<WorkerPaymentReceipt> findByStatusAndDateRange(String status, LocalDateTime startDate, LocalDateTime endDate,
                                                                     int page, int size) {
        Map<String, Object> params = Map.of(
                "status", status,
                "startDate", startDate,
                "endDate", endDate
        );
        String baseSql = BASE_SELECT + " WHERE wpr.status = :status AND wpr.created_at BETWEEN :startDate AND :endDate ORDER BY wpr.created_at DESC";
        String countSql = BASE_COUNT + " WHERE wpr.status = :status AND wpr.created_at BETWEEN :startDate AND :endDate";
        return queryForPage(baseSql, countSql, params, page, size, this::mapReceipt);
    }

    public Optional<WorkerPaymentReceipt> findById(Long id) {
        String sql = BASE_SELECT + " WHERE wpr.id = :id";
        return queryForObject(sql, Map.of("id", id), this::mapReceipt);
    }

    public Optional<WorkerPaymentReceipt> findByReceiptNumber(String receiptNumber) {
        String sql = BASE_SELECT + " WHERE wpr.receipt_number = :receiptNumber";
        return queryForObject(sql, Map.of("receiptNumber", receiptNumber), this::mapReceipt);
    }

    public List<WorkerPaymentReceipt> findByEmployerId(String employerId) {
        String sql = BASE_SELECT + " WHERE wpr.employer_id = :employerId ORDER BY wpr.created_at DESC";
        return queryForList(sql, Map.of("employerId", employerId), this::mapReceipt);
    }

    public List<WorkerPaymentReceipt> findByToliId(String toliId) {
        String sql = BASE_SELECT + " WHERE wpr.toli_id = :toliId ORDER BY wpr.created_at DESC";
        return queryForList(sql, Map.of("toliId", toliId), this::mapReceipt);
    }

    public int countByStatus(String status) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_payment_receipts WHERE status = :status",
                Map.of("status", status),
                Integer.class);
        return count != null ? count : 0;
    }

    public double getTotalAmountByStatus(String status) {
        Double total = namedParameterJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM worker_payment_receipts WHERE status = :status",
                Map.of("status", status),
                Double.class);
        return total != null ? total : 0.0;
    }

    private WorkerPaymentReceipt mapReceipt(ResultSet rs, int rowNum) throws SQLException {
        WorkerPaymentReceipt receipt = new WorkerPaymentReceipt();
        Long id = rs.getObject("id", Long.class);
        if (id != null) {
            receipt.setId(id);
        }
        receipt.setReceiptNumber(rs.getString("receipt_number"));
        receipt.setEmployerId(rs.getString("employer_id"));
        receipt.setToliId(rs.getString("toli_id"));
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
