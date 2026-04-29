package com.example.finance.controller;

import com.example.finance.service.FinanceService;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private final FinanceService service;

    public ExportController(FinanceService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    public ResponseEntity<byte[]> exportMonthly(@RequestParam int year, @RequestParam int month, @RequestParam(defaultValue = "excel") String format) throws Exception {
        Map<String, Object> report = service.monthlyReport(year, month);
        if ("excel".equalsIgnoreCase(format)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                XSSFSheet sheet = wb.createSheet("transactions");
                List<?> txs = (List<?>) report.get("transactions");
                int r = 0;
                org.apache.poi.ss.usermodel.Row header = sheet.createRow(r++);
                header.createCell(0).setCellValue("date");
                header.createCell(1).setCellValue("type");
                header.createCell(2).setCellValue("category");
                header.createCell(3).setCellValue("amount");
                header.createCell(4).setCellValue("description");
                for (Object o : txs) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) o;
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(m.get("date").toString());
                    row.createCell(1).setCellValue(m.get("type").toString());
                    row.createCell(2).setCellValue(m.get("category") == null ? "" : m.get("category").toString());
                    row.createCell(3).setCellValue(Double.valueOf(m.get("amount").toString()));
                    row.createCell(4).setCellValue(m.get("description") == null ? "" : m.get("description").toString());
                }
                wb.write(baos);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.xlsx");
            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
        } else {
            // PDF export via PDFBox
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                PDPageContentStream cs = new PDPageContentStream(doc, page);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("Monthly Report");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 730);
                cs.showText(String.format("Income: %.2f", (Double) report.get("income")));
                cs.newLineAtOffset(0, -15);
                cs.showText(String.format("Expense: %.2f", (Double) report.get("expense")));
                cs.endText();
                cs.close();
                doc.save(baos);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf");
            return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
        }
    }
}
