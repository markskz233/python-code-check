package com.pythonchecker.repository;

import com.pythonchecker.model.Mistake;
import com.pythonchecker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MistakeRepository extends JpaRepository<Mistake, Long> {
    List<Mistake> findByUser(User user);
    List<Mistake> findByUserAndErrorType(User user, String errorType);
}