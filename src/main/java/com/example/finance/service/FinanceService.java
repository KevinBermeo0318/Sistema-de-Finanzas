package com.example.finance.service;

import com.example.finance.model.Category;
import com.example.finance.model.Transaction;
import com.example.finance.repository.CategoryRepository;
import com.example.finance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinanceService {
    private final TransactionRepository txRepo;
    private final CategoryRepository catRepo;

    public FinanceService(TransactionRepository txRepo, CategoryRepository catRepo) {
        this.txRepo = txRepo;
        this.catRepo = catRepo;
    }

    @Transactional
    public Category ensureCategory(String name) {
        if (name == null) return null;
        return catRepo.findByName(name).orElseGet(() -> {
            Category c = new Category();
            c.setName(name);
            return catRepo.save(c);
        });
    }

    @Transactional
    public Transaction addTransaction(Transaction t) {
        if (t.getCategory() != null && t.getCategory().getName() != null) {
            Category c = ensureCategory(t.getCategory().getName());
            t.setCategory(c);
        }
        return txRepo.save(t);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        txRepo.deleteById(id);
    }

    public List<Transaction> listFiltered(LocalDate start, LocalDate end, String category) {
        return txRepo.findFiltered(start, end, category);
    }

    public Map<String, Object> monthlyReport(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<Transaction> txs = listFiltered(start, end, null);
        double income = txs.stream().filter(t -> "income".equals(t.getType())).mapToDouble(Transaction::getAmount).sum();
        double expense = txs.stream().filter(t -> "expense".equals(t.getType())).mapToDouble(Transaction::getAmount).sum();
        Map<String, Double> byCat = new HashMap<>();
        for (Transaction t : txs) {
            String k = t.getCategory() == null ? "(sin categoria)" : t.getCategory().getName();
            byCat.put(k, byCat.getOrDefault(k, 0.0) + t.getAmount());
        }
        Map<String, Object> r = new HashMap<>();
        r.put("income", income);
        r.put("expense", expense);
        r.put("byCategory", byCat);
        // Detach transactions into serializable maps
        List<Map<String, Object>> txmaps = txs.stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("amount", t.getAmount());
            m.put("type", t.getType());
            m.put("category", t.getCategory() == null ? null : t.getCategory().getName());
            m.put("description", t.getDescription());
            m.put("date", t.getDate().toString());
            return m;
        }).toList();
        r.put("transactions", txmaps);
        return r;
    }

    public List<String> getAllCategories() {
        return catRepo.findAll().stream().map(Category::getName).toList();
    }
}
