package com.example.paymentflow.worker.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.paymentflow.utilities.file.FileStorageUtil;
import com.example.paymentflow.utilities.file.UploadedFile;
import com.example.paymentflow.utilities.file.UploadedFileRepository;
import com.shared.utilities.logger.LoggerFactoryProvider;
import com.example.paymentflow.worker.entity.WorkerPayment;
import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import org.slf4j.Logger;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.CellType;

@Service
public class WorkerPaymentFileService {
    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerPaymentFileService.class);

    @Autowired
    private FileStorageUtil fileStorageUtil;

    
    @Autowired
    private UploadedFileRepository uploadedFileRepository;
    
    @Autowired
    private WorkerPaymentService workerPaymentService;
    
    @Autowired
    private WorkerPaymentReceiptService receiptService;
    
    @Autowired
    private WorkerUploadedDataService workerUploadedDataService;

    public Map<String, Object> handleFileUpload(MultipartFile file) {
        log.info("Received file upload: name={}, size={} bytes", file.getOriginalFilename(), file.getSize());
        
        try {
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            
            // Use the new method that returns the entity directly to avoid lookup issues
            UploadedFile uploadedFile = fileStorageUtil.storeFileAndReturnEntity(file, "workerpayments", fileName);
            String storedPath = uploadedFile.getStoredPath();
            String fileId = uploadedFile.getId().toString();
            
            log.info("File saved to {} with fileId: {}", storedPath, fileId);
            
            // Parse the file and extract worker uploaded data
            File fileToRead = new File(storedPath);
            List<com.example.paymentflow.worker.entity.WorkerUploadedData> uploadedDataList = 
                parseFileToUploadedData(fileToRead, file.getOriginalFilename(), fileId);
            
            // Update the uploaded file record with parsing results
            uploadedFile.setTotalRecords(uploadedDataList.size());
            uploadedFile.setSuccessCount(0); // Will be updated after validation
            uploadedFile.setFailureCount(0);
            uploadedFile.setStatus("UPLOADED");
            uploadedFileRepository.save(uploadedFile);
            
            // Save uploaded data to WorkerUploadedData table
            List<com.example.paymentflow.worker.entity.WorkerUploadedData> savedData = 
                workerUploadedDataService.saveAll(uploadedDataList);
            
            log.info("File {} parsed and {} records saved to WorkerUploadedData (fileId={})", 
                file.getOriginalFilename(), savedData.size(), fileId);
            
            // Create response map step by step to identify any null values
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("message", "File uploaded successfully. " + savedData.size() + " records loaded. Proceed to validation.");
            response.put("path", storedPath);
            response.put("recordCount", savedData.size());
            
            log.info("Returning response: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("Failed to process uploaded file", e);
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = e.getClass().getSimpleName() + " - " + e.toString();
            }
            return Map.of("error", "Failed to process uploaded file: " + errorMessage);
        }
    }

    public Map<String, Object> validateFileRecords(String fileId) {
        log.info("Validating records for fileId={}", fileId);
        
        try {
            Long uploadedFileId = Long.parseLong(fileId);
            Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(uploadedFileId);
            
            if (uploadedFileOpt.isEmpty()) {
                return Map.of("error", "File not found");
            }
            
            // Validate uploaded data using the new service
            workerUploadedDataService.validateUploadedData(fileId);
            
            // Get validation summary
            Map<String, Integer> summary = workerUploadedDataService.getFileStatusSummary(fileId);
            int passedCount = summary.getOrDefault("VALIDATED", 0);
            int failedCount = summary.getOrDefault("REJECTED", 0);
            
            // Update the uploaded file record with validation results
            UploadedFile uploadedFile = uploadedFileOpt.get();
            uploadedFile.setSuccessCount(passedCount);
            uploadedFile.setFailureCount(failedCount);
            uploadedFile.setStatus("COMPLETED");
            uploadedFileRepository.save(uploadedFile);
            
            log.info("Validation complete for fileId={}: {} passed, {} failed", fileId, passedCount, failedCount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("passed", passedCount);
            response.put("failed", failedCount);
            response.put("status", "COMPLETED");
            response.put("nextAction", "GENERATE_REQUEST");
            response.put("message", "Validation completed. " + passedCount + " records passed validation. Ready to generate request.");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error validating records for fileId={}", fileId, e);
            return Map.of("error", "Validation failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getValidationResults(String fileId) {
        log.info("Fetching validation results for fileId={}", fileId);
        
        try {
            // Get all uploaded data for this file
            List<com.example.paymentflow.worker.entity.WorkerUploadedData> uploadedDataList = 
                workerUploadedDataService.findByFileId(fileId);
            
            List<Map<String, Object>> passedRecords = new ArrayList<>();
            List<Map<String, Object>> failedRecords = new ArrayList<>();
            
            for (com.example.paymentflow.worker.entity.WorkerUploadedData data : uploadedDataList) {
                Map<String, Object> record = createUploadedDataSummary(data);
                
                if ("VALIDATED".equals(data.getStatus())) {
                    passedRecords.add(record);
                } else if ("REJECTED".equals(data.getStatus())) {
                    failedRecords.add(record);
                }
            }
            
            return Map.of(
                "passedRecords", passedRecords,
                "failedRecords", failedRecords
            );
            
        } catch (Exception e) {
            log.error("Error fetching validation results for fileId={}", fileId, e);
            return Map.of("error", "Failed to fetch results: " + e.getMessage());
        }
    }
    


    private Map<String, Object> createUploadedDataSummary(com.example.paymentflow.worker.entity.WorkerUploadedData data) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", data.getId());
        summary.put("fileId", data.getFileId());
        summary.put("rowNumber", data.getRowNumber());
        summary.put("workerId", data.getWorkerId());
        summary.put("workerName", data.getWorkerName());
        summary.put("companyName", data.getCompanyName());
        summary.put("department", data.getDepartment());
        summary.put("position", data.getPosition());
        summary.put("workDate", data.getWorkDate());
        summary.put("hoursWorked", data.getHoursWorked());
        summary.put("hourlyRate", data.getHourlyRate());
        summary.put("paymentAmount", data.getPaymentAmount());
        summary.put("bankAccount", data.getBankAccount());
        summary.put("phoneNumber", data.getPhoneNumber());
        summary.put("email", data.getEmail());
        summary.put("address", data.getAddress());
        summary.put("status", data.getStatus());
        summary.put("rejectionReason", data.getRejectionReason());
        summary.put("createdAt", data.getCreatedAt());
        summary.put("validatedAt", data.getValidatedAt());
        summary.put("processedAt", data.getProcessedAt());
        summary.put("receiptNumber", data.getReceiptNumber());
        
        return summary;
    }

    public Map<String, Object> getValidationResultsPaginated(String fileId, int page, int size, 
            String status, String startDate, String endDate, String sortBy, String sortDir) {
        log.info("Fetching paginated validation results for fileId={} with filters - page: {}, size: {}, status: {}, startDate: {}, endDate: {}", 
                fileId, page, size, status, startDate, endDate);
        
        try {
            // Create pageable with sorting
            org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                org.springframework.data.domain.Sort.by(sortBy).descending() : 
                org.springframework.data.domain.Sort.by(sortBy).ascending();
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size, sort);
            
            org.springframework.data.domain.Page<com.example.paymentflow.worker.entity.WorkerUploadedData> dataPage;
            
            // Handle date filtering
            if (startDate != null && endDate != null && 
                !startDate.trim().isEmpty() && !endDate.trim().isEmpty()) {
                
                java.time.LocalDateTime startDateTime = java.time.LocalDate.parse(startDate).atStartOfDay();
                java.time.LocalDateTime endDateTime = java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay().minusNanos(1);
                
                if (status != null && !status.trim().isEmpty()) {
                    // Filter by status and date range - need to add this method to service
                    dataPage = workerUploadedDataService.findByFileIdStatusAndDateRangePaginated(
                        fileId, status.trim().toUpperCase(), startDateTime, endDateTime, pageable);
                } else {
                    // Filter by date range only - need to add this method to service
                    dataPage = workerUploadedDataService.findByFileIdAndDateRangePaginated(
                        fileId, startDateTime, endDateTime, pageable);
                }
            } else if (status != null && !status.trim().isEmpty()) {
                dataPage = workerUploadedDataService.findByFileIdAndStatusPaginated(fileId, status.trim().toUpperCase(), pageable);
            } else {
                dataPage = workerUploadedDataService.findByFileIdPaginated(fileId, pageable);
            }
            
            // Convert to summary format
            List<Map<String, Object>> records = new ArrayList<>();
            for (com.example.paymentflow.worker.entity.WorkerUploadedData data : dataPage.getContent()) {
                records.add(createUploadedDataSummary(data));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("records", records);
            response.put("totalElements", dataPage.getTotalElements());
            response.put("totalPages", dataPage.getTotalPages());
            response.put("currentPage", dataPage.getNumber());
            response.put("pageSize", dataPage.getSize());
            response.put("hasNext", dataPage.hasNext());
            response.put("hasPrevious", dataPage.hasPrevious());
            response.put("fileId", fileId);
            
            // Add filtering info
            response.put("appliedFilters", Map.of(
                "status", status != null ? status : "all",
                "startDate", startDate != null ? startDate : "",
                "endDate", endDate != null ? endDate : ""
            ));
            
            return response;
            
        } catch (java.time.format.DateTimeParseException e) {
            log.error("Invalid date format provided", e);
            return Map.of("error", "Invalid date format. Use YYYY-MM-DD");
        } catch (Exception e) {
            log.error("Error fetching paginated validation results for fileId={}", fileId, e);
            return Map.of("error", "Failed to fetch results: " + e.getMessage());
        }
    }

    public Map<String, Object> generateRequest(String fileId) {
        log.info("Generating request for validated records in fileId={}", fileId);
        
        try {
            Long uploadedFileId = Long.parseLong(fileId);
            Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(uploadedFileId);
            
            if (uploadedFileOpt.isEmpty()) {
                return Map.of("error", "File not found");
            }
            
            UploadedFile uploadedFile = uploadedFileOpt.get();
            String uploadedFileRef = uploadedFile.getFileReferenceNumber();
            
            // Generate request for validated data (keep data in WorkerUploadedData with receipt number)
            int processedCount = workerUploadedDataService.generateRequestForValidatedData(fileId, uploadedFileRef);
            
            if (processedCount == 0) {
                return Map.of("error", "No validated records found to generate request");
            }
            
            // Update the uploaded file status
            uploadedFile.setStatus("REQUEST_GENERATED");
            uploadedFileRepository.save(uploadedFile);
            
            log.info("Request generated successfully for fileId={}: {} records processed with receipt numbers", fileId, processedCount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Request generated successfully");
            response.put("fileId", fileId);
            response.put("requestReference", uploadedFileRef);
            response.put("processedRecords", processedCount);
            response.put("status", "REQUEST_GENERATED");
            response.put("nextAction", "VIEW_REQUESTS");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error generating request for fileId={}", fileId, e);
            return Map.of("error", "Request generation failed: " + e.getMessage());
        }
    }

    public Map<String, Object> processValidRecords(String fileId) {
        log.info("Processing valid records for fileId={}", fileId);
        
        try {
            List<WorkerPayment> payments = workerPaymentService.findByFileId(fileId);
            
            int processedCount = 0;
            List<WorkerPayment> toUpdate = new ArrayList<>();
            
            for (WorkerPayment payment : payments) {
                // Only process records that passed validation
                if ("VALIDATED".equals(payment.getStatus())) {
                    payment.setStatus("PAYMENT_REQUESTED");
                    toUpdate.add(payment);
                    processedCount++;
                }
            }
            
            // Update statuses in bulk
            String receiptNumber = null;
            if (!toUpdate.isEmpty()) {
                // First, create the receipt for all processed payments
                WorkerPaymentReceipt receipt = receiptService.createReceipt(toUpdate);
                receiptNumber = receipt.getReceiptNumber();
                
                // Assign the receipt number to all processed payments
                for (WorkerPayment payment : toUpdate) {
                    payment.setReceiptNumber(receiptNumber);
                }
                
                // Update payments with new status and receipt number assignment
                workerPaymentService.updateBulk(toUpdate);
                
                log.info("Generated receipt {} for {} processed payments", receipt.getReceiptNumber(), processedCount);
            }
            
            // Keep uploaded file status as UPLOADED - do not change it to PROCESSED
            // The uploaded file status should remain UPLOADED only
            log.info("Keeping uploaded file status as UPLOADED for fileId={}", fileId);
            
            log.info("Receipt generation complete for fileId={}: {} records updated to PAID status", fileId, processedCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("processed", processedCount);
            if (receiptNumber != null) {
                result.put("receiptNumber", receiptNumber);
                result.put("status", "PROCESSED");
                result.put("nextAction", "RECEIPT_GENERATED");
                result.put("message", "Generated receipt for " + processedCount + " valid records. Receipt: " + receiptNumber);
            } else {
                result.put("status", "NO_VALID_RECORDS");
                result.put("nextAction", "VALIDATION_REQUIRED");
                result.put("message", "No valid records found to generate receipt.");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing records for fileId={}", fileId, e);
            return Map.of("error", "Processing failed: " + e.getMessage());
        }
    }

    public Map<String, Object> reuploadFailedRecords(String fileId, MultipartFile file) {
        log.info("Re-uploading failed records for fileId={} with file {}", fileId, file.getOriginalFilename());
        
        try {
            // Process the new file similar to initial upload
            Map<String, Object> result = handleFileUpload(file);
            
            if (result.containsKey("error")) {
                return result;
            }
            
            String newFileId = (String) result.get("fileId");
            
            // You could implement logic here to merge or replace failed records
            // For now, we'll just return success
            log.info("Re-upload complete: new fileId={}", newFileId);
            return Map.of("message", "Re-uploaded successfully with new file ID: " + newFileId);
            
        } catch (Exception e) {
            log.error("Error re-uploading file for fileId={}", fileId, e);
            return Map.of("error", "Re-upload failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getFileStatusSummary(String fileId) {
        log.info("Getting status summary for fileId={}", fileId);
        
        try {
            List<WorkerPayment> payments = workerPaymentService.findByFileId(fileId);
            
            Map<String, Integer> statusCounts = new HashMap<>();
            
            // Initialize counts
            String[] statuses = {"UPLOADED", "VALIDATED", "FAILED", "PROCESSED", "PAYMENT_REQUESTED", 
                               "PAYMENT_INITIATED", "PAYMENT_PROCESSED", "PAYMENT_RECONCILED", "GENERATED", "ERROR"};
            for (String status : statuses) {
                statusCounts.put(status, 0);
            }
            
            // Count by status
            for (WorkerPayment payment : payments) {
                String status = payment.getStatus();
                statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("totalRecords", payments.size());
            result.put("uploadedCount", statusCounts.get("UPLOADED"));
            result.put("validatedCount", statusCounts.get("VALIDATED"));
            result.put("failedCount", statusCounts.get("FAILED"));
            result.put("processedCount", statusCounts.get("PROCESSED"));
            result.put("paymentRequestedCount", statusCounts.get("PAYMENT_REQUESTED"));
            result.put("paymentInitiatedCount", statusCounts.get("PAYMENT_INITIATED"));
            result.put("paymentProcessedCount", statusCounts.get("PAYMENT_PROCESSED"));
            result.put("generatedCount", statusCounts.get("GENERATED"));
            result.put("errorCount", statusCounts.get("ERROR"));
            
            // Determine workflow status and next action
            String workflowStatus = determineWorkflowStatus(statusCounts);
            String nextAction = determineNextAction(workflowStatus, statusCounts);
            
            result.put("workflowStatus", workflowStatus);
            result.put("nextAction", nextAction);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error getting status summary for fileId={}", fileId, e);
            return Map.of("error", "Failed to get status summary: " + e.getMessage());
        }
    }
    
    private String determineWorkflowStatus(Map<String, Integer> statusCounts) {
        // Check if any records have been processed (receipts generated)
        if (statusCounts.get("PAYMENT_REQUESTED") > 0 || 
            statusCounts.get("GENERATED") > 0) {
            return "PROCESSED";
        }
        
        // Check if validation is complete
        if (statusCounts.get("VALIDATED") > 0 || 
            statusCounts.get("FAILED") > 0) {
            return "VALIDATED";
        }
        
        // Default to uploaded status
        if (statusCounts.get("UPLOADED") > 0) {
            return "UPLOADED";
        }
        
        return "UNKNOWN";
    }
    
    private String determineNextAction(String workflowStatus, Map<String, Integer> statusCounts) {
        switch (workflowStatus) {
            case "UPLOADED":
                return "START_VALIDATION";
            case "VALIDATED":
                // Only show generate receipt if there are validated records
                if (statusCounts.get("VALIDATED") > 0) {
                    return "GENERATE_RECEIPT";
                } else {
                    return "START_VALIDATION"; // All failed validation
                }
            case "PROCESSED":
                return "RECEIPT_GENERATED";
            default:
                return "START_VALIDATION";
        }
    }


    
    // Debug method to get worker payments by fileId
    public List<WorkerPayment> getWorkerPaymentsByFileId(String fileId) {
        return workerPaymentService.findByFileId(fileId);
    }

    private List<com.example.paymentflow.worker.entity.WorkerUploadedData> parseFileToUploadedData(
            File file, String originalFilename, String fileId) throws java.io.IOException {
        log.info("Parsing file {} to WorkerUploadedData format", originalFilename);

        String extension = getFileExtension(originalFilename);
        if ("csv".equalsIgnoreCase(extension)) {
            return parseCsvToUploadedData(file, fileId);
        }
        if ("xls".equalsIgnoreCase(extension) || "xlsx".equalsIgnoreCase(extension)) {
            return parseExcelToUploadedData(file, fileId);
        }

        throw new java.io.IOException("Unsupported file type: " + extension);
    }

    private List<com.example.paymentflow.worker.entity.WorkerUploadedData> parseCsvToUploadedData(
            File file, String fileId) throws java.io.IOException {
        List<com.example.paymentflow.worker.entity.WorkerUploadedData> uploadedDataList = new ArrayList<>();

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String header = br.readLine();
            if (header == null) {
                throw new java.io.IOException("File is empty or invalid");
            }

            int rowNumber = 1;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    com.example.paymentflow.worker.entity.WorkerUploadedData uploadedData =
                        parseCSVLineToUploadedData(line, fileId, rowNumber++);
                    uploadedDataList.add(uploadedData);
                } catch (Exception e) {
                    log.error("Error parsing CSV line {}: {}", rowNumber, e.getMessage());
                }
            }
        }

        log.info("Parsed {} records from CSV file", uploadedDataList.size());
        return uploadedDataList;
    }

    private List<com.example.paymentflow.worker.entity.WorkerUploadedData> parseExcelToUploadedData(
            File file, String fileId) throws java.io.IOException {
        List<com.example.paymentflow.worker.entity.WorkerUploadedData> uploadedDataList = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new java.io.IOException("No sheet found in uploaded workbook");
            }

            boolean isHeader = true;
            int rowNumber = 1;
            for (Row row : sheet) {
                if (row == null) {
                    continue;
                }
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] fields = extractExcelRow(row, formatter);
                if (isRowEmpty(fields)) {
                    continue;
                }

                try {
                    com.example.paymentflow.worker.entity.WorkerUploadedData uploadedData =
                        populateUploadedDataFromFields(fields, fileId, rowNumber++);
                    uploadedDataList.add(uploadedData);
                } catch (Exception e) {
                    log.error("Error parsing Excel row {}: {}", rowNumber, e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new java.io.IOException("Failed to read Excel file: " + e.getMessage(), e);
        }

        log.info("Parsed {} records from Excel file", uploadedDataList.size());
        return uploadedDataList;
    }

    private com.example.paymentflow.worker.entity.WorkerUploadedData parseCSVLineToUploadedData(
            String csvLine, String fileId, int rowNumber) {
        String[] fields = csvLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        return populateUploadedDataFromFields(fields, fileId, rowNumber);
    }
    
    private String cleanField(String field) {
        if (field == null) return null;
        // Remove quotes and trim whitespace
        field = field.trim();
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }
        return field.isEmpty() ? null : field;
    }

    private com.example.paymentflow.worker.entity.WorkerUploadedData populateUploadedDataFromFields(
            String[] rawFields, String fileId, int rowNumber) {
        com.example.paymentflow.worker.entity.WorkerUploadedData uploadedData =
            new com.example.paymentflow.worker.entity.WorkerUploadedData();

        uploadedData.setFileId(fileId);
        uploadedData.setRowNumber(rowNumber);
        uploadedData.setStatus("UPLOADED");
        uploadedData.setCreatedAt(java.time.LocalDateTime.now());

        // Check if we have the new CSV format with employer_id and toli_id (15 fields)
        // or the old format without them (13 fields)
        boolean isNewFormat = rawFields != null && rawFields.length >= 15;
        
        if (rawFields == null || rawFields.length < 13) {
            // Set default values for required fields to prevent null constraint violations
            uploadedData.setEmployerId("DEFAULT_EMPLOYER");
            uploadedData.setToliId("DEFAULT_TOLI");
            return uploadedData;
        }

        try {
            if (isNewFormat) {
                // New CSV format: worker_id,worker_name,employer_id,toli_id,company_name,department,position,work_date,hours_worked,hourly_rate,payment_amount,bank_account,phone_number,email,address
                uploadedData.setWorkerId(cleanField(rawFields[0]));
                uploadedData.setWorkerName(cleanField(rawFields[1]));
                uploadedData.setEmployerId(cleanField(rawFields[2])); // Now explicitly provided
                uploadedData.setToliId(cleanField(rawFields[3])); // Now explicitly provided
                uploadedData.setCompanyName(cleanField(rawFields[4]));
                uploadedData.setDepartment(cleanField(rawFields[5]));
                uploadedData.setPosition(cleanField(rawFields[6]));

                String workDateStr = cleanField(rawFields[7]);
                if (workDateStr != null && !workDateStr.isEmpty()) {
                    uploadedData.setWorkDate(parseToLocalDate(workDateStr));
                }

                String hoursStr = cleanField(rawFields[8]);
                if (hoursStr != null && !hoursStr.isEmpty()) {
                    uploadedData.setHoursWorked(new java.math.BigDecimal(hoursStr));
                }

                String rateStr = cleanField(rawFields[9]);
                if (rateStr != null && !rateStr.isEmpty()) {
                    uploadedData.setHourlyRate(new java.math.BigDecimal(rateStr));
                }

                String amountStr = cleanField(rawFields[10]);
                if (amountStr != null && !amountStr.isEmpty()) {
                    uploadedData.setPaymentAmount(new java.math.BigDecimal(amountStr));
                }

                uploadedData.setBankAccount(cleanField(rawFields[11]));
                uploadedData.setPhoneNumber(cleanField(rawFields[12]));
                uploadedData.setEmail(cleanField(rawFields[13]));
                uploadedData.setAddress(cleanField(rawFields[14]));
                
            } else {
                // Legacy CSV format: worker_id,worker_name,company_name,department,position,work_date,hours_worked,hourly_rate,payment_amount,bank_account,phone_number,email,address
                // Generate employer_id and toli_id from company_name and department
                uploadedData.setWorkerId(cleanField(rawFields[0]));
                uploadedData.setWorkerName(cleanField(rawFields[1]));
                uploadedData.setCompanyName(cleanField(rawFields[2]));
                uploadedData.setDepartment(cleanField(rawFields[3]));
                uploadedData.setPosition(cleanField(rawFields[4]));
                
                // Generate employer_id from company_name
                String companyName = cleanField(rawFields[2]);
                if (companyName != null && !companyName.trim().isEmpty()) {
                    String derivedEmployerId = "EMP_" + companyName.trim().toUpperCase().replaceAll("[^A-Z0-9]", "_");
                    if (derivedEmployerId.length() > 64) {
                        derivedEmployerId = derivedEmployerId.substring(0, 64);
                    }
                    uploadedData.setEmployerId(derivedEmployerId);
                } else {
                    uploadedData.setEmployerId("DEFAULT_EMPLOYER");
                }
                
                // Generate toli_id from department
                String department = cleanField(rawFields[3]);
                if (department != null && !department.trim().isEmpty()) {
                    String derivedToliId = "TOLI_" + department.trim().toUpperCase().replaceAll("[^A-Z0-9]", "_");
                    if (derivedToliId.length() > 64) {
                        derivedToliId = derivedToliId.substring(0, 64);
                    }
                    uploadedData.setToliId(derivedToliId);
                } else {
                    uploadedData.setToliId("DEFAULT_TOLI");
                }

                String workDateStr = cleanField(rawFields[5]);
                if (workDateStr != null && !workDateStr.isEmpty()) {
                    uploadedData.setWorkDate(parseToLocalDate(workDateStr));
                }

                String hoursStr = cleanField(rawFields[6]);
                if (hoursStr != null && !hoursStr.isEmpty()) {
                    uploadedData.setHoursWorked(new java.math.BigDecimal(hoursStr));
                }

                String rateStr = cleanField(rawFields[7]);
                if (rateStr != null && !rateStr.isEmpty()) {
                    uploadedData.setHourlyRate(new java.math.BigDecimal(rateStr));
                }

                String amountStr = cleanField(rawFields[8]);
                if (amountStr != null && !amountStr.isEmpty()) {
                    uploadedData.setPaymentAmount(new java.math.BigDecimal(amountStr));
                }

                uploadedData.setBankAccount(cleanField(rawFields[9]));
                uploadedData.setPhoneNumber(cleanField(rawFields[10]));
                uploadedData.setEmail(cleanField(rawFields[11]));
                uploadedData.setAddress(cleanField(rawFields[12]));
            }

        } catch (Exception e) {
            log.error("Error mapping uploaded data fields for row {}: {}", rowNumber, e.getMessage());
            throw new RuntimeException("Failed to map row fields: " + e.getMessage(), e);
        }

        return uploadedData;
    }

    private String[] extractExcelRow(Row row, DataFormatter formatter) {
        // Support both old format (13 fields) and new format (15 fields)
        int maxColumns = Math.max(15, row.getLastCellNum());
        String[] fields = new String[maxColumns];
        
        for (int i = 0; i < maxColumns; i++) {
            Cell cell = row.getCell(i);
            if (cell == null) {
                fields[i] = null;
                continue;
            }

            // Handle date formatting for work_date column
            // In new format: work_date is at index 7
            // In old format: work_date is at index 5
            if ((i == 7 || i == 5) && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                java.time.LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                fields[i] = date.toString();
            } else {
                fields[i] = formatter.formatCellValue(cell);
            }
        }
        return fields;
    }

    private boolean isRowEmpty(String[] fields) {
        if (fields == null) {
            return true;
        }
        for (String field : fields) {
            if (field != null && !field.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1) : "";
    }

    private java.time.LocalDate parseToLocalDate(String value) {
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException isoEx) {
            for (String pattern : List.of("d/M/uuuu", "M/d/uuuu")) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern(pattern);
                    return java.time.LocalDate.parse(value, formatter);
                } catch (java.time.format.DateTimeParseException ignored) {
                    // try next pattern
                }
            }
            throw isoEx;
        }
    }
}
