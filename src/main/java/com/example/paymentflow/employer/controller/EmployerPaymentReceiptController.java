package com.example.paymentflow.employer.controller;

import com.example.paymentflow.employer.entity.EmployerPaymentReceipt;
import com.example.paymentflow.employer.service.EmployerPaymentReceiptService;
import com.shared.common.annotation.Auditable;
import com.shared.common.annotation.SecurePagination;
import com.shared.common.dto.SecurePaginationRequest;
import com.shared.common.dto.SecurePaginationResponse;
import com.shared.common.util.ETagUtil;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    @SecurePagination
    public ResponseEntity<?> getAvailableReceiptsSecure(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @Valid @RequestBody
            SecurePaginationRequest request,
            HttpServletRequest httpRequest) {
        log.info("Fetching available receipts with secure pagination, status: {}, request: {}", request.getStatus(), request);
        try {
            // Apply pageToken if present (decodes token and sets page/size/sort)
            SecurePaginationUtil.applyPageToken(request);
            SecurePaginationUtil.ValidationResult validation =
                SecurePaginationUtil.validatePaginationRequest(request);
            if (!validation.isValid()) {
                return ResponseEntity.badRequest().body(
                    SecurePaginationUtil.createErrorResponse(validation));
            }
            
            // Create Pageable from request (either from decoded token or direct parameters)
            PageRequest pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(
                    Sort.Direction.fromString(request.getSortDir()),
                    request.getSortBy()
                )
            );
            
            // Fetch data using standard pagination
            Page<EmployerPaymentReceipt> receiptsPage =
                service.findAvailableByStatusAndDateRangePaginated(
                    request.getStatus(), 
                    validation.getStartDateTime(), 
                    validation.getEndDateTime(), 
                    pageable);
            SecurePaginationResponse<EmployerPaymentReceipt> response =
                SecurePaginationUtil.createSecureResponse(receiptsPage, request);
            String responseJson = objectMapper.writeValueAsString(response);
            String eTag = ETagUtil.generateETag(responseJson);
            String ifNoneMatch = httpRequest.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error fetching available receipts (secure)", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
