package com.example.paymentflow.board.controller;

import com.example.paymentflow.board.entity.BoardReceipt;
import com.example.paymentflow.board.entity.BoardReceiptProcessRequest;
import com.example.paymentflow.board.service.BoardReceiptService;
import com.shared.common.annotation.Auditable;
import com.shared.common.annotation.SecurePagination;
import com.shared.common.dto.SecurePaginationRequest;
import com.shared.common.dto.SecurePaginationResponse;
import com.shared.common.util.ETagUtil;
import com.shared.common.util.SecurePaginationUtil;
import com.shared.utilities.logger.LoggerFactoryProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
    @SecurePagination
    public ResponseEntity<?> getAllBoardReceiptsSecure(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Secure pagination request with mandatory date range",
                required = true
            )
            @Valid @RequestBody
            SecurePaginationRequest request,
            HttpServletRequest httpRequest) {
        log.info("Fetching board receipts with secure pagination, status: {}, request: {}", request.getStatus(), request);
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
            Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(
                    Sort.Direction.fromString(request.getSortDir()),
                    request.getSortBy()
                )
            );
            
            // Fetch data using standard pagination
            Page<BoardReceipt> receiptsPage =
                service.findByStatusAndDateRange(
                    request.getStatus(), 
                    validation.getStartDateTime(), 
                    validation.getEndDateTime(), 
                    pageable);
            SecurePaginationResponse<BoardReceipt> response =
                SecurePaginationUtil.createSecureResponse(receiptsPage, request);
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String responseJson = objectMapper.writeValueAsString(response);
            String eTag = ETagUtil.generateETag(responseJson);
            String ifNoneMatch = httpRequest.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (eTag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(eTag).build();
            }
            return ResponseEntity.ok().eTag(eTag).body(response);
        } catch (Exception e) {
            log.error("Error fetching board receipts (secure)", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
    @Auditable(action = "PROCESS_BOARD_RECEIPT", resourceType = "BOARD_RECEIPT", resourceId = "#request.boardRef")
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
    @Auditable(action = "UPDATE_BOARD_RECEIPT", resourceType = "BOARD_RECEIPT", resourceId = "#id")
    public ResponseEntity<BoardReceipt> update(@PathVariable("id") Long id, @RequestBody BoardReceipt boardReceipt) {
        log.info("Updating board receipt id={}", id);
        BoardReceipt updated = service.update(id, boardReceipt);
        
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE_BOARD_RECEIPT", resourceType = "BOARD_RECEIPT", resourceId = "#id")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.info("Deleting board receipt id={}", id);
        service.delete(id);
        
        return ResponseEntity.noContent().build();
    }

}
