package com.example.paymentflow.utilities.file;

import com.example.paymentflow.audit.annotation.Audited;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import com.example.paymentflow.utilities.logger.LoggerFactoryProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/uploaded-files")
@Tag(name = "Uploaded Files", description = "API for managing and retrieving uploaded files")
@SecurityRequirement(name = "Bearer Authentication")
public class UploadedFileController {
    
    private static final Logger log = LoggerFactoryProvider.getLogger(UploadedFileController.class);
    
    private final UploadedFileService uploadedFileService;

    public UploadedFileController(UploadedFileService uploadedFileService) {
        this.uploadedFileService = uploadedFileService;
    }

    @Operation(summary = "Get paginated uploaded files with secure date filtering", 
               description = "Retrieve paginated list of uploaded files with MANDATORY date range filtering for security. " +
                           "Prevents unrestricted data access and implements tamper-proof pagination tokens.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved uploaded files"),
        @ApiResponse(responseCode = "400", description = "Invalid date format or missing mandatory dates"),
        @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping("/secure-paginated")
    @Audited(action = "GET_SECURE_PAGINATED_FILES", resourceType = "UPLOADED_FILE")
    public ResponseEntity<?> getSecurePaginatedUploadedFiles(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @jakarta.validation.Valid @RequestBody 
            com.example.paymentflow.common.dto.SecurePaginationRequest request) {
        
        log.info("Secure paginated request for uploaded files: startDate={}, endDate={}, page={}, size={}", 
                request.getStartDate(), request.getEndDate(), request.getPage(), request.getSize());
        
        try {
            // Call service method with secure pagination (to be implemented)
            Map<String, Object> result = uploadedFileService.getSecurePaginatedFiles(
                request.getStartDate(), request.getEndDate(), request.getPage(), 
                request.getSize(), request.getSortBy(), request.getSortDir());
                
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error in secure paginated files retrieval", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to retrieve files: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    @Operation(summary = "Get all uploaded files (DEPRECATED)", 
               description = "DEPRECATED: Use /secure-paginated endpoint instead. This endpoint will be removed in future versions.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved uploaded files")
    @GetMapping
    @Deprecated
    public ResponseEntity<List<UploadedFile>> getAllUploadedFiles() {
        log.warn("DEPRECATED endpoint called: /api/uploaded-files - Use /secure-paginated instead");
        List<UploadedFile> files = uploadedFileService.getAllUploadedFiles();
        return ResponseEntity.ok(files);
    }



    @Operation(summary = "Download uploaded file", description = "Download the actual file content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File downloaded successfully", 
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "500", description = "Error reading file")
    })
    @GetMapping("/{id}/download")
    @Audited(action = "DOWNLOAD_UPLOADED_FILE", resourceType = "UPLOADED_FILE")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "ID of the file to download", required = true)
            @PathVariable Long id) {
        log.info("Request to download file with id: {}", id);
        
        try {
            Resource resource = uploadedFileService.downloadFile(id);
            String contentType = uploadedFileService.getContentType(id);
            UploadedFile fileMetadata = uploadedFileService.getUploadedFileById(id);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + fileMetadata.getFilename() + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.contentLength()))
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Error downloading file with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }




}
