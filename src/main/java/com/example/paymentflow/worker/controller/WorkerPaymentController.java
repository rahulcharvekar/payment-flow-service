package com.example.paymentflow.worker.controller;

import com.example.paymentflow.worker.entity.WorkerPayment;
import com.example.paymentflow.worker.service.WorkerPaymentService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import com.shared.common.util.ETagUtil;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.shared.common.annotation.Auditable;

@RestController
@RequestMapping("/api/v1/worker-payments")
@Tag(name = "Worker Payment Management", description = "APIs for worker payment CRUD operations and filtering")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkerPaymentController {

    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerPaymentController.class);

    private final WorkerPaymentService service;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public WorkerPaymentController(WorkerPaymentService service, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Auditable(action = "CREATE_WORKER_PAYMENT", resourceType = "WORKER_PAYMENT", resourceId = "#result.body.id")
    public ResponseEntity<WorkerPayment> create(@RequestBody WorkerPayment workerPayment) {
        log.info("Creating worker payment for workerRef={}", workerPayment.getWorkerRef());
        WorkerPayment created = service.create(workerPayment);
        log.info("Created worker payment id={}", created.getId());
        
        return ResponseEntity.created(URI.create("/api/v1/worker-payments/" + created.getId()))
                .body(created);
    }

    @PostMapping("/secure")
    @Operation(summary = "Get worker payments with secure pagination and filtering",
               description = "Returns paginated worker payments with optional status and receipt number filters, using secure pagination (mandatory date range, opaque tokens)")
    @com.shared.common.annotation.SecurePagination
    public ResponseEntity<?> getPaymentsSecure(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @jakarta.validation.Valid @RequestBody
            com.shared.common.dto.SecurePaginationRequest request,
            @Parameter(description = "Receipt number filter") @RequestParam(required = false) String receiptNumber,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("Fetching worker payments with secure pagination, status: {}, receiptNumber: {}, request: {}", 
                request.getStatus(), receiptNumber, request);
        try {
            // Apply pageToken if present
            com.shared.common.util.SecurePaginationUtil.applyPageToken(request);
            com.shared.common.util.SecurePaginationUtil.ValidationResult validation =
                com.shared.common.util.SecurePaginationUtil.validatePaginationRequest(request);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest().body(
                    com.shared.common.util.SecurePaginationUtil.createErrorResponse(validation));
            }
            // Use only pageToken and filters for cursor-based pagination
            String nextPageToken = request.getPageToken();
            // Create sort object with secure field validation
            List<String> allowedSortFields = List.of("id", "name", "workerRef", "employerId", "paymentAmount", "status", "createdAt", "receiptNumber");
            org.springframework.data.domain.Sort sort = com.shared.common.util.SecurePaginationUtil.createSecureSort(request, allowedSortFields);
            
            org.springframework.data.domain.Page<WorkerPayment> paymentsPage =
                service.findByStatusAndReceiptNumberAndDateRangeWithToken(
                    request.getStatus(), receiptNumber, 
                    validation.getStartDateTime(), validation.getEndDateTime(), 
                    sort, 
                    nextPageToken);
            com.shared.common.dto.SecurePaginationResponse<WorkerPayment> response =
                com.shared.common.util.SecurePaginationUtil.createSecureResponse(paymentsPage, request);
            String responseJson = objectMapper.writeValueAsString(response);
            String eTag = com.shared.common.util.ETagUtil.generateETag(responseJson);
            String ifNoneMatch = httpRequest.getHeader(org.springframework.http.HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error fetching worker payments (secure)", e);
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkerPayment> findById(@PathVariable("id") Long id) {
        log.info("Fetching worker payment id={}", id);
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/by-reference-prefix")
    public ResponseEntity<List<WorkerPayment>> findByReferencePrefix(@RequestParam("prefix") String prefix) {
        log.info("Fetching worker payments with reference prefix={}", prefix);
        List<WorkerPayment> payments = service.findByRequestReferencePrefix(prefix);
        return ResponseEntity.ok(payments);
    }

    @PutMapping("/{id}")
    @Auditable(action = "UPDATE_WORKER_PAYMENT", resourceType = "WORKER_PAYMENT", resourceId = "#id")
    public ResponseEntity<WorkerPayment> update(@PathVariable("id") Long id, @RequestBody WorkerPayment workerPayment) {
        log.info("Updating worker payment id={}", id);
        WorkerPayment updated = service.update(id, workerPayment);
        
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE_WORKER_PAYMENT", resourceType = "WORKER_PAYMENT", resourceId = "#id")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.info("Deleting worker payment id={}", id);
        service.delete(id);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-uploaded-file-ref/{uploadedFileRef}")
    @Operation(summary = "Get worker payments by uploaded file reference", 
               description = "Returns all worker payments that originated from a specific uploaded file")
    public ResponseEntity<?> getByUploadedFileRef(
            @Parameter(description = "Uploaded file reference") 
            @PathVariable String uploadedFileRef,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20") 
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field", example = "id")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest request) {
        log.info("Fetching worker payments by uploadedFileRef={}", uploadedFileRef);
        try {
            // Validate sortBy against allowed fields for security
            List<String> allowedSortFields = List.of("id", "name", "workerRef", "employerId", "paymentAmount", "status", "createdAt", "receiptNumber");
            Sort sort = com.shared.common.util.SecurePaginationUtil.createSort(sortBy, sortDir, allowedSortFields);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<WorkerPayment> paymentsPage = service.findByUploadedFileRefPaginated(uploadedFileRef, pageable);
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("payments", paymentsPage.getContent());
            response.put("totalElements", paymentsPage.getTotalElements());
            response.put("totalPages", paymentsPage.getTotalPages());
            response.put("currentPage", paymentsPage.getNumber());
            response.put("pageSize", paymentsPage.getSize());
            response.put("hasNext", paymentsPage.hasNext());
            response.put("hasPrevious", paymentsPage.hasPrevious());
            response.put("uploadedFileRef", uploadedFileRef);

            // Generate ETag from response content
            String responseJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
            String eTag = ETagUtil.generateETag(responseJson);
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error fetching payments by uploadedFileRef: {}", uploadedFileRef, e);
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
