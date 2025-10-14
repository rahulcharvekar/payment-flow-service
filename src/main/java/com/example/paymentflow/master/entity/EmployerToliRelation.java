package com.example.paymentflow.master.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employer_toli_relation")
public class EmployerToliRelation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employer_id", nullable = false, length = 64)
    private String employerId;

    @Column(name = "toli_id", nullable = false, length = 64)
    private String toliId;

    @Column(name = "toli_name", nullable = false, length = 120)
    private String toliName;

    @Column(name = "toli_code", length = 20)
    private String toliCode;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "supervisor_name", length = 100)
    private String supervisorName;

    @Column(name = "supervisor_contact", length = 15)
    private String supervisorContact;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public EmployerToliRelation() {
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

    public String getToliId() {
        return toliId;
    }

    public void setToliId(String toliId) {
        this.toliId = toliId;
    }

    public String getToliName() {
        return toliName;
    }

    public void setToliName(String toliName) {
        this.toliName = toliName;
    }

    public String getToliCode() {
        return toliCode;
    }

    public void setToliCode(String toliCode) {
        this.toliCode = toliCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public String getSupervisorContact() {
        return supervisorContact;
    }

    public void setSupervisorContact(String supervisorContact) {
        this.supervisorContact = supervisorContact;
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
}
