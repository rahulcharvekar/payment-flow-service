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
import java.util.Set;

@Repository
public class BoardReceiptQueryDao {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
            "id",
            "board_id",
            "board_reference",
            "employer_reference",
            "employer_id",
            "toli_id",
            "amount",
            "utr_number",
            "status",
            "maker",
            "checker",
            "receipt_date"
    );

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
        OrderClause orderClause = sanitizeOrder(sortBy, sortDir);
        QuerySpec querySpec = buildRangeQuery(status, startDate, endDate, orderClause, null, null);
        return jdbcTemplate.query(querySpec.sql(), new BoardReceiptRowMapper(), querySpec.params());
    }

    public PageResult findByStatusAndDateRange(String status,
                                               LocalDateTime startDate,
                                               LocalDateTime endDate,
                                               int page,
                                               int size,
                                               String sortBy,
                                               String sortDir) {
        OrderClause orderClause = sanitizeOrder(sortBy, sortDir);
        int pageSafe = Math.max(page, 0);
        int sizeSafe = Math.max(size, 1);
        QuerySpec querySpec = buildRangeQuery(status, startDate, endDate, orderClause, sizeSafe, pageSafe * sizeSafe);
        List<BoardReceipt> content = jdbcTemplate.query(querySpec.sql(), new BoardReceiptRowMapper(), querySpec.params());
        Long total = countRange(status, startDate, endDate);
        return new PageResult(content, total != null ? total : 0L);
    }

    private QuerySpec buildRangeQuery(String status,
                                      LocalDateTime startDate,
                                      LocalDateTime endDate,
                                      OrderClause orderClause,
                                      Integer limit,
                                      Integer offset) {
        boolean hasStatus = status != null && !status.isEmpty();
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        if (hasStatus) {
            sql.append(" WHERE br.status = ? AND br.receipt_date BETWEEN ? AND ?");
        } else {
            sql.append(" WHERE br.receipt_date BETWEEN ? AND ?");
        }
        sql.append(orderClause.sql());
        List<Object> params = new java.util.ArrayList<>();
        if (hasStatus) {
            params.add(status);
        }
        params.add(startDate.toLocalDate());
        params.add(endDate.toLocalDate());
        if (limit != null && offset != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }
        return new QuerySpec(sql.toString(), params.toArray());
    }

    private Long countRange(String status, LocalDateTime startDate, LocalDateTime endDate) {
        boolean hasStatus = status != null && !status.isEmpty();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM board_receipts br WHERE ");
        if (hasStatus) {
            sql.append("br.status = ? AND br.receipt_date BETWEEN ? AND ?");
        } else {
            sql.append("br.receipt_date BETWEEN ? AND ?");
        }
        List<Object> params = new java.util.ArrayList<>();
        if (hasStatus) {
            params.add(status);
        }
        params.add(startDate.toLocalDate());
        params.add(endDate.toLocalDate());
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private OrderClause sanitizeOrder(String sortBy, String sortDir) {
        String sanitizedColumn = ALLOWED_SORT_COLUMNS.contains(sortBy) ? sortBy : "receipt_date";
        String sanitizedDirection = "DESC";
        if ("ASC".equalsIgnoreCase(sortDir)) {
            sanitizedDirection = "ASC";
        }
        return new OrderClause(" ORDER BY br." + sanitizedColumn + " " + sanitizedDirection);
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

    public record PageResult(List<BoardReceipt> content, long totalElements) {
    }

    private record OrderClause(String sql) {
    }

    private record QuerySpec(String sql, Object[] params) {
    }
}
