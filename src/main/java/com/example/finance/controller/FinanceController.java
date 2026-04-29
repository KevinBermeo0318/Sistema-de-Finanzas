package com.example.finance.controller;

import com.example.finance.dto.TransactionDto;
import com.example.finance.model.Category;
import com.example.finance.model.Transaction;
import com.example.finance.service.FinanceService;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final FinanceService service;

    public FinanceController(FinanceService service) {
        this.service = service;
    }

    @PostMapping("/transactions")
    public Transaction createTx(@RequestBody TransactionDto dto) {
        Transaction t = new Transaction();
        t.setAmount(dto.getAmount());
        t.setType(dto.getType());
        if (dto.getCategory() != null) {
            Category c = new Category();
            c.setName(dto.getCategory());
            t.setCategory(c);
        }
        t.setDescription(dto.getDescription());
        t.setDate(dto.getDate() != null ? dto.getDate() : LocalDate.now());
        return service.addTransaction(t);
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<Transaction> updateTx(@PathVariable Long id, @RequestBody TransactionDto dto) {
        List<Transaction> found = service.listFiltered(null, null, null);
        Transaction tx = found.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (tx == null) return ResponseEntity.notFound().build();
        tx.setAmount(dto.getAmount());
        tx.setType(dto.getType());
        if (dto.getCategory() != null) {
            Category c = new Category();
            c.setName(dto.getCategory());
            tx.setCategory(service.ensureCategory(c.getName()));
        } else {
            tx.setCategory(null);
        }
        tx.setDescription(dto.getDescription());
        tx.setDate(dto.getDate() != null ? dto.getDate() : LocalDate.now());
        Transaction saved = service.addTransaction(tx);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTx(@PathVariable Long id) {
        List<Transaction> found = service.listFiltered(null, null, null);
        Transaction tx = found.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (tx == null) return ResponseEntity.notFound().build();
        service.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/transactions")
    public List<Transaction> listTxs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                     @RequestParam(required = false) String category) {
        return service.listFiltered(start, end, category);
    }

    @GetMapping("/report/monthly")
    public Map<String, Object> monthly(@RequestParam int year, @RequestParam int month) {
        return service.monthlyReport(year, month);
    }

    @GetMapping("/report/monthly/plot")
    public ResponseEntity<byte[]> monthlyPlot(@RequestParam int year, @RequestParam int month) throws Exception {
        Map<String, Object> r = service.monthlyReport(year, month);
        Map<String, Double> byCat = (Map<String, Double>) r.get("byCategory");
        PieChart chart = new PieChartBuilder().width(600).height(400).title("Gastos por categoria").build();
        if (byCat.isEmpty()) {
            chart.addSeries("No data", 1);
        } else {
            for (Map.Entry<String, Double> e : byCat.entrySet()) {
                chart.addSeries(e.getKey(), e.getValue());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
    }

    // Category endpoints
    @GetMapping("/categories")
    public List<String> listCategories() {
        // return names only
        return service.getAllCategories();
    }

    @PostMapping("/categories")
    public Category createCategory(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return service.ensureCategory(name);
    }
}
