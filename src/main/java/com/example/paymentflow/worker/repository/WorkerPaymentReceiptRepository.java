package com.example.paymentflow.worker.repository;

import com.example.paymentflow.worker.entity.WorkerPaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerPaymentReceiptRepository extends JpaRepository<WorkerPaymentReceipt, Long> {
    // All read operations now handled by WorkerPaymentReceiptQueryDao
    // Only JPA save operations remain for WRITE operations
    
    // Add method to find by receipt number for proper JPA entity management
    java.util.Optional<WorkerPaymentReceipt> findByReceiptNumber(String receiptNumber);
}
