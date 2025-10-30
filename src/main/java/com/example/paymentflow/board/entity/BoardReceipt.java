package com.example.paymentflow.board.entity;

import com.shared.entityaudit.annotation.EntityAuditEnabled;
import com.shared.entityaudit.descriptor.AbstractAuditableEntity;
import com.shared.entityaudit.listener.SharedEntityAuditListener;
import com.shared.entityaudit.model.EntityAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Entity
@EntityAuditEnabled
@EntityListeners(SharedEntityAuditListener.class)
@Table(name = "board_receipts")
public class BoardReceipt extends AbstractAuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false, length = 64)
    private String boardId;

    @Column(name = "board_reference", nullable = false, length = 64)
    private String boardRef;

    @Column(name = "employer_reference", nullable = false, length = 64)
    private String employerRef;

    @Column(name = "employer_id", nullable = false, length = 64)
    private String employerId;

    @Column(name = "toli_id", nullable = false, length = 64)
    private String toliId;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "utr_number", nullable = false, length = 48)
    private String utrNumber;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "PENDING";

    @Column(name = "maker", nullable = false, length = 64)
    private String maker;

    @Column(name = "checker", length = 64)
    private String checker;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate date;

    public BoardReceipt() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBoardId() {
        return boardId;
    }

    public void setBoardId(String boardId) {
        this.boardId = boardId;
    }

    public String getBoardRef() {
        return boardRef;
    }

    public void setBoardRef(String boardRef) {
        this.boardRef = boardRef;
    }

    public String getEmployerRef() {
        return employerRef;
    }

    public void setEmployerRef(String employerRef) {
        this.employerRef = employerRef;
    }

    public String getEmployerId() {
        return employerId;
    }

    public void setEmployerId(String employerId) {
        this.employerId = employerId;
    }

    public String getToliId() {
        return toliId;
    }

    public void setToliId(String toliId) {
        this.toliId = toliId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getUtrNumber() {
        return utrNumber;
    }

    public void setUtrNumber(String utrNumber) {
        this.utrNumber = utrNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMaker() {
        return maker;
    }

    public void setMaker(String maker) {
        this.maker = maker;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String entityType() {
        return "BOARD_RECEIPT";
    }

    @Override
    public Map<String, Object> auditState() {
        return auditStateOf(
                "boardId", boardId,
                "boardRef", boardRef,
                "employerRef", employerRef,
                "employerId", employerId,
                "toliId", toliId,
                "amount", amount != null ? amount.toPlainString() : null,
                "utrNumber", utrNumber,
                "status", status,
                "maker", maker,
                "checker", checker,
                "date", date != null ? date.toString() : null
        );
    }

    @Override
    public String changeSummary(EntityAuditAction action,
                                Map<String, Object> oldState,
                                Map<String, Object> newState) {
        if (action == EntityAuditAction.UPDATE) {
            return "Board receipt totals adjusted";
        }
        return null;
    }
}
