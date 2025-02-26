package com.pythonchecker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pythonchecker.model.ErrorRecord;
import java.util.List;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, Long> {
    List<ErrorRecord> findByUserId(Long userId);
}