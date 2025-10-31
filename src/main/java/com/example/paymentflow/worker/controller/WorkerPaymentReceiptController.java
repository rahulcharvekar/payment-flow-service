package com.example.paymentflow.worker.controller;

import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import com.example.paymentflow.worker.service.WorkerPaymentReceiptService;
import com.example.paymentflow.worker.service.WorkerPaymentService;
import com.example.paymentflow.employer.service.EmployerPaymentReceiptService;
import com.shared.common.annotation.SecurePagination;
import com.shared.common.dto.SecurePaginationRequest;
import com.shared.common.dto.SecurePaginationResponse;
import com.shared.common.util.ETagUtil;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/worker/receipts")
@Tag(name = "Worker Receipt Management", description = "APIs for worker receipt management and tracking")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkerPaymentReceiptController {
    
    private static final Logger log = LoggerFactoryProvider.getLogger(WorkerPaymentReceiptController.class);
    
    private final WorkerPaymentReceiptService service;
    
    @Autowired
    private EmployerPaymentReceiptService employerReceiptService;
    
    @Autowired
    private WorkerPaymentService workerPaymentService;

    public WorkerPaymentReceiptController(WorkerPaymentReceiptService service) {
        this.service = service;
    }









    @PostMapping("/all/secure")
    @Operation(summary = "Get all worker receipts with secure pagination and filtering", 
               description = "Returns paginated worker receipts with optional status filter and secure pagination (mandatory date range, opaque tokens)")
    @SecurePagination
    public ResponseEntity<?> getAllWorkerReceiptsSecure(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @Valid @RequestBody 
            SecurePaginationRequest request,
            HttpServletRequest httpRequest) {
        log.info("Fetching worker receipts with secure pagination, status: {}, request: {}", request.getStatus(), request);
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
            Page<WorkerPaymentReceipt> receiptsPage;
            if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
                receiptsPage = service.findByStatusAndDateRangePaginated(
                    request.getStatus().trim().toUpperCase(), 
                    validation.getStartDateTime(), 
                    validation.getEndDateTime(), 
                    pageable);
            } else {
                receiptsPage = service.findByDateRangePaginated(
                    validation.getStartDateTime(), 
                    validation.getEndDateTime(), 
                    pageable);
            }
            SecurePaginationResponse<WorkerPaymentReceipt> response = 
                SecurePaginationUtil.createSecureResponse(receiptsPage, request);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            String responseJson = objectMapper.writeValueAsString(response);
            String eTag = ETagUtil.generateETag(responseJson);
            String ifNoneMatch = httpRequest.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error fetching worker receipts (secure)", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }



    @GetMapping("/{receiptNumber}")
    @Operation(summary = "Get worker receipt by receipt number", 
               description = "Returns worker receipt details for a specific receipt number")
    public ResponseEntity<?> getByReceiptNumber(
            @Parameter(description = "Worker receipt number") 
            @PathVariable String receiptNumber,
            HttpServletRequest request) {
        log.info("Fetching worker receipt: {}", receiptNumber);
        
        try {
            return service.findByReceiptNumber(receiptNumber)
                    .map(receipt -> {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                            String responseJson = objectMapper.writeValueAsString(receipt);
                            String eTag = ETagUtil.generateETag(responseJson);
                            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
                            if (eTag.equals(ifNoneMatch)) {
                                return ResponseEntity.status(304).eTag(eTag).build();
                            }
                            return ResponseEntity.ok().eTag(eTag).body(receipt);
                        } catch (Exception ex) {
                            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching receipt: {}", receiptNumber, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }





    @PostMapping("/{receiptNumber}/send-to-employer")
    @Operation(summary = "Send worker receipt to employer for validation", 
               description = "Creates a pending employer receipt for manual review and validation")
    public ResponseEntity<?> sendReceiptToEmployer(
            @Parameter(description = "Worker receipt number") 
            @PathVariable String receiptNumber) {
        log.info("Sending worker receipt {} to employer for validation", receiptNumber);
        
        try {
            // Get the worker receipt
            java.util.Optional<WorkerPaymentReceipt> workerReceiptOpt = service.findByReceiptNumber(receiptNumber);
            
            if (workerReceiptOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Worker receipt not found",
                    "receiptNumber", receiptNumber
                ));
            }
            
            WorkerPaymentReceipt workerReceipt = workerReceiptOpt.get();
            
            // Create employer receipt manually using injected service
            com.example.paymentflow.employer.entity.EmployerPaymentReceipt employerReceipt = 
                employerReceiptService.createPendingEmployerReceipt(workerReceipt);
            
            // Update worker receipt status to PAYMENT_INITIATED
            service.updateStatus(receiptNumber, "PAYMENT_INITIATED");
            log.info("Updated worker receipt {} status to PAYMENT_INITIATED", receiptNumber);
            
            // Update all related worker payment records to PAYMENT_INITIATED
            java.util.List<com.example.paymentflow.worker.entity.WorkerPayment> workerPayments = 
                workerPaymentService.findByReceiptNumber(receiptNumber, 
                    PageRequest.of(0, 1000)).getContent();
            
            int updatedPayments = 0;
            for (com.example.paymentflow.worker.entity.WorkerPayment payment : workerPayments) {
                payment.setStatus("PAYMENT_INITIATED");
                workerPaymentService.save(payment);
                updatedPayments++;
            }
            log.info("Updated {} worker payment records to PAYMENT_INITIATED for receipt {}", updatedPayments, receiptNumber);
            return ResponseEntity.ok(Map.of(
                "message", "Worker receipt sent to employer successfully",
                "workerReceiptNumber", receiptNumber,
                "employerReceiptNumber", employerReceipt.getEmployerReceiptNumber(),
                "workerReceiptStatus", "PAYMENT_INITIATED",
                "employerReceiptStatus", "PENDING",
                "totalRecords", employerReceipt.getTotalRecords(),
                "totalAmount", employerReceipt.getTotalAmount(),
                "updatedPaymentRecords", updatedPayments
            ));
            
        } catch (Exception e) {
            log.error("Error sending worker receipt {} to employer", receiptNumber, e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to send receipt to employer: " + e.getMessage(),
                "receiptNumber", receiptNumber
            ));
        }
    }


}
