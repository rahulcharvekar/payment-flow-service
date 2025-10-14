package com.example.paymentflow.utilities.file;

import com.example.paymentflow.worker.entity.WorkerPayment;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import com.shared.utilities.logger.LoggerFactoryProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class FileParsingUtil {
    private static final Logger log = LoggerFactoryProvider.getLogger(FileParsingUtil.class);

    public List<WorkerPayment> parseFile(File file, String filename) throws IOException {
        String extension = getFileExtension(filename);
        
        switch (extension.toLowerCase()) {
            case "csv":
                return parseCsvFile(file);
            case "xls":
                return parseXlsFile(file);
            case "xlsx":
                return parseXlsxFile(file);
            default:
                throw new IllegalArgumentException("Unsupported file format: " + extension);
        }
    }

    private List<WorkerPayment> parseCsvFile(File file) throws IOException {
        List<WorkerPayment> payments = new ArrayList<>();
        log.info("Parsing CSV file: {}", file.getName());

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] nextLine;
            boolean isFirstRow = true;

            while ((nextLine = reader.readNext()) != null) {
                if (isFirstRow) {
                    // Skip header row
                    isFirstRow = false;
                    continue;
                }

                WorkerPayment payment = createWorkerPayment(nextLine);
                if (payment != null) {
                    payments.add(payment);
                }
            }
        } catch (CsvValidationException e) {
            log.error("CSV validation error while parsing file: {}", file.getName(), e);
            throw new IOException("CSV validation error: " + e.getMessage(), e);
        }

        log.info("Parsed {} worker payment records from CSV", payments.size());
        return payments;
    }

    private List<WorkerPayment> parseXlsFile(File file) throws IOException {
        List<WorkerPayment> payments = new ArrayList<>();
        log.info("Parsing XLS file: {}", file.getName());

        try (FileInputStream fis = new FileInputStream(file);
             HSSFWorkbook workbook = new HSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0); // Get first sheet
            return parseExcelSheet(sheet, payments);
        }
    }

    private List<WorkerPayment> parseXlsxFile(File file) throws IOException {
        List<WorkerPayment> payments = new ArrayList<>();
        log.info("Parsing XLSX file: {}", file.getName());

        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0); // Get first sheet
            return parseExcelSheet(sheet, payments);
        }
    }

    private List<WorkerPayment> parseExcelSheet(Sheet sheet, List<WorkerPayment> payments) {
        boolean isFirstRow = true;

        for (Row row : sheet) {
            if (isFirstRow) {
                // Skip header row
                isFirstRow = false;
                continue;
            }

            String[] rowData = new String[8];
            for (int i = 0; i < 8; i++) {
                Cell cell = row.getCell(i);
                rowData[i] = getCellValueAsString(cell);
            }

            WorkerPayment payment = createWorkerPayment(rowData);
            if (payment != null) {
                payments.add(payment);
            }
        }

        log.info("Parsed {} worker payment records from Excel", payments.size());
        return payments;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private WorkerPayment createWorkerPayment(String[] rowData) {
        try {
            if (rowData.length < 8) {
                log.warn("Row has insufficient columns, skipping: {}", java.util.Arrays.toString(rowData));
                return null;
            }

            WorkerPayment payment = new WorkerPayment();
            payment.setWorkerRef(rowData[0] != null ? rowData[0].trim() : "");
            payment.setRegId(rowData[1] != null ? rowData[1].trim() : "");
            payment.setName(rowData[2] != null ? rowData[2].trim() : "");
            payment.setToli(rowData[3] != null ? rowData[3].trim() : "");
            payment.setAadhar(rowData[4] != null ? rowData[4].trim() : "");
            payment.setPan(rowData[5] != null ? rowData[5].trim() : "");
            payment.setBankAccount(rowData[6] != null ? rowData[6].trim() : "");

            // Parse payment amount
            String amountStr = rowData[7] != null ? rowData[7].trim() : "0";
            try {
                // Handle numeric values that might be in scientific notation
                double amount = Double.parseDouble(amountStr);
                payment.setPaymentAmount(BigDecimal.valueOf(amount));
            } catch (NumberFormatException e) {
                log.warn("Invalid payment amount '{}', setting to 0", amountStr);
                payment.setPaymentAmount(BigDecimal.ZERO);
            }

            // Validate required fields
            if (payment.getWorkerRef().isEmpty() || payment.getName().isEmpty()) {
                log.warn("Row missing required fields (workerRef or name), skipping");
                return null;
            }

            return payment;
        } catch (Exception e) {
            log.error("Error creating WorkerPayment from row data: {}", java.util.Arrays.toString(rowData), e);
            return null;
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
