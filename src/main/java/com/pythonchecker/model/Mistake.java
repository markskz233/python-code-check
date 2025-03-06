package com.pythonchecker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mistakes")
public class Mistake {
    public enum AlgorithmType {
        DYNAMIC_PROGRAMMING,
        GREEDY,
        BACKTRACKING,
        DIVIDE_AND_CONQUER,
        SORTING,
        SEARCHING,
        GRAPH,
        TREE,
        NONE  // 用于纯语法错误
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String code;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private String errorType; // "SYNTAX" or "LOGIC"

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm_type")
    private AlgorithmType algorithmType;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "error_record_id")
    private Long errorRecordId;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public Long getErrorRecordId() {
        return errorRecordId;
    }
    
    public void setErrorRecordId(Long errorRecordId) {
        this.errorRecordId = errorRecordId;
    }
    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public AlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}