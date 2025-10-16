package com.example.paymentflow.worker.controller;

import com.example.paymentflow.worker.entity.WorkerUploadedData;
import com.example.paymentflow.worker.service.WorkerUploadedDataService;
import com.example.paymentflow.worker.service.WorkerPaymentFileService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import com.shared.common.util.ETagUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;


import java.util.HashMap;
import java.util.Map;
import com.shared.audit.AuditHelper;

@RestController
@RequestMapping("/api/worker/uploaded-data")
@Tag(name = "Worker Uploaded Data Management", description = "APIs for managing worker uploaded data validation and processing")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkerUploadedDataController {
    
    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerUploadedDataController.class);
    
    private final WorkerUploadedDataService service;
    private final AuditHelper auditHelper;
    
    @Autowired
    private WorkerPaymentFileService fileService;

    public WorkerUploadedDataController(WorkerUploadedDataService service, AuditHelper auditHelper) {
        this.service = service;
        this.auditHelper = auditHelper;
    }

    // NEW: Secure paginated endpoint with mandatory date range filtering
    @PostMapping("/secure-paginated")
    @Operation(summary = "Get secure paginated uploaded data", 
               description = "Retrieve paginated uploaded data with MANDATORY date range filtering for security. " +
                           "Prevents unrestricted data access and implements tamper-proof pagination tokens.")
    @com.shared.common.annotation.SecurePagination
    @com.shared.common.annotation.UiType(value = com.shared.common.util.UiTypes.LIST, usage = "Display paginated list of uploaded data with sorting and filtering")
    public ResponseEntity<?> getSecurePaginatedUploadedData(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @jakarta.validation.Valid @RequestBody 
            com.shared.common.dto.SecurePaginationRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("Secure paginated request for uploaded data: {}", request);
        try {
            // Apply pageToken if present
            com.shared.common.util.SecurePaginationUtil.applyPageToken(request);
            // Validate request using utility
            com.shared.common.util.SecurePaginationUtil.ValidationResult validation = 
                com.shared.common.util.SecurePaginationUtil.validatePaginationRequest(request);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest().body(
                    com.shared.common.util.SecurePaginationUtil.createErrorResponse(validation));
            }
            // Create pageable with secure field validation
            List<String> allowedSortFields = List.of("id", "workerName", "employerId", "paymentAmount", "status", "createdAt", "workDate", "receiptNumber");
            org.springframework.data.domain.Sort sort = com.shared.common.util.SecurePaginationUtil.createSecureSort(request, allowedSortFields);
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                request.getPage(), Math.min(request.getSize(), 100), sort);
            // Get paginated data with date filtering
            org.springframework.data.domain.Page<WorkerUploadedData> dataPage = 
                service.findByDateRangePaginated(validation.getStartDateTime(), validation.getEndDateTime(), pageable);
            // Create secure response with opaque tokens
            com.shared.common.dto.SecurePaginationResponse<WorkerUploadedData> response = 
                com.shared.common.util.SecurePaginationUtil.createSecureResponse(dataPage, request);
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String responseJson = objectMapper.writeValueAsString(response);
            String eTag = com.shared.common.util.ETagUtil.generateETag(responseJson);
            String ifNoneMatch = httpRequest.getHeader(org.springframework.http.HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error in secure paginated data retrieval", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to retrieve data: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload worker payment file", 
               description = "Upload CSV, XLS, or XLSX file containing worker payment data. Returns fileId for subsequent operations.")
    @com.shared.common.annotation.UiType(value = com.shared.common.util.UiTypes.UPLOAD, usage = "File upload button for worker payment data")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // File type check
            String filename = file.getOriginalFilename();
            if (filename == null || !(filename.toLowerCase().endsWith(".csv") || 
                                    filename.toLowerCase().endsWith(".xls") || 
                                    filename.toLowerCase().endsWith(".xlsx"))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "failed",
                    "error", "Only .csv, .xls, and .xlsx files are allowed.",
                    "message", "File upload failed due to unsupported file type"
                ));
            }
            
            // File size check (max 200MB)
            long maxSize = 200L * 1024 * 1024; // 200MB
            if (file.getSize() > maxSize) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "failed",
                    "error", "File size exceeds 200MB limit.",
                    "message", "File upload failed due to size limit exceeded"
                ));
            }

            // Empty file check
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "failed",
                    "error", "Uploaded file is empty.",
                    "message", "File upload failed due to empty file"
                ));
            }

            Map<String, Object> result = fileService.handleFileUpload(file);
            
            if (result.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
            
            auditHelper.recordAudit("UPLOAD_WORKER_PAYMENT_FILE", "WORKER_UPLOADED_DATA", result.get("fileId").toString(), "SUCCESS", Map.of("filename", filename, "fileSize", file.getSize()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("File upload failed", e);
            
            auditHelper.recordAudit("UPLOAD_WORKER_PAYMENT_FILE", "WORKER_UPLOADED_DATA", null, "FAILURE", Map.of("filename", file.getOriginalFilename(), "error", e.getMessage()));
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "failed",
                "error", "File upload failed: " + e.getMessage(),
                "message", "Internal server error during file upload"
            ));
        }
    }

    @PostMapping("/files/secure-summaries")
    @Operation(summary = "Get secure paginated file summaries", 
               description = "Returns paginated list of all uploaded files with comprehensive summaries, validation counts, and total amounts. Uses secure pagination with mandatory date range and opaque tokens.")
    @com.shared.common.annotation.SecurePagination
    public ResponseEntity<?> getSecurePaginatedFileSummaries(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @jakarta.validation.Valid @RequestBody 
            com.shared.common.dto.SecurePaginationRequest request) {
        log.info("Secure paginated file summaries request: {}", request);
        com.shared.common.util.SecurePaginationUtil.ValidationResult validation = 
            com.shared.common.util.SecurePaginationUtil.validatePaginationRequest(request);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(
                com.shared.common.util.SecurePaginationUtil.createErrorResponse(validation));
        }
        try {
            // Only use nextPageToken and filters for cursor-based pagination
            String status = request.getStatus();
            String startDate = validation.getStartDateTime().toLocalDate().toString();
            String endDate = validation.getEndDateTime().toLocalDate().toString();
            String sortBy = request.getSortBy() != null ? request.getSortBy() : "uploadDate";
            String sortDir = request.getSortDir() != null ? request.getSortDir() : "desc";
            String nextPageToken = request.getPageToken();

            // Use service to get paginated file summaries (one per file) using cursor
            Map<String, Object> paginatedSummaries = service.getPaginatedFileSummariesWithToken(
                nextPageToken, null, status, startDate, endDate, sortBy, sortDir);
            return ResponseEntity.ok(paginatedSummaries);
        } catch (Exception e) {
            log.error("Error in secure paginated file summaries retrieval", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get file summaries: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    @PostMapping("/file/{fileId}/validate")
    @Operation(summary = "Validate uploaded data", 
               description = "Validates all uploaded data for a specific file and updates uploaded file status")
    public ResponseEntity<?> validateUploadedData(
            @Parameter(description = "File ID") 
            @PathVariable String fileId) {
        log.info("Starting validation for fileId: {}", fileId);
        
        try {
            // Use the comprehensive validation from WorkerPaymentFileService
            // This handles both validation and uploaded file status updates
            Map<String, Object> result = fileService.validateFileRecords(fileId);
            
            if (result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }
            
            auditHelper.recordAudit("VALIDATE_UPLOADED_DATA", "WORKER_UPLOADED_DATA", fileId, "SUCCESS", null);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error validating uploaded data for fileId: {}", fileId, e);
            
            auditHelper.recordAudit("VALIDATE_UPLOADED_DATA", "WORKER_UPLOADED_DATA", fileId, "FAILURE", Map.of("error", e.getMessage()));
            
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/results/{fileId}")
    @Operation(summary = "Get uploaded data results with pagination and filtering", 
               description = "Returns paginated uploaded data results with optional status and date range filtering")
    public ResponseEntity<?> getValidationResults(
            @Parameter(description = "File ID") 
            @PathVariable String fileId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20") 
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Status filter", example = "VALIDATED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Start date for range filter (YYYY-MM-DD) - MANDATORY", example = "2024-01-01")
            @RequestParam(required = true) String startDate,
            @Parameter(description = "End date for range filter (YYYY-MM-DD) - MANDATORY", example = "2024-01-31")
            @RequestParam(required = true) String endDate,
            @Parameter(description = "Sort field", example = "rowNumber")
            @RequestParam(defaultValue = "rowNumber") String sortBy,
            @Parameter(description = "Sort direction", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest request) {
        
        try {
            Map<String, Object> result = fileService.getValidationResultsPaginated(
                fileId, page, size, status, startDate, endDate, sortBy, sortDir);
            
            if (result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }
            
            com.fasterxml.jackson.databind.ObjectMapper objectMapper2 = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper2.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String responseJson = objectMapper2.writeValueAsString(result);
            String eTag = ETagUtil.generateETag(responseJson);
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(result);
            
        } catch (Exception e) {
            log.error("Error fetching validation results for fileId: {}", fileId, e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to fetch validation results: " + e.getMessage(),
                "fileId", fileId
            ));
        }
    }

    @PostMapping("/file/{fileId}/generate-request")
    @Operation(summary = "Generate request for validated data", 
               description = "Generates request numbers for validated data (keeps data in same table)")
    public ResponseEntity<?> generateRequestForValidatedData(
            @Parameter(description = "File ID") 
            @PathVariable String fileId,
            @RequestBody(required = false) ProcessRequest request) {
        log.info("Generating request for validated data in fileId: {}", fileId);
        
        try {
            String uploadedFileRef = request != null && request.getUploadedFileRef() != null 
                ? request.getUploadedFileRef() : fileId;
            
            int processedCount = service.generateRequestForValidatedData(fileId, uploadedFileRef);
            
            // Get updated summary
            Map<String, Integer> summary = service.getFileStatusSummary(fileId);
            
            auditHelper.recordAudit("GENERATE_REQUEST_FOR_VALIDATED_DATA", "WORKER_UPLOADED_DATA", fileId, "SUCCESS", Map.of("processedRecords", processedCount));
            
            return ResponseEntity.ok(Map.of(
                "message", "Request generated successfully",
                "fileId", fileId,
                "processedRecords", processedCount,
                "summary", summary
            ));
            
        } catch (Exception e) {
            log.error("Error generating request for validated data in fileId: {}", fileId, e);
            
            auditHelper.recordAudit("GENERATE_REQUEST_FOR_VALIDATED_DATA", "WORKER_UPLOADED_DATA", fileId, "FAILURE", Map.of("error", e.getMessage()));
            
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/file/{fileId}")
    @Operation(summary = "Delete uploaded data", 
               description = "Deletes all uploaded data for a specific file")
    public ResponseEntity<?> deleteUploadedData(
            @Parameter(description = "File ID") 
            @PathVariable String fileId) {
        log.info("Deleting uploaded data for fileId: {}", fileId);
        
        try {
            service.deleteByFileId(fileId);
            
            auditHelper.recordAudit("DELETE_UPLOADED_DATA", "WORKER_UPLOADED_DATA", fileId, "SUCCESS", null);
            
            return ResponseEntity.ok(Map.of(
                "message", "Uploaded data deleted successfully",
                "fileId", fileId
            ));
            
        } catch (Exception e) {
            log.error("Error deleting uploaded data for fileId: {}", fileId, e);
            
            auditHelper.recordAudit("DELETE_UPLOADED_DATA", "WORKER_UPLOADED_DATA", fileId, "FAILURE", Map.of("error", e.getMessage()));
            
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/receipt/{receiptNumber}")
    @Operation(summary = "Get request details by receipt number", 
               description = "Returns all records associated with a specific receipt number")
    public ResponseEntity<?> getRequestByReceiptNumber(
            @Parameter(description = "Receipt number") 
            @PathVariable String receiptNumber,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20") 
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        log.info("Fetching request details for receiptNumber: {}", receiptNumber);
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("rowNumber").ascending());
            Page<WorkerUploadedData> requestPage = service.findByReceiptNumberPaginated(receiptNumber, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("requestDetails", requestPage.getContent());
            response.put("totalElements", requestPage.getTotalElements());
            response.put("totalPages", requestPage.getTotalPages());
            response.put("currentPage", requestPage.getNumber());
            response.put("pageSize", requestPage.getSize());
            response.put("hasNext", requestPage.hasNext());
            response.put("hasPrevious", requestPage.hasPrevious());
            response.put("receiptNumber", receiptNumber);
            
            com.fasterxml.jackson.databind.ObjectMapper objectMapper3 = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper3.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String responseJson = objectMapper3.writeValueAsString(response);
            String eTag = ETagUtil.generateETag(responseJson);
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
            
        } catch (Exception e) {
            log.error("Error fetching request details for receiptNumber: {}", receiptNumber, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Request DTO class
    public static class ProcessRequest {
        private String uploadedFileRef;

        public String getUploadedFileRef() {
            return uploadedFileRef;
        }

        public void setUploadedFileRef(String uploadedFileRef) {
            this.uploadedFileRef = uploadedFileRef;
        }
    }
}
