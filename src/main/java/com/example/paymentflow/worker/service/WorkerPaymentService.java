package com.example.paymentflow.worker.service;

import com.example.paymentflow.worker.entity.WorkerPayment;
import com.example.paymentflow.exception.ResourceNotFoundException;
import com.example.paymentflow.worker.repository.WorkerPaymentRepository;
import com.example.paymentflow.worker.dao.WorkerPaymentQueryDao;
import com.example.paymentflow.common.dao.BaseQueryDao.PageResult; // Uncomment if exists
import java.util.List;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import com.example.paymentflow.utilities.logger.LoggerFactoryProvider; // Uncomment if exists
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@Service
@Transactional
public class WorkerPaymentService {

    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerPaymentService.class);

    private final WorkerPaymentRepository repository;
    private final WorkerPaymentQueryDao workerPaymentQueryDao;

    public WorkerPaymentService(WorkerPaymentRepository repository, WorkerPaymentQueryDao workerPaymentQueryDao) {
        this.repository = repository;
        this.workerPaymentQueryDao = workerPaymentQueryDao;
    }

    public WorkerPayment create(WorkerPayment workerPayment) {
        log.info("Persisting worker payment for workerRef={}", workerPayment.getWorkerRef());
        WorkerPayment saved = repository.save(workerPayment);
        log.info("Persisted worker payment id={}", saved.getId());
        return saved;
    }

    public List<WorkerPayment> createBulk(List<WorkerPayment> workerPayments) {
        log.info("Bulk persisting {} worker payments", workerPayments.size());
        List<WorkerPayment> saved = repository.saveAll(workerPayments);
        log.info("Bulk persisted {} worker payments", saved.size());
        return saved;
    }

    public List<WorkerPayment> updateBulk(List<WorkerPayment> workerPayments) {
        log.info("Bulk updating {} worker payments", workerPayments.size());
        List<WorkerPayment> saved = repository.saveAll(workerPayments);
        log.info("Bulk updated {} worker payments", saved.size());
        return saved;
    }

    // READ OPERATIONS - Using Query DAO
    @Transactional(readOnly = true)
    public List<WorkerPayment> findByRequestReferencePrefix(String prefix) {
        log.info("Finding worker payments with request reference starting with: {} using query DAO", prefix);
        return workerPaymentQueryDao.findByRequestReferenceNumberStartingWith(prefix);
    }

    @Transactional(readOnly = true)
    public List<WorkerPayment> findByStatus(String status) {
        log.info("Finding worker payments with status: {} using query DAO", status);
        // Using paginated method with large page size to get all results
        return workerPaymentQueryDao.findByStatus(status, 0, 10000).getContent();
    }

    @Transactional(readOnly = true)
    public List<WorkerPayment> findByReferencePrefixAndStatus(String prefix, String status) {
        log.info("Finding worker payments with reference prefix: {} and status: {} using query DAO", prefix, status);
        // Use the filter method with null values for other filters
        return workerPaymentQueryDao.findWithFilters(status, null, null, null, null, 0, 10000).getContent()
                .stream()
                .filter(wp -> wp.getRequestReferenceNumber().startsWith(prefix))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerPayment> findByFileId(String fileId) {
        log.info("Finding worker payments for fileId: {} using query DAO", fileId);
        // Using paginated method with large page size to get all results
        return workerPaymentQueryDao.findByFileId(fileId, 0, 10000).getContent();
    }

    @Transactional(readOnly = true)
    public List<WorkerPayment> findByFileIdAndStatus(String fileId, String status) {
        log.info("Finding worker payments for fileId: {} with status: {} using query DAO", fileId, status);
        return workerPaymentQueryDao.findWithFilters(status, null, fileId, null, null, 0, 10000).getContent();
    }

    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByFileIdPaginated(String fileId, Pageable pageable) {
        log.info("Finding worker payments with fileId: {} (paginated) using query DAO", fileId);
        var result = workerPaymentQueryDao.findByFileId(fileId, pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByFileIdAndStatusPaginated(String fileId, String status, Pageable pageable) {
        log.info("Finding worker payments with fileId: {} and status: {} (paginated) using query DAO", fileId, status);
        var result = workerPaymentQueryDao.findWithFilters(status, null, fileId, null, null, 
                                                           pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }

    @Transactional(readOnly = true)
    public List<WorkerPayment> findAll() {
        log.info("Retrieving all worker payments using query DAO");
        // Use a large page size to get all results
        return workerPaymentQueryDao.findWithFilters(null, null, null, null, null, 0, 100000).getContent();
    }
    
    @Transactional(readOnly = true)
    public Page<WorkerPayment> findAllPaginated(Pageable pageable) {
        log.info("Retrieving all worker payments (paginated) using query DAO");
        var result = workerPaymentQueryDao.findWithFilters(null, null, null, null, null, 
                                                           pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByStatusPaginated(String status, Pageable pageable) {
        log.info("Finding worker payments with status: {} (paginated) using query DAO", status);
        var result = workerPaymentQueryDao.findByStatus(status, pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByReceiptNumber(String receiptNumber, Pageable pageable) {
        log.info("Finding worker payments with receipt number: {} (paginated) using query DAO", receiptNumber);
        var result = workerPaymentQueryDao.findWithFilters(null, receiptNumber, null, null, null, 
                                                           pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByStatusAndReceiptNumber(
            String status,
            String receiptNumber,
            Pageable pageable) {
        log.info("Finding worker payments with status: {}, receipt number: {} (paginated) using query DAO", 
                status, receiptNumber);
        var result = workerPaymentQueryDao.findWithFilters(status, receiptNumber, null, null, null, 
                                                           pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }

    @Transactional(readOnly = true)
    public WorkerPayment findById(Long id) {
        log.info("Retrieving worker payment id={}", id);
        return repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Worker payment id={} not found", id);
                    return new ResourceNotFoundException("Worker payment not found for id=" + id);
                });
    }

    public WorkerPayment update(Long id, WorkerPayment updated) {
        log.info("Updating worker payment id={}", id);
        WorkerPayment existing = findById(id);
        existing.setWorkerRef(updated.getWorkerRef());
        existing.setRegId(updated.getRegId());
        existing.setName(updated.getName());
        existing.setToli(updated.getToli());
        existing.setAadhar(updated.getAadhar());
        existing.setPan(updated.getPan());
        existing.setBankAccount(updated.getBankAccount());
        existing.setPaymentAmount(updated.getPaymentAmount());
        if (updated.getRequestReferenceNumber() != null && !updated.getRequestReferenceNumber().isBlank()) {
            existing.setRequestReferenceNumber(updated.getRequestReferenceNumber());
        }
        WorkerPayment saved = repository.save(existing);
        log.info("Updated worker payment id={}", saved.getId());
        return saved;
    }

    public void delete(Long id) {
        log.info("Deleting worker payment id={}", id);
        if (!repository.existsById(id)) {
            log.warn("Cannot delete worker payment id={} because it does not exist", id);
            throw new ResourceNotFoundException("Worker payment not found for id=" + id);
        }
        repository.deleteById(id);
        log.info("Deleted worker payment id={}", id);
    }

    public WorkerPayment save(WorkerPayment workerPayment) {
        log.info("Saving worker payment with id={}", workerPayment.getId());
        WorkerPayment saved = repository.save(workerPayment);
        log.info("Saved worker payment with id={}", saved.getId());
        return saved;
    }

    // All read methods now use WorkerPaymentQueryDao
    // These methods are removed as they used the old repository approach
    
    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByStatusAndReceiptNumberAndDateRange(
            String status,
            String receiptNumber,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        log.info("Finding worker payments with status: {}, receipt number: {}, date range: {} to {} (paginated) using query DAO", 
                status, receiptNumber, startDate, endDate);
        var result = workerPaymentQueryDao.findWithFilters(status, receiptNumber, null, startDate, endDate, 
                                                           pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<WorkerPayment> findByUploadedFileRefPaginated(String uploadedFileRef, Pageable pageable) {
        log.info("Finding worker payments by uploaded file ref: {} (paginated) using query DAO", uploadedFileRef);
        var result = workerPaymentQueryDao.findWithFilters(null, null, uploadedFileRef, null, null, 
                                                           pageable.getPageNumber(), pageable.getPageSize());
        return createPageFromPageResult(result, pageable);
    }
    
    /**
     * Cursor-based pagination for worker payments (stub implementation).
     * @param status Payment status filter
     * @param receiptNumber Receipt number filter
     * @param startDate Start date
     * @param endDate End date
     * @param sort Sort order
     * @param nextPageToken Opaque cursor for next page
     * @return Page of WorkerPayment
     */
    public Page<WorkerPayment> findByStatusAndReceiptNumberAndDateRangeWithToken(
            String status,
            String receiptNumber,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            org.springframework.data.domain.Sort sort,
            String nextPageToken) {
        // TODO: Implement real cursor-based pagination logic
        // For now, fallback to first page of classic pagination
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20, sort);
        return findByStatusAndReceiptNumberAndDateRange(status, receiptNumber, startDate, endDate, pageable);
    }
    
    // Utility method to convert PageResult to Spring Page
    private Page<WorkerPayment> createPageFromPageResult(PageResult<WorkerPayment> pageResult, Pageable pageable) {
        return new PageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements());
    }
}
