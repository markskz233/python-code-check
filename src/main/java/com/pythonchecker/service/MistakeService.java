package com.pythonchecker.service;

import com.pythonchecker.model.Mistake;
import com.pythonchecker.model.User;
import com.pythonchecker.repository.MistakeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MistakeService {
    @Autowired
    private MistakeRepository mistakeRepository;

    public List<Mistake> getMistakesByUser(User user) {
        return mistakeRepository.findByUser(user);
    }

    public List<Mistake> getMistakesByUserAndType(User user, String errorType) {
        return mistakeRepository.findByUserAndErrorType(user, errorType);
    }

    public void saveMistake(Mistake mistake) {
        mistakeRepository.save(mistake);
    }

    public void deleteMistake(Long id) {
        mistakeRepository.deleteById(id);
    }
}