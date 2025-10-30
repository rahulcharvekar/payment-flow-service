package com.example.paymentflow.employer.entity;

import com.shared.entityaudit.annotation.EntityAuditEnabled;
import com.shared.entityaudit.descriptor.AbstractAuditableEntity;
import com.shared.entityaudit.listener.SharedEntityAuditListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@EntityAuditEnabled
@EntityListeners(SharedEntityAuditListener.class)
@Table(name = "employer_payment_receipts")
public class EmployerPaymentReceipt extends AbstractAuditableEntity<Long> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employer_receipt_number", nullable = false, unique = true, length = 40)
    private String employerReceiptNumber;

    @Column(name = "worker_receipt_number", nullable = false, length = 40)
    private String workerReceiptNumber;

    @Column(name = "employer_id", nullable = false, length = 64)
    private String employerId;

    @Column(name = "toli_id", nullable = false, length = 64)
    private String toliId;

    @Column(name = "transaction_reference", nullable = false, length = 50)
    private String transactionReference;

    @Column(name = "validated_by", nullable = false, length = 64)
    private String validatedBy; // Employer username who validated

    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;

    @Column(name = "total_records", nullable = false)
    private Integer totalRecords;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // VALIDATED, PROCESSED, etc.

    public EmployerPaymentReceipt() {
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public String getEmployerReceiptNumber() {
        return employerReceiptNumber;
    }

    public void setEmployerReceiptNumber(String employerReceiptNumber) {
        this.employerReceiptNumber = employerReceiptNumber;
    }

    public String getWorkerReceiptNumber() {
        return workerReceiptNumber;
    }

    public void setWorkerReceiptNumber(String workerReceiptNumber) {
        this.workerReceiptNumber = workerReceiptNumber;
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

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public Integer getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String entityType() {
        return "EMPLOYER_PAYMENT_RECEIPT";
    }

    @Override
    public Map<String, Object> auditState() {
        return auditStateOf(
                "id", id,
                "employerReceiptNumber", employerReceiptNumber,
                "workerReceiptNumber", workerReceiptNumber,
                "employerId", employerId,
                "toliId", toliId,
                "transactionReference", transactionReference,
                "validatedBy", validatedBy,
                "validatedAt", validatedAt != null ? validatedAt.toString() : null,
                "totalRecords", totalRecords,
                "totalAmount", totalAmount != null ? totalAmount.toPlainString() : null,
                "status", status
        );
    }
}
