
package com.example.paymentflow.utilities.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import com.example.paymentflow.utilities.logger.LoggerFactoryProvider;

@Component
public class FileStorageUtil {
    private static final Logger log = LoggerFactoryProvider.getLogger(FileStorageUtil.class);
    @Value("${file.upload.base-dir:uploads}")
    private String baseUploadDir;
    private final UploadedFileRepository uploadedFileRepository;

    public FileStorageUtil(UploadedFileRepository uploadedFileRepository) {
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

    private String generateRequestReferenceNumber() {
        // Generate request reference number in format: REQ-YYYYMMDD-HHMMSS-XXX
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String dateTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String sequence = String.format("%03d", (System.currentTimeMillis() % 1000));
        return "REQ-" + dateTime + "-" + sequence;
    }

    private UploadedFile storeFileInternal(MultipartFile file, String category, String fileName) throws IOException {
        String uploadDir = baseUploadDir + File.separator + category;
        log.info("Resolved baseUploadDir property: {}", baseUploadDir);
        log.info("Resolved uploadDir for category '{}': {}", category, uploadDir);

        Path destinationPath = Path.of(uploadDir, fileName).toAbsolutePath();
        Files.createDirectories(destinationPath.getParent());
        log.info("Resolved destination file path: {}", destinationPath);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && uploadedFileRepository.findByFilename(originalFilename).isPresent()) {
            throw new IOException("Duplicate file: a file with the same name already exists.");
        }

        MessageDigest digest = createMessageDigest();

        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);
             OutputStream outputStream = new BufferedOutputStream(
                 Files.newOutputStream(destinationPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = digestInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException ioException) {
            Files.deleteIfExists(destinationPath);
            throw ioException;
        }

        String fileHash = HexFormat.of().formatHex(digest.digest());
        log.debug("Calculated SHA-256 hash {} for uploaded file", fileHash);

        if (!fileHash.isEmpty() && uploadedFileRepository.findByFileHash(fileHash).isPresent()) {
            Files.deleteIfExists(destinationPath);
            throw new IOException("Duplicate file: a file with the same content already exists.");
        }

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setFilename(originalFilename != null ? originalFilename : fileName);
        uploadedFile.setStoredPath(destinationPath.toString());
        uploadedFile.setFileHash(fileHash);
        uploadedFile.setFileType(category);
        uploadedFile.setUploadDate(java.time.LocalDateTime.now());
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

    private MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
