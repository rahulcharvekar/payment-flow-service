package com.example.paymentflow.worker.repository;

import com.example.paymentflow.worker.entity.WorkerUploadedData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkerUploadedDataRepository extends JpaRepository<WorkerUploadedData, Long> {
    // READ operations - to be moved to WorkerUploadedDataQueryDao in future
    List<WorkerUploadedData> findByFileId(String fileId);
    List<WorkerUploadedData> findByFileIdAndStatus(String fileId, String status);
    Page<WorkerUploadedData> findByFileIdAndStatus(String fileId, String status, Pageable pageable);
    Page<WorkerUploadedData> findByFileId(String fileId, Pageable pageable);
    List<WorkerUploadedData> findByStatus(String status);
    Page<WorkerUploadedData> findByStatus(String status, Pageable pageable);
    List<WorkerUploadedData> findByReceiptNumber(String receiptNumber);
    Page<WorkerUploadedData> findByReceiptNumber(String receiptNumber, Pageable pageable);
    Page<WorkerUploadedData> findByFileIdAndStatusAndCreatedAtBetween(String fileId, String status, 
                                                                   LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    Page<WorkerUploadedData> findByFileIdAndCreatedAtBetween(String fileId, 
                                                             LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    Page<WorkerUploadedData> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("SELECT COUNT(w) FROM WorkerUploadedData w WHERE w.fileId = :fileId AND w.status = :status")
    long countByFileIdAndStatus(@Param("fileId") String fileId, @Param("status") String status);
    
    @Query("SELECT w.status, COUNT(w) FROM WorkerUploadedData w WHERE w.fileId = :fileId GROUP BY w.status")
    List<Object[]> getStatusCountsByFileId(@Param("fileId") String fileId);
    
    @Query("SELECT DISTINCT w.fileId FROM WorkerUploadedData w ORDER BY w.fileId")
    List<String> findDistinctFileIds();
    
    // WRITE operations
    void deleteByFileId(String fileId);
}
