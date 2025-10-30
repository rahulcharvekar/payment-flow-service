package com.example.paymentflow.master.entity;

import com.shared.entityaudit.annotation.EntityAuditEnabled;
import com.shared.entityaudit.descriptor.AbstractAuditableEntity;
import com.shared.entityaudit.listener.SharedEntityAuditListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@EntityAuditEnabled
@EntityListeners(SharedEntityAuditListener.class)
@Table(name = "employer_master")
public class EmployerMaster extends AbstractAuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employer_id", nullable = false, unique = true, length = 64)
    private String employerId;

    @Column(name = "serial_no", unique = true, length = 64)
    private String serialNo;

    @Column(name = "registration_no", nullable = false, unique = true, length = 64)
    private String registrationNo;

    @Column(name = "establishment_name", nullable = false, length = 200)
    @NotBlank(message = "Establishment name is required")
    @Size(max = 200, message = "Establishment name cannot exceed 200 characters")
    private String establishmentName;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "owner_name", length = 120)
    private String ownerName;

    @Column(name = "mobile_number", length = 15)
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @Column(name = "aadhaar_number", unique = true, length = 12)
    @Pattern(regexp = "^[0-9]{12}$", message = "Aadhaar number must be 12 digits")
    private String aadhaarNumber;

    @Column(name = "pan_number", length = 16)
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN number format is invalid")
    private String panNumber;

    @Column(name = "tan_number", length = 10)
    @Pattern(regexp = "^[A-Z]{4}[0-9]{5}[A-Z]{1}$", message = "TAN number format is invalid")
    private String tanNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public EmployerMaster() {
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

    public String getEmployerId() {
        return employerId;
    }

    public void setEmployerId(String employerId) {
        this.employerId = employerId;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(String registrationNo) {
        this.registrationNo = registrationNo;
    }

    public String getEstablishmentName() {
        return establishmentName;
    }

    public void setEstablishmentName(String establishmentName) {
        this.establishmentName = establishmentName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
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

    public String getTanNumber() {
        return tanNumber;
    }

    public void setTanNumber(String tanNumber) {
        this.tanNumber = tanNumber;
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
    public String getEmployerName() {
        return establishmentName;
    }

    /**
     * Backwards compatible setter for legacy naming.
     */
    @Deprecated(forRemoval = false)
    public void setEmployerName(String employerName) {
        this.establishmentName = employerName;
    }

    @Override
    public String entityType() {
        return "EMPLOYER_MASTER";
    }

    @Override
    public Map<String, Object> auditState() {
        return auditStateOf(
                "id", id,
                "employerId", employerId,
                "serialNo", serialNo,
                "registrationNo", registrationNo,
                "establishmentName", establishmentName,
                "address", address,
                "ownerName", ownerName,
                "mobileNumber", mobileNumber,
                "aadhaarNumber", aadhaarNumber,
                "panNumber", panNumber,
                "tanNumber", tanNumber,
                "status", status,
                "createdAt", createdAt != null ? createdAt.toString() : null,
                "updatedAt", updatedAt != null ? updatedAt.toString() : null
        );
    }
}
