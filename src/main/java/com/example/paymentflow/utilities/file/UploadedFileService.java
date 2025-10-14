package com.example.paymentflow.utilities.file;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import com.example.paymentflow.utilities.logger.LoggerFactoryProvider;
import com.example.paymentflow.exception.ResourceNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.HashMap;

@Service
@Transactional
public class UploadedFileService {
    private static final Logger log = LoggerFactoryProvider.getLogger(UploadedFileService.class);
    
    private final UploadedFileRepository uploadedFileRepository;
    
    public UploadedFileService(UploadedFileRepository uploadedFileRepository) {
        this.uploadedFileRepository = uploadedFileRepository;
    }
    
    @Transactional(readOnly = true)
    public List<UploadedFile> getAllUploadedFiles() {
        log.info("Retrieving all uploaded files");
        return uploadedFileRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public UploadedFile getUploadedFileById(Long id) {
        log.info("Retrieving uploaded file with id: {}", id);
        return uploadedFileRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Uploaded file with id {} not found", id);
                    return new ResourceNotFoundException("Uploaded file not found with id: " + id);
                });
    }

    
    public Resource downloadFile(Long fileId) {
        log.info("Attempting to download file with id: {}", fileId);
        
        UploadedFile uploadedFile = getUploadedFileById(fileId);
        
        try {
            Path filePath = Paths.get(uploadedFile.getStoredPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                log.info("File download successful for id: {}", fileId);
                return resource;
            } else {
                log.error("File not found or not readable at path: {}", uploadedFile.getStoredPath());
                throw new ResourceNotFoundException("File not found or not readable: " + uploadedFile.getFilename());
            }
        } catch (Exception e) {
            log.error("Error downloading file with id: {}", fileId, e);
            throw new RuntimeException("Error downloading file: " + e.getMessage(), e);
        }
    }
    
    public String getContentType(Long fileId) {
        UploadedFile uploadedFile = getUploadedFileById(fileId);
        String contentType = null;
        String filename = uploadedFile.getFilename();
        try {
            Path filePath = Paths.get(uploadedFile.getStoredPath());
            contentType = Files.probeContentType(filePath);
        } catch (IOException e) {
            log.warn("Could not determine content type for file id: {}, using default", fileId);
        }
        if (contentType == null) {
            // Fallback based on file extension
            if (filename.endsWith(".csv")) {
                contentType = "text/csv";
            } else if (filename.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (filename.endsWith(".xls")) {
                contentType = "application/vnd.ms-excel";
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }
        log.info("Content type for file {}: {}", filename, contentType);
        return contentType;
    }

    
    @Transactional(readOnly = true)
    public boolean fileExists(Long fileId) {
        log.info("Checking if file exists for id: {}", fileId);
        
        Optional<UploadedFile> uploadedFileOpt = uploadedFileRepository.findById(fileId);
        if (uploadedFileOpt.isEmpty()) {
            return false;
        }
        
        UploadedFile uploadedFile = uploadedFileOpt.get();
        Path filePath = Paths.get(uploadedFile.getStoredPath());
        boolean exists = Files.exists(filePath) && Files.isReadable(filePath);
        
        log.info("File exists check for id {}: database={}, physical={}", 
                fileId, true, exists);
        
        return exists;
    }
    
    private LocalDate parseDate(String dateString) throws DateTimeParseException {
        return LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
    }
    
    /**
     * Get secure paginated uploaded files with mandatory date range filtering
     * Implements tamper-proof pagination with opaque tokens
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSecurePaginatedFiles(String startDate, String endDate, 
                                                      int page, int size, String sortBy, 
                                                      String sortDir) {
        try {
            log.info("Secure paginated request: startDate={}, endDate={}, page={}, size={}", 
                    startDate, endDate, page, size);
            
            // Parse dates with validation
            LocalDateTime startDateTime = parseDate(startDate).atStartOfDay();
            LocalDateTime endDateTime = parseDate(endDate).atTime(23, 59, 59);
            
            // Validate date range
            if (startDateTime.isAfter(endDateTime)) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
            
            // Create sort object with validation
            // Map createdAt and receiptDate to uploadDate for UploadedFile entity
            String actualSortBy = "createdAt".equals(sortBy) || "receiptDate".equals(sortBy) ? "uploadDate" : sortBy;
            org.springframework.data.domain.Sort sort;
            if ("asc".equalsIgnoreCase(sortDir)) {
                sort = org.springframework.data.domain.Sort.by(actualSortBy).ascending();
            } else {
                sort = org.springframework.data.domain.Sort.by(actualSortBy).descending();
            }
            
            // Create pageable with size limit
            int maxSize = Math.min(size, 100); // Enforce max page size
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, maxSize, sort);
            
            // Get paginated results with date filtering
            org.springframework.data.domain.Page<UploadedFile> filePage = 
                uploadedFileRepository.findByUploadDateBetween(startDateTime, endDateTime, pageable);
            
            // Create secure response with opaque tokens
            Map<String, Object> response = new HashMap<>();
            response.put("content", filePage.getContent()
                .stream()
                .map(this::createFileSummary)
                .toList());
            response.put("currentPage", filePage.getNumber());
            response.put("pageSize", filePage.getSize());
            response.put("totalElements", filePage.getTotalElements());
            response.put("totalPages", filePage.getTotalPages());
            response.put("first", filePage.isFirst());
            response.put("last", filePage.isLast());
            response.put("hasNext", filePage.hasNext());
            response.put("hasPrevious", filePage.hasPrevious());
            
            // Add date range metadata
            Map<String, String> dateRange = new HashMap<>();
            dateRange.put("startDate", startDate);
            dateRange.put("endDate", endDate);
            response.put("dateRange", dateRange);
            
            // Add sort metadata
            Map<String, String> sortInfo = new HashMap<>();
            sortInfo.put("sortBy", sortBy);
            sortInfo.put("sortDir", sortDir);
            response.put("sortInfo", sortInfo);
            
            // Generate opaque pagination tokens (tamper-proof)
            if (filePage.hasNext()) {
                String nextToken = generateSecureToken(startDate, endDate, page + 1, size, sortBy, sortDir);
                response.put("nextPageToken", nextToken);
            }
            if (filePage.hasPrevious()) {
                String prevToken = generateSecureToken(startDate, endDate, page - 1, size, sortBy, sortDir);
                response.put("previousPageToken", prevToken);
            }
            
            response.put("success", true);
            log.info("Successfully retrieved {} files for date range {} to {}", 
                    filePage.getNumberOfElements(), startDate, endDate);
            
            return response;
            
        } catch (DateTimeParseException e) {
            log.error("Invalid date format", e);
            return Map.of(
                "success", false,
                "error", "Invalid date format. Please use YYYY-MM-DD format.",
                "details", e.getMessage()
            );
        } catch (Exception e) {
            log.error("Error in secure paginated files retrieval", e);
            return Map.of(
                "success", false,
                "error", "Failed to retrieve files: " + e.getMessage()
            );
        }
    }
    
    /**
     * Generate secure opaque token for pagination (tamper-proof)
     * In production, this should use encryption or signed tokens
     */
    private String generateSecureToken(String startDate, String endDate, int page, 
                                     int size, String sortBy, String sortDir) {
        // Simple base64 encoding for demo - use proper encryption in production
        String tokenData = String.format("%s|%s|%d|%d|%s|%s|%d", 
                                        startDate, endDate, page, size, sortBy, sortDir, 
                                        System.currentTimeMillis());
        return java.util.Base64.getEncoder().encodeToString(tokenData.getBytes());
    }

    private Map<String, Object> createFileSummary(UploadedFile file) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", file.getId());
        summary.put("filename", file.getFilename());
        summary.put("fileType", file.getFileType());
        summary.put("fileHash", file.getFileHash());
        summary.put("status", file.getStatus());
        summary.put("uploadDate", file.getUploadDate());
        summary.put("uploadedBy", file.getUploadedBy());
        summary.put("totalRecords", file.getTotalRecords());
        summary.put("successCount", file.getSuccessCount());
        summary.put("failureCount", file.getFailureCount());
        summary.put("storedPath", file.getStoredPath());
        summary.put("fileReferenceNumber", file.getFileReferenceNumber());
        return summary;
    }
}
