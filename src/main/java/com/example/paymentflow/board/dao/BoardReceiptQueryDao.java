package com.example.paymentflow.board.dao;

import com.example.paymentflow.board.entity.BoardReceipt;
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
public class BoardReceiptQueryDao {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private static final String BASE_SELECT = """
        SELECT br.id, br.board_id, br.board_reference, br.employer_reference, 
               br.employer_id, br.toli_id, br.amount, br.utr_number, br.status, 
               br.maker, br.checker, br.receipt_date
        FROM board_receipts br
        """;
    
    public List<BoardReceipt> findAll() {
        String sql = BASE_SELECT + " ORDER BY br.created_at DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper());
    }
    
    public Optional<BoardReceipt> findById(Long id) {
        String sql = BASE_SELECT + " WHERE br.id = ?";
        List<BoardReceipt> results = jdbcTemplate.query(sql, new BoardReceiptRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<BoardReceipt> findByStatus(String status) {
        String sql = BASE_SELECT + " WHERE br.status = ? ORDER BY br.created_at DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), status);
    }
    
    public List<BoardReceipt> findByBoardId(String boardId) {
        String sql = BASE_SELECT + " WHERE br.board_id = ? ORDER BY br.created_at DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), boardId);
    }
    
    public List<BoardReceipt> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = BASE_SELECT + " WHERE br.receipt_date BETWEEN ? AND ? ORDER BY br.receipt_date DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), 
                                 startDate.toLocalDate(), endDate.toLocalDate());
    }
    
    public List<BoardReceipt> findByStatusAndDateRange(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return findByStatusAndDateRange(status, startDate, endDate, "receipt_date", "desc");
    }
    
    public List<BoardReceipt> findByStatusAndDateRange(String status, LocalDateTime startDate, LocalDateTime endDate, String sortBy, String sortDir) {
        String sql;
        Object[] params;
        String orderBy = " ORDER BY " + sortBy + " " + sortDir.toUpperCase();
        if (status != null && !status.isEmpty()) {
            sql = BASE_SELECT + " WHERE br.status = ? AND br.receipt_date BETWEEN ? AND ?" + orderBy;
            params = new Object[]{status, startDate.toLocalDate(), endDate.toLocalDate()};
        } else {
            sql = BASE_SELECT + " WHERE br.receipt_date BETWEEN ? AND ?" + orderBy;
            params = new Object[]{startDate.toLocalDate(), endDate.toLocalDate()};
        }
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), params);
    }
    
    public List<BoardReceipt> findByMaker(String maker) {
        String sql = BASE_SELECT + " WHERE br.maker = ? ORDER BY br.receipt_date DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), maker);
    }
    
    public List<BoardReceipt> findByChecker(String checker) {
        String sql = BASE_SELECT + " WHERE br.checker = ? ORDER BY br.receipt_date DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), checker);
    }
    
    public Optional<BoardReceipt> findByUtrNumber(String utrNumber) {
        String sql = BASE_SELECT + " WHERE br.utr_number = ?";
        List<BoardReceipt> results = jdbcTemplate.query(sql, new BoardReceiptRowMapper(), utrNumber);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<BoardReceipt> findByEmployerId(String employerId) {
        String sql = BASE_SELECT + " WHERE br.employer_id = ? ORDER BY br.receipt_date DESC";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), employerId);
    }
    
    public Optional<BoardReceipt> findByBoardRef(String boardRef) {
        String sql = BASE_SELECT + " WHERE br.board_reference = ?";
        List<BoardReceipt> results = jdbcTemplate.query(sql, new BoardReceiptRowMapper(), boardRef);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public Optional<BoardReceipt> findByEmployerRef(String employerRef) {
        String sql = BASE_SELECT + " WHERE br.employer_reference = ?";
        List<BoardReceipt> results = jdbcTemplate.query(sql, new BoardReceiptRowMapper(), employerRef);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    // Summary queries
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM board_receipts WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }
    
    public double getTotalAmountByStatus(String status) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM board_receipts WHERE status = ?";
        Double total = jdbcTemplate.queryForObject(sql, Double.class, status);
        return total != null ? total : 0.0;
    }
    
    public double getTotalAmountByBoardId(String boardId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM board_receipts WHERE board_id = ?";
        Double total = jdbcTemplate.queryForObject(sql, Double.class, boardId);
        return total != null ? total : 0.0;
    }
    
    // Search functionality
    public List<BoardReceipt> searchByBoardIdOrRef(String searchTerm) {
        String sql = BASE_SELECT + """
            WHERE (LOWER(br.board_id) LIKE LOWER(?) 
                OR LOWER(br.board_reference) LIKE LOWER(?))
            ORDER BY br.receipt_date DESC
            """;
        String pattern = "%" + searchTerm + "%";
        return jdbcTemplate.query(sql, new BoardReceiptRowMapper(), pattern, pattern);
    }
    
    private static class BoardReceiptRowMapper implements RowMapper<BoardReceipt> {
        @Override
        public BoardReceipt mapRow(ResultSet rs, int rowNum) throws SQLException {
            BoardReceipt receipt = new BoardReceipt();
            Long id = rs.getObject("id", Long.class);
            if (id != null) {
                receipt.setId(id);
            }
            receipt.setBoardId(rs.getString("board_id"));
            receipt.setBoardRef(rs.getString("board_reference"));
            receipt.setEmployerRef(rs.getString("employer_reference"));
            receipt.setEmployerId(rs.getString("employer_id"));
            receipt.setToliId(rs.getString("toli_id"));
            receipt.setAmount(rs.getBigDecimal("amount"));
            receipt.setUtrNumber(rs.getString("utr_number"));
            receipt.setStatus(rs.getString("status"));
            receipt.setMaker(rs.getString("maker"));
            receipt.setChecker(rs.getString("checker"));
            
            var receiptDate = rs.getDate("receipt_date");
            if (receiptDate != null) {
                receipt.setDate(receiptDate.toLocalDate());
            }
            
            return receipt;
        }
    }
}
