package com.example.paymentflow.employer.controller;

import com.example.paymentflow.employer.entity.EmployerPaymentReceipt;
import com.example.paymentflow.employer.service.EmployerPaymentReceiptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import com.shared.common.annotation.Auditable;

@RestController
@RequestMapping("/api/employer/receipts")
@Tag(name = "Employer Receipt Management", description = "APIs for employer receipt validation and processing")
@SecurityRequirement(name = "Bearer Authentication")
public class EmployerPaymentReceiptController {
    
    private static final Logger log = LoggerFactoryProvider.getLogger(EmployerPaymentReceiptController.class);
    
    private final EmployerPaymentReceiptService service;
    private final ObjectMapper objectMapper;

    public EmployerPaymentReceiptController(EmployerPaymentReceiptService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/available/secure")
    @Operation(summary = "Get available worker receipts with secure pagination and filtering",
               description = "Returns paginated worker receipts with optional status and date range filters, using secure pagination (mandatory date range, opaque tokens)")
    @com.shared.common.annotation.SecurePagination
    public ResponseEntity<?> getAvailableReceiptsSecure(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @jakarta.validation.Valid @RequestBody
            com.shared.common.dto.SecurePaginationRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("Fetching available receipts with secure pagination, status: {}, request: {}", request.getStatus(), request);
        try {
            // Apply pageToken if present
            com.shared.common.util.SecurePaginationUtil.applyPageToken(request);
            com.shared.common.util.SecurePaginationUtil.ValidationResult validation =
                com.shared.common.util.SecurePaginationUtil.validatePaginationRequest(request);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest().body(
                    com.shared.common.util.SecurePaginationUtil.createErrorResponse(validation));
            }
            // Use only nextPageToken and filters for cursor-based pagination
            String nextPageToken = request.getPageToken();
            org.springframework.data.domain.Page<EmployerPaymentReceipt> receiptsPage =
                service.findAvailableByStatusAndDateRangeWithToken(request.getStatus(), validation.getStartDateTime(), validation.getEndDateTime(), nextPageToken);
            com.shared.common.dto.SecurePaginationResponse<EmployerPaymentReceipt> response =
                com.shared.common.util.SecurePaginationUtil.createSecureResponse(receiptsPage, request);
            String responseJson = objectMapper.writeValueAsString(response);
            String eTag = com.shared.common.util.ETagUtil.generateETag(responseJson);
            String ifNoneMatch = httpRequest.getHeader(org.springframework.http.HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error fetching available receipts (secure)", e);
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate worker receipt and create employer receipt", 
               description = "Validates a worker receipt with transaction reference and creates employer receipt")
    @Auditable(action = "VALIDATE_EMPLOYER_RECEIPT", resourceType = "EMPLOYER_PAYMENT_RECEIPT", resourceId = "#request.workerReceiptNumber")
    public ResponseEntity<?> validateReceipt(@RequestBody ReceiptValidationRequest request) {
        log.info("Validating worker receipt: {} with transaction reference: {}", 
                request.getWorkerReceiptNumber(), request.getTransactionReference());
        
        try {
            EmployerPaymentReceipt employerReceipt = service.validateAndCreateEmployerReceipt(
                request.getWorkerReceiptNumber(),
                request.getTransactionReference(),
                request.getValidatedBy()
            );
            
            return ResponseEntity.ok(Map.of(
                "message", "Receipt validated successfully",
                "employerReceiptNumber", employerReceipt.getEmployerReceiptNumber(),
                "workerReceiptNumber", employerReceipt.getWorkerReceiptNumber(),
                "transactionReference", employerReceipt.getTransactionReference(),
                "totalAmount", employerReceipt.getTotalAmount(),
                "totalRecords", employerReceipt.getTotalRecords(),
                "validatedAt", employerReceipt.getValidatedAt()
            ));
            
        } catch (Exception e) {
            log.error("Error validating receipt: {}", request.getWorkerReceiptNumber(), e);
            
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Request DTO class
    public static class ReceiptValidationRequest {
        private String workerReceiptNumber;
        private String transactionReference;
        private String validatedBy;

        public String getWorkerReceiptNumber() {
            return workerReceiptNumber;
        }

        public void setWorkerReceiptNumber(String workerReceiptNumber) {
            this.workerReceiptNumber = workerReceiptNumber;
        }

        public String getTransactionReference() {
            return transactionReference;
        }

        public void setTransactionReference(String transactionReference) {
            this.transactionReference = transactionReference;
        }

        public String getValidatedBy() {
            return validatedBy;
        }

        public void setValidatedBy(String validatedBy) {
            this.validatedBy = validatedBy;
        }
    }
}
