package com.example.paymentflow.worker.entity;

import com.shared.entityaudit.annotation.EntityAuditEnabled;
import com.shared.entityaudit.descriptor.AbstractAuditableEntity;
import com.shared.entityaudit.listener.SharedEntityAuditListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@EntityAuditEnabled
@EntityListeners(SharedEntityAuditListener.class)
@Table(name = "worker_payments")
public class WorkerPayment extends AbstractAuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_reference", nullable = false, length = 64)
    private String workerRef;

    @Column(name = "registration_id", nullable = false, length = 64)
    private String regId;

    @Column(name = "worker_name", nullable = false, length = 120)
    private String name;

    @Column(name = "employer_id", nullable = false, length = 64)
    private String employerId;

    @Column(name = "toli_id", nullable = false, length = 64)
    private String toliId;

    @Column(name = "toli", nullable = false, length = 64)
    private String toli;

    @Column(name = "aadhar", nullable = false, length = 16)
    private String aadhar;

    @Column(name = "pan", nullable = false, length = 16)
    private String pan;

    @Column(name = "bank_account", nullable = false, length = 34)
    private String bankAccount;

    @Column(name = "payment_amount", precision = 15, scale = 2, nullable = false)
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than 0")
    private BigDecimal paymentAmount;

    @Column(name = "file_id", nullable = true, length = 20)
    private String fileId;

    @Column(name = "uploaded_file_ref", nullable = true, length = 100)
    private String uploadedFileRef;

    @Column(name = "request_reference_number", nullable = false, length = 40)
    private String requestReferenceNumber;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "UPLOADED";

    @Column(name = "receipt_number", length = 40)
    private String receiptNumber;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public WorkerPayment() {
    }

    @PrePersist
    public void prePersist() {
        if (Objects.isNull(requestReferenceNumber) || requestReferenceNumber.isBlank()) {
            requestReferenceNumber = "WRK-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase();
        }
        if (Objects.isNull(createdAt)) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkerRef() {
        return workerRef;
    }

    public void setWorkerRef(String workerRef) {
        this.workerRef = workerRef;
    }

    public String getRegId() {
        return regId;
    }

    public void setRegId(String regId) {
        this.regId = regId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getToli() {
        return toli;
    }

    public void setToli(String toli) {
        this.toli = toli;
    }

    public String getAadhar() {
        return aadhar;
    }

    public void setAadhar(String aadhar) {
        this.aadhar = aadhar;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getUploadedFileRef() {
        return uploadedFileRef;
    }

    public void setUploadedFileRef(String uploadedFileRef) {
        this.uploadedFileRef = uploadedFileRef;
    }

    public String getRequestReferenceNumber() {
        return requestReferenceNumber;
    }

    public void setRequestReferenceNumber(String requestReferenceNumber) {
        this.requestReferenceNumber = requestReferenceNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String entityType() {
        return "WORKER_PAYMENT";
    }

    @Override
    public Map<String, Object> auditState() {
        return auditStateOf(
                "id", id,
                "workerRef", workerRef,
                "regId", regId,
                "name", name,
                "employerId", employerId,
                "toliId", toliId,
                "toli", toli,
                "aadhar", aadhar,
                "pan", pan,
                "bankAccount", bankAccount,
                "paymentAmount", paymentAmount != null ? paymentAmount.toPlainString() : null,
                "fileId", fileId,
                "uploadedFileRef", uploadedFileRef,
                "requestReferenceNumber", requestReferenceNumber,
                "status", status,
                "receiptNumber", receiptNumber,
                "createdAt", createdAt != null ? createdAt.toString() : null
        );
    }
}
