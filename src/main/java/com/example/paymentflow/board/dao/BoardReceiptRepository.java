package com.example.paymentflow.board.dao;

import com.example.paymentflow.board.entity.BoardReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardReceiptRepository extends JpaRepository<BoardReceipt, Long> {
    // All read operations now handled by BoardReceiptQueryDao
    // Only JPA save operations remain for WRITE operations
}
