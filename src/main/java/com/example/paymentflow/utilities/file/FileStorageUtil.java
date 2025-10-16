
package com.example.paymentflow.utilities.file;

import com.shared.utilities.fileupload.FileStorageService;
import com.shared.utilities.fileupload.FileMetadata;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;

@Component
public class FileStorageUtil {
    private static final Logger log = LoggerFactoryProvider.getLogger(FileStorageUtil.class);
    private final FileStorageService fileStorageService;
    private final UploadedFileRepository uploadedFileRepository;

    public FileStorageUtil(FileStorageService fileStorageService, UploadedFileRepository uploadedFileRepository) {
        this.fileStorageService = fileStorageService;
        this.uploadedFileRepository = uploadedFileRepository;
    }

    /**
     * Store a file in a category-specific subfolder under the base upload directory.
     * @param file the file to store
     * @param category the subfolder/category (e.g. "workerpayments", "employerdata", "masterdata")
     * @param fileName the file name to use
     * @return the absolute path to the stored file
     */
    public String storeFile(MultipartFile file, String category, String fileName) throws IOException {
        UploadedFile savedFile = storeFileInternal(file, category, fileName);
        return savedFile.getStoredPath();
    }

    /**
     * Store a file and return the UploadedFile entity directly
     * This avoids the need to look up the file record after storing
     */
    public UploadedFile storeFileAndReturnEntity(MultipartFile file, String category, String fileName) throws IOException {
        return storeFileInternal(file, category, fileName);
    }

    private UploadedFile storeFileInternal(MultipartFile file, String category, String fileName) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && uploadedFileRepository.findByFilename(originalFilename).isPresent()) {
            throw new IOException("Duplicate file: a file with the same name already exists.");
        }

        FileMetadata metadata = fileStorageService.storeFile(file, category, fileName);

        if (!metadata.getFileHash().isEmpty() && uploadedFileRepository.findByFileHash(metadata.getFileHash()).isPresent()) {
            // Delete the stored file if duplicate
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(metadata.getStoredPath()));
            throw new IOException("Duplicate file: a file with the same content already exists.");
        }

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setFilename(metadata.getFilename());
        uploadedFile.setStoredPath(metadata.getStoredPath());
        uploadedFile.setFileHash(metadata.getFileHash());
        uploadedFile.setFileType(metadata.getFileType());
        uploadedFile.setUploadDate(metadata.getUploadDate());
        uploadedFile.setUploadedBy(null);
        uploadedFile.setTotalRecords(0);
        uploadedFile.setSuccessCount(0);
        uploadedFile.setFailureCount(0);
        uploadedFile.setStatus("UPLOADED");
        uploadedFile.setFileReferenceNumber(generateRequestReferenceNumber());

        UploadedFile savedFile = uploadedFileRepository.save(uploadedFile);
        log.info("Saved UploadedFile with ID: {}", savedFile.getId());
        return savedFile;
    }

    private String generateRequestReferenceNumber() {
        // Generate request reference number in format: REQ-YYYYMMDD-HHMMSS-XXX
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String dateTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String sequence = String.format("%03d", (System.currentTimeMillis() % 1000));
        return "REQ-" + dateTime + "-" + sequence;
    }
}
