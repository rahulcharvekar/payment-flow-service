package com.example.paymentflow.worker.service;

import com.example.paymentflow.worker.entity.WorkerUploadedData;
import com.example.paymentflow.worker.entity.WorkerPayment;
import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import com.example.paymentflow.worker.repository.WorkerUploadedDataRepository;
import com.example.paymentflow.utilities.file.UploadedFileRepository;
import com.example.paymentflow.utilities.file.UploadedFile;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;

@Service
@Transactional
public class WorkerUploadedDataService {
    
    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerUploadedDataService.class);
    
    private final WorkerUploadedDataRepository repository;
    
    @Autowired
    private UploadedFileRepository uploadedFileRepository;
    
    @Autowired
    private WorkerPaymentService workerPaymentService;
    
    @Autowired
    private WorkerPaymentReceiptService workerPaymentReceiptService;

    public WorkerUploadedDataService(WorkerUploadedDataRepository repository) {
        this.repository = repository;
    }

    public WorkerUploadedData save(WorkerUploadedData uploadedData) {
        log.debug("Saving worker uploaded data for fileId: {}, row: {}", 
                uploadedData.getFileId(), uploadedData.getRowNumber());
        return repository.save(uploadedData);
    }

    public List<WorkerUploadedData> saveAll(List<WorkerUploadedData> uploadedDataList) {
        log.info("Saving {} worker uploaded data records", uploadedDataList.size());
        return repository.saveAll(uploadedDataList);
    }

    public List<WorkerUploadedData> findByFileId(String fileId) {
        log.info("Finding worker uploaded data for fileId: {}", fileId);
        return repository.findByFileId(fileId);
    }

    public List<WorkerUploadedData> findByFileIdAndStatus(String fileId, String status) {
        log.info("Finding worker uploaded data for fileId: {} with status: {}", fileId, status);
        return repository.findByFileIdAndStatus(fileId, status);
    }

    public Page<WorkerUploadedData> findByFileIdAndStatusPaginated(String fileId, String status, Pageable pageable) {
        log.info("Finding worker uploaded data for fileId: {} with status: {} (paginated)", fileId, status);
        return repository.findByFileIdAndStatus(fileId, status, pageable);
    }

    public Page<WorkerUploadedData> findByFileIdPaginated(String fileId, Pageable pageable) {
        log.info("Finding worker uploaded data for fileId: {} (paginated)", fileId);
        return repository.findByFileId(fileId, pageable);
    }

    public Map<String, Integer> getFileStatusSummary(String fileId) {
        log.info("Getting status summary for fileId: {}", fileId);
        
        List<Object[]> statusCounts = repository.getStatusCountsByFileId(fileId);
        Map<String, Integer> summary = new HashMap<>();
        
        // Initialize with common statuses
        summary.put("UPLOADED", 0);
        summary.put("VALIDATED", 0);
        summary.put("REJECTED", 0);
        summary.put("REQUEST_GENERATED", 0);
        
        // Populate with actual counts
        for (Object[] result : statusCounts) {
            String status = (String) result[0];
            Long count = (Long) result[1];
            summary.put(status, count.intValue());
        }
        
        return summary;
    }

    private String determineOverallFileStatus(Map<String, Integer> statusSummary) {
        // Determine overall file status based on the distribution of record statuses
        int totalRecords = statusSummary.values().stream().mapToInt(Integer::intValue).sum();
        
        if (totalRecords == 0) {
            return "EMPTY";
        }
        
        int validatedCount = statusSummary.getOrDefault("VALIDATED", 0);
        int rejectedCount = statusSummary.getOrDefault("REJECTED", 0);
        int uploadedCount = statusSummary.getOrDefault("UPLOADED", 0);
        int requestGeneratedCount = statusSummary.getOrDefault("REQUEST_GENERATED", 0);
        
        // If all records have requests generated
        if (requestGeneratedCount == totalRecords) {
            return "REQUEST_GENERATED";
        }
        
        // If majority are validated (and some may have requests generated)
        if (validatedCount + requestGeneratedCount == totalRecords) {
            return requestGeneratedCount > 0 ? "PARTIALLY_PROCESSED" : "VALIDATED";
        }
        
        // If majority are rejected
        if (rejectedCount > totalRecords / 2) {
            return "MOSTLY_REJECTED";
        }
        
        // If majority are still uploaded (not validated)
        if (uploadedCount > totalRecords / 2) {
            return "PENDING_VALIDATION";
        }
        
        // Mixed status - some validated, some rejected, some uploaded
        return "MIXED";
    }

    public Map<String, Object> getComprehensiveFileSummary(String fileId) {
        log.info("Getting comprehensive summary for fileId: {}", fileId);
        
        try {
            // Get file metadata
            Long uploadedFileId = Long.parseLong(fileId);
            Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(uploadedFileId);
            
            if (uploadedFileOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "File not found");
                return error;
            }
            
            UploadedFile uploadedFile = uploadedFileOpt.get();
            
            // Get status summary
            Map<String, Integer> statusSummary = getFileStatusSummary(fileId);
            
            // Calculate validated count and total amount for validated records
            List<WorkerUploadedData> validatedRecords = repository.findByFileIdAndStatus(fileId, "VALIDATED");
            int validatedCount = validatedRecords.size();
            
            BigDecimal totalValidatedAmount = validatedRecords.stream()
                .map(record -> record.getPaymentAmount() != null ? record.getPaymentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Build comprehensive response
            Map<String, Object> summary = new HashMap<>();
            summary.put("fileId", fileId);
            summary.put("fileName", uploadedFile.getFilename());
            summary.put("uploadDate", uploadedFile.getUploadDate());
            summary.put("totalRecords", uploadedFile.getTotalRecords());
            summary.put("validatedCount", validatedCount);
            summary.put("totalValidatedAmount", totalValidatedAmount);
            summary.put("statusSummary", statusSummary);
            summary.put("fileStatus", uploadedFile.getStatus());
            
            // Add ready for payment flag
            boolean readyForPayment = validatedCount > 0 && 
                (statusSummary.getOrDefault("REJECTED", 0) == 0 || 
                 statusSummary.getOrDefault("VALIDATED", 0) > 0);
            summary.put("readyForPayment", readyForPayment);
            
            log.info("Comprehensive summary generated for fileId: {} - {} validated records, total amount: {}", 
                fileId, validatedCount, totalValidatedAmount);
            
            return summary;
            
        } catch (NumberFormatException e) {
            log.error("Invalid fileId format: {}", fileId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid file ID format");
            return error;
        } catch (Exception e) {
            log.error("Error getting comprehensive summary for fileId: {}", fileId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get file summary: " + e.getMessage());
            return error;
        }
    }

    public Map<String, Object> getPaginatedFileSummaries(
            int page, int size, String fileId, String status, 
            String startDate, String endDate, String sortBy, String sortDir) {
        
        log.info("Getting aggregated file summaries - page: {}, size: {}, fileId: {}, status: {}", 
                page, size, fileId, status);
        
        try {
            // Get distinct file IDs from worker_uploaded_data table with filters
            List<String> distinctFileIds = repository.findDistinctFileIds();
            
            // Apply fileId filter if provided
            if (fileId != null && !fileId.trim().isEmpty()) {
                distinctFileIds = distinctFileIds.stream()
                    .filter(id -> id.equals(fileId.trim()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            // Build summary for each file and apply filters
            java.util.List<Map<String, Object>> allFileSummaries = new java.util.ArrayList<>();
            
            for (String currentFileId : distinctFileIds) {
                try {
                    // Get file metadata from uploaded_files table
                    Long uploadedFileId = Long.parseLong(currentFileId);
                    Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(uploadedFileId);
                    
                    if (uploadedFileOpt.isEmpty()) {
                        log.warn("File metadata not found for fileId: {}", currentFileId);
                        continue;
                    }
                    
                    UploadedFile file = uploadedFileOpt.get();
                    
                    // Apply date range filter
                    if (startDate != null && endDate != null) {
                        java.time.LocalDateTime startDateTime = java.time.LocalDate.parse(startDate).atStartOfDay();
                        java.time.LocalDateTime endDateTime = java.time.LocalDate.parse(endDate).atTime(23, 59, 59);
                        
                        if (file.getUploadDate().isBefore(startDateTime) || file.getUploadDate().isAfter(endDateTime)) {
                            continue;
                        }
                    }
                    
                    // Get aggregated data from worker_uploaded_data
                    Map<String, Integer> statusSummary = getFileStatusSummary(currentFileId);
                    int totalRecords = statusSummary.values().stream().mapToInt(Integer::intValue).sum();
                    
                    // Skip if no records found
                    if (totalRecords == 0) {
                        continue;
                    }
                    
                    // Apply status filter based on majority status or specific criteria
                    if (status != null && !status.trim().isEmpty()) {
                        String filterStatus = status.trim().toUpperCase();
                        // Only include if the file has records with the requested status
                        if (!statusSummary.containsKey(filterStatus) || statusSummary.get(filterStatus) == 0) {
                            continue;
                        }
                    }
                    
                    // Calculate validated amount
                    List<WorkerUploadedData> validatedRecords = 
                        repository.findByFileIdAndStatus(currentFileId, "VALIDATED");
                    int validatedCount = validatedRecords.size();
                    
                    BigDecimal totalValidatedAmount = validatedRecords.stream()
                        .map(record -> record.getPaymentAmount() != null ? record.getPaymentAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    // Determine overall file status based on aggregated data
                    String overallStatus = determineOverallFileStatus(statusSummary);
                    
                    // Build file summary
                    Map<String, Object> fileSummary = new HashMap<>();
                    fileSummary.put("fileId", currentFileId);
                    fileSummary.put("fileName", file.getFilename());
                    fileSummary.put("uploadDate", file.getUploadDate());
                    fileSummary.put("totalRecords", totalRecords);
                    fileSummary.put("validatedCount", validatedCount);
                    fileSummary.put("totalValidatedAmount", totalValidatedAmount);
                    fileSummary.put("statusSummary", statusSummary);
                    fileSummary.put("overallStatus", overallStatus);
                    
                    // Add ready for payment flag
                    boolean readyForPayment = validatedCount > 0;
                    fileSummary.put("readyForPayment", readyForPayment);
                    
                    // Add upload timestamp for sorting
                    fileSummary.put("uploadTimestamp", file.getUploadDate());
                    
                    allFileSummaries.add(fileSummary);
                    
                } catch (NumberFormatException e) {
                    log.warn("Invalid fileId format: {}", currentFileId);
                } catch (Exception e) {
                    log.error("Error processing fileId: {}", currentFileId, e);
                }
            }
            
            // Sort the results
            if ("uploadDate".equals(sortBy)) {
                allFileSummaries.sort((a, b) -> {
                    java.time.LocalDateTime dateA = (java.time.LocalDateTime) a.get("uploadTimestamp");
                    java.time.LocalDateTime dateB = (java.time.LocalDateTime) b.get("uploadTimestamp");
                    return "desc".equalsIgnoreCase(sortDir) ? dateB.compareTo(dateA) : dateA.compareTo(dateB);
                });
            } else if ("totalRecords".equals(sortBy)) {
                allFileSummaries.sort((a, b) -> {
                    Integer countA = (Integer) a.get("totalRecords");
                    Integer countB = (Integer) b.get("totalRecords");
                    return "desc".equalsIgnoreCase(sortDir) ? countB.compareTo(countA) : countA.compareTo(countB);
                });
            }
            
            // Apply pagination manually
            int totalElements = allFileSummaries.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int start = page * size;
            int end = Math.min(start + size, totalElements);
            
            List<Map<String, Object>> paginatedSummaries = start < totalElements ? 
                allFileSummaries.subList(start, end) : new ArrayList<>();
            
            // Build paginated response
            Map<String, Object> response = new HashMap<>();
            response.put("data", paginatedSummaries);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("hasNext", page < totalPages - 1);
            response.put("hasPrevious", page > 0);
            
            log.info("Retrieved {} file summaries (page {} of {}) from {} total files", 
                paginatedSummaries.size(), page + 1, totalPages, totalElements);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error getting paginated file summaries", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get file summaries: " + e.getMessage());
            return error;
        }
    }

    /**
     * Cursor-based pagination for file summaries (stub implementation).
     * @param nextPageToken Opaque cursor for next page
     * @param fileId Optional fileId filter
     * @param status Status filter
     * @param startDate Start date (YYYY-MM-DD)
     * @param endDate End date (YYYY-MM-DD)
     * @param sortBy Sort field
     * @param sortDir Sort direction
     * @return Map with paginated summaries and nextPageToken
     */
    public Map<String, Object> getPaginatedFileSummariesWithToken(
            String nextPageToken, String fileId, String status, String startDate, String endDate, String sortBy, String sortDir) {
        // TODO: Implement real cursor-based pagination logic
        // For now, fallback to first page of classic pagination
        int page = 0;
        int size = 20;
        Map<String, Object> result = getPaginatedFileSummaries(page, size, fileId, status, startDate, endDate, sortBy, sortDir);
        // Add a dummy nextPageToken for demonstration
        result.put("nextPageToken", null); // Or generate a real token if needed
        return result;
    }

    @Transactional
    public void validateUploadedData(String fileId) {
        log.info("Starting validation for fileId: {}", fileId);
        
        List<WorkerUploadedData> uploadedRecords = repository.findByFileIdAndStatus(fileId, "UPLOADED");
        log.info("Found {} uploaded records to validate", uploadedRecords.size());
        
        for (WorkerUploadedData record : uploadedRecords) {
            try {
                validateRecord(record);
                if (record.getStatus().equals("VALIDATED")) {
                    record.setValidatedAt(LocalDateTime.now());
                }
            } catch (Exception e) {
                log.error("Error validating record {} for fileId: {}", record.getRowNumber(), fileId, e);
                record.setStatus("REJECTED");
                record.setRejectionReason("Validation error: " + e.getMessage());
            }
        }
        
        repository.saveAll(uploadedRecords);
        log.info("Validation completed for fileId: {}", fileId);
    }

    private void validateRecord(WorkerUploadedData record) {
        StringBuilder errors = new StringBuilder();
        
        // Required field validations
        if (record.getWorkerId() == null || record.getWorkerId().trim().isEmpty()) {
            errors.append("Worker ID is required. ");
        }
        
        if (record.getWorkerName() == null || record.getWorkerName().trim().isEmpty()) {
            errors.append("Worker name is required. ");
        }
        
        if (record.getPaymentAmount() == null || record.getPaymentAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.append("Valid payment amount greater than 0 is required. ");
        }
        
        if (record.getBankAccount() == null || record.getBankAccount().trim().isEmpty()) {
            errors.append("Bank account is required. ");
        }
        
        if (record.getWorkDate() == null) {
            errors.append("Work date is required. ");
        }
        
        // Field length validations
        if (record.getWorkerName() != null && record.getWorkerName().length() > 100) {
            errors.append("Worker name must not exceed 100 characters. ");
        }
        
        if (record.getCompanyName() != null && record.getCompanyName().length() > 100) {
            errors.append("Company name must not exceed 100 characters. ");
        }
        
        if (record.getDepartment() != null && record.getDepartment().length() > 50) {
            errors.append("Department must not exceed 50 characters. ");
        }
        
        if (record.getPosition() != null && record.getPosition().length() > 50) {
            errors.append("Position must not exceed 50 characters. ");
        }
        
        if (record.getWorkerId() != null && record.getWorkerId().length() > 50) {
            errors.append("Worker ID must not exceed 50 characters. ");
        }
        
        if (record.getEmail() != null && record.getEmail().length() > 100) {
            errors.append("Email must not exceed 100 characters. ");
        }
        
        // Bank account validation
        if (record.getBankAccount() != null) {
            String bankAccount = record.getBankAccount().trim();
            if (bankAccount.length() < 10 || bankAccount.length() > 20) {
                errors.append("Bank account must be between 10-20 characters. ");
            }
            // Allow alphanumeric characters for bank accounts (more realistic)
            if (!bankAccount.matches("^[A-Za-z0-9]+$")) {
                errors.append("Bank account must contain only letters and digits. ");
            }
        }
        
        // Phone number validation
        if (record.getPhoneNumber() != null && !record.getPhoneNumber().trim().isEmpty()) {
            String phone = record.getPhoneNumber().trim();
            if (phone.length() > 15) {
                errors.append("Phone number must not exceed 15 characters. ");
            }
            // Valid phone formats: +91-9876543210, +919876543210, 9876543210
            if (!phone.matches("^(\\+\\d{1,3}[\\s\\-]?)?\\d{10}$")) {
                errors.append("Invalid phone number format. ");
            }
        }
        
        // Email format validation
        if (record.getEmail() != null && !record.getEmail().trim().isEmpty()) {
            if (!record.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                errors.append("Invalid email format. ");
            }
        }
        
        // Date validations
        if (record.getWorkDate() != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (record.getWorkDate().isAfter(today)) {
                errors.append("Work date cannot be in the future. ");
            }
            // Check if work date is too far in the past (more than 1 year)
            if (record.getWorkDate().isBefore(today.minusYears(1))) {
                errors.append("Work date cannot be more than 1 year old. ");
            }
        }
        
        // Hours worked validation
        if (record.getHoursWorked() != null) {
            if (record.getHoursWorked().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                errors.append("Hours worked must be greater than 0. ");
            }
            if (record.getHoursWorked().compareTo(new java.math.BigDecimal("24")) > 0) {
                errors.append("Hours worked cannot exceed 24 hours per day. ");
            }
        }
        
        // Hourly rate validation
        if (record.getHourlyRate() != null) {
            if (record.getHourlyRate().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                errors.append("Hourly rate must be greater than 0. ");
            }
            if (record.getHourlyRate().compareTo(new java.math.BigDecimal("10000")) > 0) {
                errors.append("Hourly rate seems unreasonably high (max 10,000). ");
            }
        }
        
        // Payment amount validation
        if (record.getPaymentAmount() != null) {
            if (record.getPaymentAmount().compareTo(new java.math.BigDecimal("1000000")) > 0) {
                errors.append("Payment amount seems unreasonably high (max 1,000,000). ");
            }
        }
        
        // Business logic validations
        if (record.getHoursWorked() != null && record.getHourlyRate() != null && record.getPaymentAmount() != null) {
            java.math.BigDecimal calculatedAmount = record.getHoursWorked().multiply(record.getHourlyRate());
            // Allow for small rounding differences (0.01)
            java.math.BigDecimal difference = record.getPaymentAmount().subtract(calculatedAmount).abs();
            if (difference.compareTo(new java.math.BigDecimal("0.01")) > 0) {
                errors.append("Payment amount doesn't match hours worked × hourly rate (calculated: " + calculatedAmount + "). ");
            }
        }
        
        if (errors.length() > 0) {
            record.setStatus("REJECTED");
            record.setRejectionReason(errors.toString().trim());
        } else {
            record.setStatus("VALIDATED");
        }
    }

    @Transactional
    public int generateRequestForValidatedData(String fileId, String uploadedFileRef) {
        log.info("Generating request for validated data in fileId: {}", fileId);
        
        List<WorkerUploadedData> validatedRecords = repository.findByFileIdAndStatus(fileId, "VALIDATED");
        log.info("Found {} validated records to process", validatedRecords.size());
        
        if (validatedRecords.isEmpty()) {
            return 0;
        }
        
        try {
            // Step 1: Convert WorkerUploadedData to WorkerPayment objects
            List<WorkerPayment> workerPayments = new ArrayList<>();
            for (WorkerUploadedData uploadedData : validatedRecords) {
                WorkerPayment payment = convertUploadedDataToPayment(uploadedData);
                WorkerPayment savedPayment = workerPaymentService.save(payment);
                workerPayments.add(savedPayment);
                log.debug("Created WorkerPayment record for worker: {}", uploadedData.getWorkerId());
            }
            
            // Step 2: Create WorkerPaymentReceipt using the receipt service
            WorkerPaymentReceipt receipt = workerPaymentReceiptService.createReceipt(workerPayments);
            log.info("Created WorkerPaymentReceipt with number: {}", receipt.getReceiptNumber());
            
            // Step 3: Update WorkerPayment records with receipt number (IMPORTANT LINK!)
            for (WorkerPayment payment : workerPayments) {
                try {
                    payment.setReceiptNumber(receipt.getReceiptNumber());
                    workerPaymentService.save(payment);
                    log.debug("Updated WorkerPayment {} with receipt number: {}", payment.getId(), receipt.getReceiptNumber());
                } catch (Exception e) {
                    log.error("Error updating WorkerPayment {} with receipt number", payment.getId(), e);
                }
            }
            
            // Step 4: Update uploaded data records with receipt info and status
            int processedCount = 0;
            for (WorkerUploadedData validatedData : validatedRecords) {
                try {
                    validatedData.setStatus("REQUEST_GENERATED");
                    validatedData.setReceiptNumber(receipt.getReceiptNumber());
                    validatedData.setProcessedAt(LocalDateTime.now());
                    repository.save(validatedData);
                    processedCount++;
                } catch (Exception e) {
                    log.error("Error updating uploaded data record {} after payment creation", validatedData.getId(), e);
                }
            }
            
            log.info("Successfully generated request for {} records with receipt: {}", processedCount, receipt.getReceiptNumber());
            return processedCount;
            
        } catch (Exception e) {
            log.error("Error generating payment request for fileId: {}", fileId, e);
            throw new RuntimeException("Failed to generate payment request: " + e.getMessage(), e);
        }
    }
    
    private WorkerPayment convertUploadedDataToPayment(WorkerUploadedData uploadedData) {
        WorkerPayment payment = new WorkerPayment();
        
        // Map fields from WorkerUploadedData to WorkerPayment based on available fields
        payment.setWorkerRef(uploadedData.getWorkerId()); // worker_id → worker_reference
        payment.setName(uploadedData.getWorkerName()); // worker_name → name
        payment.setPaymentAmount(uploadedData.getPaymentAmount());
        payment.setBankAccount(uploadedData.getBankAccount());
        payment.setFileId(uploadedData.getFileId());
        
        // Set the required employer_id and toli_id fields
        payment.setEmployerId(uploadedData.getEmployerId());
        payment.setToliId(uploadedData.getToliId());
        
        // Set fields that might not have direct mappings but are required
        payment.setRegId(uploadedData.getWorkerId()); // Use worker_id as reg_id for now
        payment.setToli(uploadedData.getDepartment() != null ? uploadedData.getDepartment() : "DEFAULT");
        
        // Set default values for required fields that don't have mappings
        payment.setAadhar(""); // Will need to be updated with actual data
        payment.setPan(""); // Will need to be updated with actual data
        
        // Set status and other metadata
        // Using VALIDATED since these records have already passed validation
        payment.setStatus("VALIDATED");
        payment.setCreatedAt(LocalDateTime.now());
        
        return payment;
    }

    public void deleteByFileId(String fileId) {
        log.info("Deleting all uploaded data for fileId: {}", fileId);
        repository.deleteByFileId(fileId);
    }

    public List<WorkerUploadedData> findRejectedRecords(String fileId) {
        log.info("Finding rejected records for fileId: {}", fileId);
        return repository.findByFileIdAndStatus(fileId, "REJECTED");
    }

    public List<WorkerUploadedData> findRequestGeneratedRecords(String fileId) {
        log.info("Finding request generated records for fileId: {}", fileId);
        return repository.findByFileIdAndStatus(fileId, "REQUEST_GENERATED");
    }

    public List<WorkerUploadedData> findByReceiptNumber(String receiptNumber) {
        log.info("Finding records by receipt number: {}", receiptNumber);
        return repository.findByReceiptNumber(receiptNumber);
    }

    public Page<WorkerUploadedData> findByReceiptNumberPaginated(String receiptNumber, Pageable pageable) {
        log.info("Finding records by receipt number: {} (paginated)", receiptNumber);
        return repository.findByReceiptNumber(receiptNumber, pageable);
    }

    public Page<WorkerUploadedData> findByFileIdStatusAndDateRangePaginated(String fileId, String status, 
            LocalDateTime startDate, LocalDateTime endDate, org.springframework.data.domain.Pageable pageable) {
        log.info("Finding records by fileId: {}, status: {}, date range: {} to {} (paginated)", 
                fileId, status, startDate, endDate);
        return repository.findByFileIdAndStatusAndCreatedAtBetween(fileId, status, startDate, endDate, pageable);
    }

    public Page<WorkerUploadedData> findByFileIdAndDateRangePaginated(String fileId, 
            LocalDateTime startDate, LocalDateTime endDate, org.springframework.data.domain.Pageable pageable) {
        log.info("Finding records by fileId: {}, date range: {} to {} (paginated)", 
                fileId, startDate, endDate);
        return repository.findByFileIdAndCreatedAtBetween(fileId, startDate, endDate, pageable);
    }
    
    public Page<WorkerUploadedData> findByDateRangePaginated(LocalDateTime startDate, LocalDateTime endDate, 
            org.springframework.data.domain.Pageable pageable) {
        log.info("Finding all records by date range: {} to {} (paginated)", startDate, endDate);
        return repository.findByCreatedAtBetween(startDate, endDate, pageable);
    }
}
