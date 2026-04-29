package com.example.finance.repository;

import com.example.finance.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("select t from Transaction t where (:start is null or t.date >= :start) and (:end is null or t.date <= :end) and (:category is null or t.category.name = :category) order by t.date desc")
    List<Transaction> findFiltered(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("category") String category);
}
