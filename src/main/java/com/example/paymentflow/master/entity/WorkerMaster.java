package com.example.paymentflow.master.entity;

import com.shared.entityaudit.annotation.EntityAuditEnabled;
import com.shared.entityaudit.descriptor.AbstractAuditableEntity;
import com.shared.entityaudit.listener.SharedEntityAuditListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@EntityAuditEnabled
@EntityListeners(SharedEntityAuditListener.class)
@Table(name = "worker_master")
public class WorkerMaster extends AbstractAuditableEntity<Long> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false, unique = true, length = 64)
    private String workerId;

    @Column(name = "worker_reference", nullable = false, unique = true, length = 64)
    private String workerReference;

    @Column(name = "registration_no", nullable = false, unique = true, length = 64)
    private String registrationNo;

    @Column(name = "serial_no", unique = true, length = 64)
    private String serialNo;

    @Column(name = "labor_name_english", length = 255)
    private String laborNameEnglish;

    @Column(name = "labor_address_1", length = 255)
    private String laborAddress1;

    @Column(name = "labor_address_2", length = 255)
    private String laborAddress2;

    @Column(name = "permanent_address_3", length = 255)
    private String permanentAddress3;

    @Column(name = "age")
    private Integer age;

    @Column(name = "marital_status", length = 30)
    private String maritalStatus;

    @Column(name = "receipt_no", length = 64)
    private String receiptNo;

    @Column(name = "receipt_amount", precision = 15, scale = 2)
    @DecimalMin(value = "0.0", inclusive = false, message = "Receipt amount must be greater than 0")
    private BigDecimal receiptAmount;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "worker_name", nullable = false, length = 120)
    @NotBlank(message = "Worker name is required")
    @Size(max = 120, message = "Worker name cannot exceed 120 characters")
    private String workerName;

    @Column(name = "employer_name", length = 120)
    private String employerName;

    @Column(name = "employer_address", length = 255)
    private String employerAddress;

    @Column(name = "worker_address", length = 255)
    private String workerAddress;

    @Column(name = "labor_name_marathi", length = 120)
    private String laborNameMarathi;

    @Column(name = "mother_name", length = 120)
    private String motherName;

    @Column(name = "aadhaar_number", nullable = false, unique = true, length = 12)
    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "^[0-9]{12}$", message = "Aadhaar number must be 12 digits")
    private String aadhaarNumber;

    @Column(name = "pan_number", length = 16)
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN number format is invalid")
    private String panNumber;

    @Column(name = "mobile_number", length = 15)
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @Column(name = "phone1", length = 15)
    private String phone1;

    @Column(name = "phone2", length = 15)
    private String phone2;

    @Column(name = "witness_name_1", length = 120)
    private String witnessName1;

    @Column(name = "witness_name_2", length = 120)
    private String witnessName2;

    @Column(name = "witness_designation", length = 120)
    private String witnessDesignation;

    @Column(name = "labor_union_address", length = 255)
    private String laborUnionAddress;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public WorkerMaster() {
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getWorkerReference() {
        return workerReference;
    }

    public void setWorkerReference(String workerReference) {
        this.workerReference = workerReference;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(String registrationNo) {
        this.registrationNo = registrationNo;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getLaborNameEnglish() {
        return laborNameEnglish;
    }

    public void setLaborNameEnglish(String laborNameEnglish) {
        this.laborNameEnglish = laborNameEnglish;
    }

    public String getLaborAddress1() {
        return laborAddress1;
    }

    public void setLaborAddress1(String laborAddress1) {
        this.laborAddress1 = laborAddress1;
    }

    public String getLaborAddress2() {
        return laborAddress2;
    }

    public void setLaborAddress2(String laborAddress2) {
        this.laborAddress2 = laborAddress2;
    }

    public String getPermanentAddress3() {
        return permanentAddress3;
    }

    public void setPermanentAddress3(String permanentAddress3) {
        this.permanentAddress3 = permanentAddress3;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public BigDecimal getReceiptAmount() {
        return receiptAmount;
    }

    public void setReceiptAmount(BigDecimal receiptAmount) {
        this.receiptAmount = receiptAmount;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public String getEmployerAddress() {
        return employerAddress;
    }

    public void setEmployerAddress(String employerAddress) {
        this.employerAddress = employerAddress;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public void setWorkerAddress(String workerAddress) {
        this.workerAddress = workerAddress;
    }

    public String getLaborNameMarathi() {
        return laborNameMarathi;
    }

    public void setLaborNameMarathi(String laborNameMarathi) {
        this.laborNameMarathi = laborNameMarathi;
    }

    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public void setAadhaarNumber(String aadhaarNumber) {
        this.aadhaarNumber = aadhaarNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getPhone1() {
        return phone1;
    }

    public void setPhone1(String phone1) {
        this.phone1 = phone1;
    }

    public String getPhone2() {
        return phone2;
    }

    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    public String getWitnessName1() {
        return witnessName1;
    }

    public void setWitnessName1(String witnessName1) {
        this.witnessName1 = witnessName1;
    }

    public String getWitnessName2() {
        return witnessName2;
    }

    public void setWitnessName2(String witnessName2) {
        this.witnessName2 = witnessName2;
    }

    public String getWitnessDesignation() {
        return witnessDesignation;
    }

    public void setWitnessDesignation(String witnessDesignation) {
        this.witnessDesignation = witnessDesignation;
    }

    public String getLaborUnionAddress() {
        return laborUnionAddress;
    }

    public void setLaborUnionAddress(String laborUnionAddress) {
        this.laborUnionAddress = laborUnionAddress;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Backwards compatible getter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public String getRegistrationId() {
        return registrationNo;
    }

    /**
     * Backwards compatible setter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public void setRegistrationId(String registrationId) {
        this.registrationNo = registrationId;
    }

    /**
     * Backwards compatible getter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public String getAadhar() {
        return aadhaarNumber;
    }

    /**
     * Backwards compatible setter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public void setAadhar(String aadhar) {
        this.aadhaarNumber = aadhar;
    }

    /**
     * Backwards compatible getter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public String getEstablishmentName() {
        return laborNameEnglish;
    }

    /**
     * Backwards compatible setter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public void setEstablishmentName(String establishmentName) {
        this.laborNameEnglish = establishmentName;
    }

    /**
     * Backwards compatible getter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public String getLaborOfficeName() {
        return laborNameMarathi;
    }

    /**
     * Backwards compatible setter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public void setLaborOfficeName(String laborOfficeName) {
        this.laborNameMarathi = laborOfficeName;
    }

    @Override
    public String entityType() {
        return "WORKER_MASTER";
    }

    @Override
    public Map<String, Object> auditState() {
        return auditStateOf(
                "id", id,
                "workerId", workerId,
                "workerReference", workerReference,
                "registrationNo", registrationNo,
                "serialNo", serialNo,
                "laborNameEnglish", laborNameEnglish,
                "laborAddress1", laborAddress1,
                "laborAddress2", laborAddress2,
                "permanentAddress3", permanentAddress3,
                "age", age,
                "maritalStatus", maritalStatus,
                "receiptNo", receiptNo,
                "receiptAmount", receiptAmount != null ? receiptAmount.toPlainString() : null,
                "nationality", nationality,
                "workerName", workerName,
                "employerName", employerName,
                "employerAddress", employerAddress,
                "workerAddress", workerAddress,
                "laborNameMarathi", laborNameMarathi,
                "motherName", motherName,
                "aadhaarNumber", aadhaarNumber,
                "panNumber", panNumber,
                "mobileNumber", mobileNumber,
                "phone1", phone1,
                "phone2", phone2,
                "witnessName1", witnessName1,
                "witnessName2", witnessName2,
                "witnessDesignation", witnessDesignation,
                "laborUnionAddress", laborUnionAddress,
                "status", status,
                "createdAt", createdAt != null ? createdAt.toString() : null,
                "updatedAt", updatedAt != null ? updatedAt.toString() : null
        );
    }
}
