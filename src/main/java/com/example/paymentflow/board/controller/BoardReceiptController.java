package com.example.paymentflow.board.controller;

import com.shared.audit.annotation.Audited;
import com.example.paymentflow.board.entity.BoardReceiptProcessRequest;

import com.example.paymentflow.board.entity.BoardReceipt;
import com.example.paymentflow.board.service.BoardReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import com.shared.common.util.ETagUtil;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/board-receipts")
@Tag(name = "Board Receipt Management", description = "APIs for board receipt processing and management")
@SecurityRequirement(name = "Bearer Authentication")
public class BoardReceiptController {

    private static final Logger log = LoggerFactoryProvider.getLogger(BoardReceiptController.class);

    private final BoardReceiptService service;

    public BoardReceiptController(BoardReceiptService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BoardReceipt> create(@RequestBody BoardReceipt boardReceipt) {
        log.info("Creating board receipt for boardRef={}", boardReceipt.getBoardRef());
        BoardReceipt created = service.create(boardReceipt);
        log.info("Created board receipt id={}", created.getId());
        return ResponseEntity.created(URI.create("/api/v1/board-receipts/" + created.getId()))
                .body(created);
    }

    @PostMapping("/secure")
    @Operation(summary = "Get all board receipts with secure pagination and filtering",
               description = "Returns paginated board receipts with optional status and date range filters, using secure pagination (mandatory date range, opaque tokens)")
    @com.shared.common.annotation.SecurePagination
    public ResponseEntity<?> getAllBoardReceiptsSecure(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @jakarta.validation.Valid @RequestBody
            com.shared.common.dto.SecurePaginationRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("Fetching board receipts with secure pagination, status: {}, request: {}", request.getStatus(), request);
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
            org.springframework.data.domain.Page<BoardReceipt> receiptsPage =
                service.findByStatusAndDateRangeWithToken(request.getStatus(), validation.getStartDateTime(), validation.getEndDateTime(), nextPageToken, request.getSortBy(), request.getSortDir());
            com.shared.common.dto.SecurePaginationResponse<BoardReceipt> response =
                com.shared.common.util.SecurePaginationUtil.createSecureResponse(receiptsPage, request);
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
            log.error("Error fetching board receipts (secure)", e);
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardReceipt> findById(@PathVariable("id") Long id, HttpServletRequest request) {
        log.info("Fetching board receipt id={}", id);
        BoardReceipt receipt = service.findById(id);
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
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/process")
    @Operation(summary = "Process board receipt with UTR number", 
               description = "Processes a board receipt by adding UTR number and changing status to VERIFIED")
    @Audited(action = "PROCESS_BOARD_RECEIPT", resourceType = "BOARD_RECEIPT")
    public ResponseEntity<?> processBoardReceipt(@RequestBody BoardReceiptProcessRequest request) {
        log.info("Processing board receipt: {} with UTR: {} by checker: {}", 
                request.getBoardRef(), request.getUtrNumber(), request.getChecker());
        
        try {
            BoardReceipt processedReceipt = service.processBoardReceipt(
                request.getBoardRef(),
                request.getUtrNumber(),
                request.getChecker()
            );
            
            return ResponseEntity.ok(Map.of(
                "message", "Board receipt processed successfully",
                "boardRef", processedReceipt.getBoardRef(),
                "employerRef", processedReceipt.getEmployerRef(),
                "utrNumber", processedReceipt.getUtrNumber(),
                "status", processedReceipt.getStatus(),
                "amount", processedReceipt.getAmount(),
                "checker", processedReceipt.getChecker(),
                "processedDate", processedReceipt.getDate()
            ));
            
        } catch (Exception e) {
            log.error("Error processing board receipt: {}", request.getBoardRef(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Audited(action = "UPDATE_BOARD_RECEIPT", resourceType = "BOARD_RECEIPT")
    public ResponseEntity<BoardReceipt> update(@PathVariable("id") Long id, @RequestBody BoardReceipt boardReceipt) {
        log.info("Updating board receipt id={}", id);
        return ResponseEntity.ok(service.update(id, boardReceipt));
    }

    @DeleteMapping("/{id}")
    @Audited(action = "DELETE_BOARD_RECEIPT", resourceType = "BOARD_RECEIPT")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.info("Deleting board receipt id={}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
