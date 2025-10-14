package com.example.paymentflow.employer.service;

import com.example.paymentflow.employer.entity.EmployerPaymentReceipt;
import com.example.paymentflow.employer.dao.EmployerPaymentReceiptRepository;
import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import com.example.paymentflow.worker.dao.WorkerPaymentReceiptQueryDao;
import com.example.paymentflow.worker.entity.WorkerPayment;
import com.example.paymentflow.worker.service.WorkerPaymentReceiptService;

import com.example.paymentflow.worker.service.WorkerPaymentService;
import com.example.paymentflow.board.service.BoardReceiptService;
import com.example.paymentflow.board.entity.BoardReceipt;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class EmployerPaymentReceiptService {
    /**
     * Cursor-based pagination for employer payment receipts (stub implementation).
     * @param status Payment status filter
     * @param start Start date
     * @param end End date
     * @param nextPageToken Opaque cursor for next page
     * @return Page of EmployerPaymentReceipt
     */
    @Transactional(readOnly = true)
    public Page<EmployerPaymentReceipt> findAvailableByStatusAndDateRangeWithToken(String status, java.time.LocalDateTime start, java.time.LocalDateTime end, String nextPageToken) {
        // TODO: Implement real cursor-based pagination logic
        // For now, fallback to first page of classic pagination
        Pageable pageable = PageRequest.of(0, 20, Sort.by("validatedAt").descending());
        return findAvailableByStatusAndDateRange(status, start, end, pageable);
    }

    @Transactional(readOnly = true)
    public Page<EmployerPaymentReceipt> findAvailableByStatusAndDateRange(String status, java.time.LocalDateTime start, java.time.LocalDateTime end, Pageable pageable) {
        log.info("Finding available employer receipts with status: {} and date range: {} to {} (paginated)", status, start, end);
        String filterStatus = (status != null && !status.trim().isEmpty()) ? status : "PAYMENT_INITIATED";
        if (start != null && end != null && status != null && !status.trim().isEmpty()) {
            return repository.findByStatusAndValidatedAtBetween(filterStatus, start, end, pageable);
        } else if (start != null && end != null) {
            return repository.findByValidatedAtBetween(start, end, pageable);
        } else if (status != null && !status.trim().isEmpty()) {
            return repository.findByStatus(filterStatus, pageable);
        } else {
            return repository.findAll(pageable);
        }
    }
    
    private static final Logger log = LoggerFactoryProvider.getLogger(EmployerPaymentReceiptService.class);
    
    private final EmployerPaymentReceiptRepository repository;
    private final WorkerPaymentReceiptQueryDao workerReceiptQueryDao;
    private final WorkerPaymentService workerPaymentService;
    private final BoardReceiptService boardReceiptService;
    private final WorkerPaymentReceiptService workerReceiptService;

    public EmployerPaymentReceiptService(EmployerPaymentReceiptRepository repository,
                                       WorkerPaymentReceiptQueryDao workerReceiptQueryDao,
                                       WorkerPaymentService workerPaymentService,
                                       BoardReceiptService boardReceiptService,
                                       WorkerPaymentReceiptService workerReceiptService) {
        this.repository = repository;
        this.workerReceiptQueryDao = workerReceiptQueryDao;
        this.workerPaymentService = workerPaymentService;
        this.boardReceiptService = boardReceiptService;
        this.workerReceiptService = workerReceiptService;
    }

        @Transactional(readOnly = true)
    public List<WorkerPaymentReceipt> getAvailableReceipts() {
        log.info("Retrieving worker receipts available for employer validation using query DAO");
        return workerReceiptQueryDao.findByStatus("PROCESSED");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAvailableReceiptsWithFilters(int page, int size, String status, 
                                                              String singleDate, String startDate, String endDate) {
        log.info("Retrieving paginated worker receipts - page: {}, size: {}, status: {}, singleDate: {}, startDate: {}, endDate: {}", 
                page, size, status, singleDate, startDate, endDate);
        
        // Create pageable object
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        // Default status to PROCESSED if not provided (receipts that are ready for employer validation)
        String filterStatus = (status != null && !status.trim().isEmpty()) ? status : "PAYMENT_INITIATED";
        
        Page<WorkerPaymentReceipt> receiptsPage;
        
        // Handle date filtering using query DAO (converting to simple lists for now)
        List<WorkerPaymentReceipt> allReceipts;
        if (singleDate != null && !singleDate.trim().isEmpty()) {
            // Single date filter
            LocalDate date = LocalDate.parse(singleDate);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(23, 59, 59);
            allReceipts = workerReceiptQueryDao.findByStatusAndDateRange(filterStatus, startOfDay, endOfDay);
            
        } else if (startDate != null && !startDate.trim().isEmpty() && endDate != null && !endDate.trim().isEmpty()) {
            // Date range filter
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            allReceipts = workerReceiptQueryDao.findByStatusAndDateRange(filterStatus, startDateTime, endDateTime);
            
        } else {
            // No date filter, just status
            allReceipts = workerReceiptQueryDao.findByStatus(filterStatus);
        }
        
        // Manual pagination since our DAO doesn't support it yet
        int start = Math.min(page * size, allReceipts.size());
        int end = Math.min(start + size, allReceipts.size());
        List<WorkerPaymentReceipt> pageContent = allReceipts.subList(start, end);
        receiptsPage = new PageImpl<>(pageContent, pageable, allReceipts.size());
        
        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("content", receiptsPage.getContent());
        response.put("page", receiptsPage.getNumber());
        response.put("size", receiptsPage.getSize());
        response.put("totalElements", receiptsPage.getTotalElements());
        response.put("totalPages", receiptsPage.getTotalPages());
        response.put("first", receiptsPage.isFirst());
        response.put("last", receiptsPage.isLast());
        response.put("empty", receiptsPage.isEmpty());
        
        log.info("Found {} worker receipts (page {} of {})", receiptsPage.getTotalElements(), 
                receiptsPage.getNumber() + 1, receiptsPage.getTotalPages());
        
        return response;
    }

    public EmployerPaymentReceipt validateAndCreateEmployerReceipt(String workerReceiptNumber, 
                                                                 String transactionReference, 
                                                                 String validatedBy) {
        log.info("Validating employer receipt for worker receipt: {} with txn ref: {}", 
                workerReceiptNumber, transactionReference);
        
        // Find the worker receipt
        Optional<WorkerPaymentReceipt> workerReceiptOpt = workerReceiptQueryDao.findByReceiptNumber(workerReceiptNumber);
        if (workerReceiptOpt.isEmpty()) {
            throw new RuntimeException("Worker receipt not found: " + workerReceiptNumber);
        }
        
        WorkerPaymentReceipt workerReceipt = workerReceiptOpt.get();
        
        // Check if employer receipt exists (should be PENDING)
        Optional<EmployerPaymentReceipt> existingOpt = repository.findByWorkerReceiptNumber(workerReceiptNumber);
        EmployerPaymentReceipt employerReceipt;
        
        if (existingOpt.isPresent()) {
            employerReceipt = existingOpt.get();
            // Check if already validated
            if ("VALIDATED".equals(employerReceipt.getStatus())) {
                throw new RuntimeException("Worker receipt already validated: " + workerReceiptNumber);
            }
            // Update existing PENDING receipt to SEND TO BOARD
            employerReceipt.setTransactionReference(transactionReference);
            employerReceipt.setValidatedBy(validatedBy);
            employerReceipt.setValidatedAt(LocalDateTime.now());
            employerReceipt.setStatus("SEND TO BOARD");
        } else {
            // Create new employer receipt (fallback if auto-creation failed)
            employerReceipt = new EmployerPaymentReceipt();
            employerReceipt.setEmployerReceiptNumber(generateEmployerReceiptNumber());
            employerReceipt.setWorkerReceiptNumber(workerReceiptNumber);
            employerReceipt.setEmployerId(workerReceipt.getEmployerId());
            employerReceipt.setToliId(workerReceipt.getToliId());
            employerReceipt.setTransactionReference(transactionReference);
            employerReceipt.setValidatedBy(validatedBy);
            employerReceipt.setValidatedAt(LocalDateTime.now());
            employerReceipt.setTotalRecords(workerReceipt.getTotalRecords());
            employerReceipt.setTotalAmount(workerReceipt.getTotalAmount());
            employerReceipt.setStatus("SEND TO BOARD");
        }
        
        // Save employer receipt
        EmployerPaymentReceipt savedReceipt = repository.save(employerReceipt);
        
        // Create board receipt with PENDING status
        try {
            BoardReceipt boardReceipt = boardReceiptService.createFromEmployerReceipt(savedReceipt, validatedBy);
            log.info("Created board receipt {} for employer receipt {}", 
                    boardReceipt.getBoardRef(), savedReceipt.getEmployerReceiptNumber());
        } catch (Exception e) {
            log.error("Failed to create board receipt for employer receipt {}", 
                    savedReceipt.getEmployerReceiptNumber(), e);
        }
        
        // Update worker receipt status using the proper service method
        workerReceiptService.updateStatus(workerReceiptNumber, "VALIDATED");
        
        // Update all worker payments with this receipt number to PAYMENT_INITIATED
        // Use WorkerPaymentService method that uses the query DAO
        Page<WorkerPayment> paymentsPage = workerPaymentService.findByReceiptNumber(workerReceiptNumber, PageRequest.of(0, 1000));
        List<WorkerPayment> workerPayments = paymentsPage.getContent();
        for (WorkerPayment payment : workerPayments) {
            if ("PAYMENT_REQUESTED".equals(payment.getStatus())) {
                payment.setStatus("PAYMENT_INITIATED");
                workerPaymentService.save(payment);
            }
        }
        
        log.info("Validated employer receipt {} for worker receipt {}, created board receipt, and updated {} worker payments to PAYMENT_INITIATED", 
                savedReceipt.getEmployerReceiptNumber(), workerReceiptNumber, workerPayments.size());
        
        return savedReceipt;
    }

    public EmployerPaymentReceipt createPendingEmployerReceipt(WorkerPaymentReceipt workerReceipt) {
        log.info("Creating pending employer receipt for worker receipt: {}", workerReceipt.getReceiptNumber());
        
        // Check if already exists
        Optional<EmployerPaymentReceipt> existingOpt = repository.findByWorkerReceiptNumber(workerReceipt.getReceiptNumber());
        if (existingOpt.isPresent()) {
            log.info("Employer receipt already exists for worker receipt: {}", workerReceipt.getReceiptNumber());
            return existingOpt.get();
        }
        
        // Create employer receipt with PENDING status
        EmployerPaymentReceipt employerReceipt = new EmployerPaymentReceipt();
        employerReceipt.setEmployerReceiptNumber(generateEmployerReceiptNumber());
        employerReceipt.setWorkerReceiptNumber(workerReceipt.getReceiptNumber());
        employerReceipt.setEmployerId(workerReceipt.getEmployerId());
        employerReceipt.setToliId(workerReceipt.getToliId());
        employerReceipt.setTransactionReference(""); // Will be filled during validation
        employerReceipt.setValidatedBy(""); // Will be filled during validation
        employerReceipt.setValidatedAt(LocalDateTime.now()); // Creation time for now
        employerReceipt.setTotalRecords(workerReceipt.getTotalRecords());
        employerReceipt.setTotalAmount(workerReceipt.getTotalAmount());
        employerReceipt.setStatus("PENDING");
        
        // Save employer receipt
        EmployerPaymentReceipt savedReceipt = repository.save(employerReceipt);
        
        log.info("Created pending employer receipt {} for worker receipt {}", 
                savedReceipt.getEmployerReceiptNumber(), workerReceipt.getReceiptNumber());
        
        return savedReceipt;
    }
    
    private String generateEmployerReceiptNumber() {
        // Generate employer receipt number in format: EMP-YYYYMMDD-HHMMSS-XXX
        LocalDateTime now = LocalDateTime.now();
        String dateTime = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String sequence = String.format("%03d", (System.currentTimeMillis() % 1000));
        
        return "EMP-" + dateTime + "-" + sequence;
    }
}
