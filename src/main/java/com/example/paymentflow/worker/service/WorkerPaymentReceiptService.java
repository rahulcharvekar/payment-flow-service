package com.example.paymentflow.worker.service;

import com.example.paymentflow.worker.entity.WorkerPayment;
import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import com.example.paymentflow.worker.repository.WorkerPaymentReceiptRepository;
import com.example.paymentflow.worker.dao.WorkerPaymentReceiptQueryDao;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class WorkerPaymentReceiptService {
    /**
     * Cursor-based pagination for worker payment receipts (stub implementation).
     * @param status Receipt status filter
     * @param startDate Start date
     * @param endDate End date
     * @param nextPageToken Opaque cursor for next page
     * @return Page of WorkerPaymentReceipt
     */
    public org.springframework.data.domain.Page<WorkerPaymentReceipt> findByStatusAndDateRangeWithToken(
            String status, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, String nextPageToken) {
        // TODO: Implement real cursor-based pagination logic
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("validatedAt").descending());
        return findByStatusAndDateRangePaginated(status, startDate, endDate, pageable);
    }

    /**
     * Cursor-based pagination for worker payment receipts by date range (stub implementation).
     * @param startDate Start date
     * @param endDate End date
     * @param nextPageToken Opaque cursor for next page
     * @return Page of WorkerPaymentReceipt
     */
    public org.springframework.data.domain.Page<WorkerPaymentReceipt> findByDateRangeWithToken(
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, String nextPageToken) {
        // TODO: Implement real cursor-based pagination logic
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("validatedAt").descending());
        return findByDateRangePaginated(startDate, endDate, pageable);
    }
    
    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerPaymentReceiptService.class);
    
    private final WorkerPaymentReceiptRepository repository;
    private final WorkerPaymentReceiptQueryDao queryDao;

    public WorkerPaymentReceiptService(WorkerPaymentReceiptRepository repository, WorkerPaymentReceiptQueryDao queryDao) {
        this.repository = repository;
        this.queryDao = queryDao;
    }

    public WorkerPaymentReceipt createReceipt(List<WorkerPayment> processedPayments) {
        log.info("Creating receipt for {} payments", processedPayments.size());
        
        if (processedPayments.isEmpty()) {
            throw new IllegalArgumentException("Cannot create receipt for empty payment list");
        }
        
        // Calculate total amount
        BigDecimal totalAmount = processedPayments.stream()
                .map(WorkerPayment::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Generate receipt number
        String receiptNumber = generateReceiptNumber();
        
        // Get employer_id and toli_id from the first payment (all payments in a batch should have the same employer/toli)
        WorkerPayment firstPayment = processedPayments.get(0);
        String employerId = firstPayment.getEmployerId();
        String toliId = firstPayment.getToliId();
        
        // Create receipt
        WorkerPaymentReceipt receipt = new WorkerPaymentReceipt();
        receipt.setReceiptNumber(receiptNumber);
        receipt.setEmployerId(employerId);
        receipt.setToliId(toliId);
        receipt.setCreatedAt(LocalDateTime.now());
        receipt.setTotalRecords(processedPayments.size());
        receipt.setTotalAmount(totalAmount);
        receipt.setStatus("PROCESSED"); // Changed from GENERATED to match constraint
        
        // Save receipt first to get ID
        WorkerPaymentReceipt savedReceipt = repository.save(receipt);
        
        log.info("Created receipt {} with {} payments totaling {}", receiptNumber, processedPayments.size(), totalAmount);
        
        return savedReceipt;
    }
    
    private String generateReceiptNumber() {
        // Generate receipt number in format: RCP-YYYYMMDD-HHMMSS-XXX
        // Use retry mechanism to ensure uniqueness
        String receiptNumber;
        int maxAttempts = 10;
        int attempt = 0;
        
        do {
            LocalDateTime now = LocalDateTime.now();
            String dateTime = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            
            // Use a combination of current nanos and a random component for better uniqueness
            long nanos = System.nanoTime();
            int randomComponent = (int) (Math.random() * 1000);
            String sequence = String.format("%03d", (nanos % 1000 + randomComponent) % 1000);
            
            receiptNumber = "RCP-" + dateTime + "-" + sequence;
            attempt++;
            
            // Check if this receipt number already exists in database
            if (queryDao.findByReceiptNumber(receiptNumber).isEmpty()) {
                log.info("Generated unique receipt number: {} (attempt {})", receiptNumber, attempt);
                return receiptNumber;
            }
            
            log.warn("Receipt number {} already exists, retrying... (attempt {})", receiptNumber, attempt);
            
            // Small delay to avoid immediate collision
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while generating receipt number", e);
            }
            
        } while (attempt < maxAttempts);
        
        throw new RuntimeException("Failed to generate unique receipt number after " + maxAttempts + " attempts");
    }

    public List<WorkerPaymentReceipt> findByStatus(String status) {
        log.info("Finding worker payment receipts with status: {}", status);
        return queryDao.findByStatus(status);
    }

    public List<WorkerPaymentReceipt> findAll() {
        log.info("Finding all worker payment receipts");
        return queryDao.findAll();
    }

    public java.util.Optional<WorkerPaymentReceipt> findByReceiptNumber(String receiptNumber) {
        log.info("Finding worker payment receipt by receipt number: {}", receiptNumber);
        return queryDao.findByReceiptNumber(receiptNumber);
    }

    public org.springframework.data.domain.Page<WorkerPaymentReceipt> findByStatusPaginated(String status, org.springframework.data.domain.Pageable pageable) {
        log.info("Finding worker payment receipts with status: {} (paginated)", status);
        // For now, return a simple implementation - can be enhanced later with proper pagination
        List<WorkerPaymentReceipt> results = queryDao.findByStatus(status);
        return new org.springframework.data.domain.PageImpl<>(results, pageable, results.size());
    }

    public org.springframework.data.domain.Page<WorkerPaymentReceipt> findAllPaginated(org.springframework.data.domain.Pageable pageable) {
        log.info("Finding all worker payment receipts (paginated)");
        List<WorkerPaymentReceipt> results = queryDao.findAll();
        return new org.springframework.data.domain.PageImpl<>(results, pageable, results.size());
    }

    public org.springframework.data.domain.Page<WorkerPaymentReceipt> findByStatusAndDateRangePaginated(
            String status, LocalDateTime startDate, LocalDateTime endDate, org.springframework.data.domain.Pageable pageable) {
        log.info("Finding worker payment receipts with status: {} between {} and {} (paginated)", status, startDate, endDate);
        List<WorkerPaymentReceipt> results = queryDao.findByStatusAndDateRange(status, startDate, endDate);
        return new org.springframework.data.domain.PageImpl<>(results, pageable, results.size());
    }

    public org.springframework.data.domain.Page<WorkerPaymentReceipt> findByDateRangePaginated(
            LocalDateTime startDate, LocalDateTime endDate, org.springframework.data.domain.Pageable pageable) {
        log.info("Finding worker payment receipts between {} and {} (paginated)", startDate, endDate);
        List<WorkerPaymentReceipt> results = queryDao.findByDateRange(startDate, endDate);
        return new org.springframework.data.domain.PageImpl<>(results, pageable, results.size());
    }

    public WorkerPaymentReceipt updateStatus(String receiptNumber, String newStatus) {
        log.info("Updating status of worker payment receipt {} to {}", receiptNumber, newStatus);
        
        // Use JPA repository to fetch the entity in a managed state to ensure proper UPDATE
        WorkerPaymentReceipt receipt = repository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new RuntimeException("Worker payment receipt not found with number: " + receiptNumber));
        
        receipt.setStatus(newStatus);
        return repository.save(receipt);
    }
}
