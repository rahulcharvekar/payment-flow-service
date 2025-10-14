package com.example.paymentflow.board.service;

import com.example.paymentflow.board.entity.BoardReceipt;

import com.example.paymentflow.employer.entity.EmployerPaymentReceipt;
import com.shared.exception.ResourceNotFoundException;
import com.example.paymentflow.board.dao.BoardReceiptRepository;
import com.example.paymentflow.board.dao.BoardReceiptQueryDao;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class BoardReceiptService {
    /**
     * Cursor-based pagination for board receipts (stub implementation).
     * @param status Receipt status filter
     * @param start Start date
     * @param end End date
     * @param nextPageToken Opaque cursor for next page
     * @return Page of BoardReceipt
     */
    @Transactional(readOnly = true)
    public Page<BoardReceipt> findByStatusAndDateRangeWithToken(String status, java.time.LocalDateTime start, java.time.LocalDateTime end, String nextPageToken) {
        // TODO: Implement real cursor-based pagination logic
        // For now, fallback to first page of classic pagination
        Pageable pageable = PageRequest.of(0, 20, Sort.by("receipt_date").descending());
        return findByStatusAndDateRange(status, start, end, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<BoardReceipt> findByStatusAndDateRangeWithToken(String status, java.time.LocalDateTime start, java.time.LocalDateTime end, String nextPageToken, String sortBy, String sortDir) {
        // TODO: Implement real cursor-based pagination logic
        // For now, fallback to first page of classic pagination
        Sort sort = Sort.by("receipt_date").descending();
        if ("asc".equalsIgnoreCase(sortDir)) {
            sort = Sort.by("receipt_date").ascending();
        }
        Pageable pageable = PageRequest.of(0, 20, sort);
        return findByStatusAndDateRange(status, start, end, pageable, sortBy, sortDir);
    }
    @Transactional(readOnly = true)
    public Page<BoardReceipt> findByStatusAndDateRange(String status, java.time.LocalDateTime start, java.time.LocalDateTime end, Pageable pageable) {
        log.info("Finding board receipts with status: {} and date range: {} to {} (paginated)", status, start, end);
        String upperStatus = status != null && !status.trim().isEmpty() ? status.trim().toUpperCase() : null;
        if (upperStatus != null && !upperStatus.equals("PENDING") && !upperStatus.equals("VERIFIED") &&
            !upperStatus.equals("REJECTED") && !upperStatus.equals("PROCESSED")) {
            throw new RuntimeException("Invalid status: " + status + ". Valid values are: PENDING, VERIFIED, REJECTED, PROCESSED");
        }
        List<BoardReceipt> allResults = queryDao.findByStatusAndDateRange(upperStatus, start, end);
        int startIdx = (int) pageable.getOffset();
        int endIdx = Math.min(startIdx + pageable.getPageSize(), allResults.size());
        List<BoardReceipt> pageContent = (startIdx > endIdx) ? java.util.Collections.emptyList() : allResults.subList(startIdx, endIdx);
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allResults.size());
    }
    
    @Transactional(readOnly = true)
    public Page<BoardReceipt> findByStatusAndDateRange(String status, java.time.LocalDateTime start, java.time.LocalDateTime end, Pageable pageable, String sortBy, String sortDir) {
        log.info("Finding board receipts with status: {} and date range: {} to {} (paginated) sortBy: {} sortDir: {}", status, start, end, sortBy, sortDir);
        String upperStatus = status != null && !status.trim().isEmpty() ? status.trim().toUpperCase() : null;
        if (upperStatus != null && !upperStatus.equals("PENDING") && !upperStatus.equals("VERIFIED") &&
            !upperStatus.equals("REJECTED") && !upperStatus.equals("PROCESSED")) {
            throw new RuntimeException("Invalid status: " + status + ". Valid values are: PENDING, VERIFIED, REJECTED, PROCESSED");
        }
        // Allowed sort columns for security
        java.util.Set<String> allowedSortColumns = java.util.Set.of("id", "board_id", "board_reference", "employer_reference", "employer_id", "toli_id", "amount", "utr_number", "status", "maker", "checker", "receipt_date");
        String dbSortBy = allowedSortColumns.contains(sortBy) ? sortBy : "receipt_date";
        List<BoardReceipt> allResults = queryDao.findByStatusAndDateRange(upperStatus, start, end, dbSortBy, sortDir);
        int startIdx = (int) pageable.getOffset();
        int endIdx = Math.min(startIdx + pageable.getPageSize(), allResults.size());
        List<BoardReceipt> pageContent = (startIdx > endIdx) ? java.util.Collections.emptyList() : allResults.subList(startIdx, endIdx);
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allResults.size());
    }

    private static final Logger log = LoggerFactoryProvider.getLogger(BoardReceiptService.class);

    private final BoardReceiptRepository repository;
    private final BoardReceiptQueryDao queryDao;

    public BoardReceiptService(BoardReceiptRepository repository, BoardReceiptQueryDao queryDao) {
        this.repository = repository;
        this.queryDao = queryDao;
    }

    public BoardReceipt create(BoardReceipt boardReceipt) {
        log.info("Persisting board receipt for boardRef={}", boardReceipt.getBoardRef());
        BoardReceipt saved = repository.save(boardReceipt);
        log.info("Persisted board receipt id={}", saved.getId());
        return saved;
    }

    // READ OPERATIONS - Using Query DAO
    @Transactional(readOnly = true)
    public List<BoardReceipt> findAll() {
        log.info("Retrieving all board receipts using query DAO");
        return queryDao.findAll();
    }

    @Transactional(readOnly = true)
    public BoardReceipt findById(Long id) {
        log.info("Retrieving board receipt id={} using query DAO", id);
        return queryDao.findById(id)
                .orElseThrow(() -> {
                    log.warn("Board receipt id={} not found", id);
                    return new ResourceNotFoundException("Board receipt not found for id=" + id);
                });
    }

    public BoardReceipt update(Long id, BoardReceipt updated) {
        log.info("Updating board receipt id={}", id);
        BoardReceipt existing = findById(id);
        existing.setBoardRef(updated.getBoardRef());
        existing.setEmployerRef(updated.getEmployerRef());
        existing.setAmount(updated.getAmount());
        existing.setUtrNumber(updated.getUtrNumber());
        existing.setStatus(updated.getStatus());
        existing.setMaker(updated.getMaker());
        existing.setChecker(updated.getChecker());
        existing.setDate(updated.getDate());
        BoardReceipt saved = repository.save(existing);
        log.info("Updated board receipt id={}", saved.getId());
        return saved;
    }

    public void delete(Long id) {
        log.info("Deleting board receipt id={}", id);
        if (!repository.existsById(id)) {
            log.warn("Cannot delete board receipt id={} because it does not exist", id);
            throw new ResourceNotFoundException("Board receipt not found for id=" + id);
        }
        repository.deleteById(id);
        log.info("Deleted board receipt id={}", id);
    }

    public BoardReceipt createFromEmployerReceipt(EmployerPaymentReceipt employerReceipt, String maker) {
        log.info("Creating board receipt from employer receipt: {}", employerReceipt.getEmployerReceiptNumber());
        
        // Generate board reference number
        String boardRef = generateBoardReceiptNumber();
        
        // Create board receipt
        BoardReceipt boardReceipt = new BoardReceipt();
        boardReceipt.setBoardRef(boardRef);
        boardReceipt.setBoardId("BOARD_" + boardRef); // Generate board_id from board_reference
        boardReceipt.setEmployerRef(employerReceipt.getEmployerReceiptNumber());
        boardReceipt.setEmployerId(employerReceipt.getEmployerId());
        boardReceipt.setToliId(employerReceipt.getToliId());
        boardReceipt.setAmount(employerReceipt.getTotalAmount());
        boardReceipt.setUtrNumber(""); // Will be filled when processing
        boardReceipt.setStatus("PENDING");
        boardReceipt.setMaker(maker);
        boardReceipt.setDate(LocalDate.now());
        
        BoardReceipt savedReceipt = repository.save(boardReceipt);
        
        log.info("Created board receipt {} from employer receipt {} with status PENDING", 
                savedReceipt.getBoardRef(), employerReceipt.getEmployerReceiptNumber());
        
        return savedReceipt;
    }

    public Map<String, Object> getAllBoardReceiptsWithFilters(int page, int size, String status, 
                                                              String singleDate, String startDate, String endDate) {
        log.info("Fetching board receipts with filters - page: {}, size: {}, status: {}, singleDate: {}, startDate: {}, endDate: {}", 
                page, size, status, singleDate, startDate, endDate);
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
            Page<BoardReceipt> receiptsPage;
            
            // Parse dates if provided
            LocalDate startLocalDate = null;
            LocalDate endLocalDate = null;
            
            if (singleDate != null && !singleDate.trim().isEmpty()) {
                LocalDate date = LocalDate.parse(singleDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                startLocalDate = date;
                endLocalDate = date;
            } else if (startDate != null && endDate != null && 
                      !startDate.trim().isEmpty() && !endDate.trim().isEmpty()) {
                startLocalDate = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                endLocalDate = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            
            // Validate status if provided
            String statusValue = null;
            if (status != null && !status.trim().isEmpty()) {
                String upperStatus = status.toUpperCase();
                if (!upperStatus.equals("PENDING") && !upperStatus.equals("VERIFIED") && 
                    !upperStatus.equals("REJECTED") && !upperStatus.equals("PROCESSED")) {
                    throw new RuntimeException("Invalid status: " + status + ". Valid values are: PENDING, VERIFIED, REJECTED, PROCESSED");
                }
                statusValue = upperStatus;
            }
            
            // Apply filters - using simple pagination for now
            List<BoardReceipt> allResults;
            if (statusValue != null && startLocalDate != null && endLocalDate != null) {
                allResults = queryDao.findByStatusAndDateRange(statusValue, startLocalDate.atStartOfDay(), endLocalDate.atTime(23, 59, 59));
            } else if (statusValue != null) {
                allResults = queryDao.findByStatus(statusValue);
            } else if (startLocalDate != null && endLocalDate != null) {
                allResults = queryDao.findByDateRange(startLocalDate.atStartOfDay(), endLocalDate.atTime(23, 59, 59));
            } else {
                allResults = queryDao.findAll();
            }
            receiptsPage = new org.springframework.data.domain.PageImpl<>(allResults, pageable, allResults.size());
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("content", receiptsPage.getContent());
            response.put("totalElements", receiptsPage.getTotalElements());
            response.put("totalPages", receiptsPage.getTotalPages());
            response.put("currentPage", receiptsPage.getNumber());
            response.put("pageSize", receiptsPage.getSize());
            response.put("hasNext", receiptsPage.hasNext());
            response.put("hasPrevious", receiptsPage.hasPrevious());
            
            log.info("Found {} board receipts (page {}/{})", 
                    receiptsPage.getTotalElements(), receiptsPage.getNumber(), receiptsPage.getTotalPages());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error fetching board receipts with filters", e);
            throw new RuntimeException("Failed to fetch board receipts: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<BoardReceipt> findByStatus(String status) {
        log.info("Finding board receipts with status: {}", status);
        String upperStatus = status.toUpperCase();
        if (!upperStatus.equals("PENDING") && !upperStatus.equals("VERIFIED") && 
            !upperStatus.equals("REJECTED") && !upperStatus.equals("PROCESSED")) {
            throw new RuntimeException("Invalid status: " + status + ". Valid values are: PENDING, VERIFIED, REJECTED, PROCESSED");
        }
        return queryDao.findByStatus(upperStatus);
    }
    
    @Transactional(readOnly = true)
    public Optional<BoardReceipt> findByBoardRef(String boardRef) {
        log.info("Finding board receipt for board ref: {}", boardRef);
        return queryDao.findByBoardRef(boardRef);
    }
    
    @Transactional(readOnly = true)
    public Optional<BoardReceipt> findByEmployerRef(String employerRef) {
        log.info("Finding board receipt for employer ref: {}", employerRef);
        return queryDao.findByEmployerRef(employerRef);
    }

    public BoardReceipt processBoardReceipt(String boardRef, String utrNumber, String checker) {
        log.info("Processing board receipt: {} with UTR: {} by checker: {}", boardRef, utrNumber, checker);
        
        // Find the board receipt
        Optional<BoardReceipt> boardReceiptOpt = queryDao.findByBoardRef(boardRef);
        if (boardReceiptOpt.isEmpty()) {
            throw new RuntimeException("Board receipt not found: " + boardRef);
        }
        
        BoardReceipt boardReceipt = boardReceiptOpt.get();
        
        // Check if already processed
        if (!"PENDING".equals(boardReceipt.getStatus())) {
            throw new RuntimeException("Board receipt already processed: " + boardRef + " (Status: " + boardReceipt.getStatus() + ")");
        }
        
        // Update board receipt
        boardReceipt.setUtrNumber(utrNumber);
        boardReceipt.setChecker(checker);
        boardReceipt.setStatus("VERIFIED");
        
        // Save board receipt
        BoardReceipt savedReceipt = repository.save(boardReceipt);
        
        log.info("Processed board receipt {} with UTR {} and updated status to VERIFIED", 
                savedReceipt.getBoardRef(), utrNumber);
        
        return savedReceipt;
    }

    private String generateBoardReceiptNumber() {
        // Generate board receipt number in format: BRD-YYYYMMDD-XXX
        LocalDate now = LocalDate.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequence = String.format("%03d", (System.currentTimeMillis() % 1000));
        
        return "BRD-" + dateStr + "-" + sequence;
    }
}
