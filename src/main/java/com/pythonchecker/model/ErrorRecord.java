package com.pythonchecker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "error_records")
public class ErrorRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_content", nullable = false, columnDefinition = "TEXT")
    private String codeContent;

    @Column(name = "error_type", nullable = false)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "problem_description", columnDefinition = "TEXT")
    private String problemDescription;

    @Column(name = "submit_time")
    private LocalDateTime submitTime;

    public Object getUserId() {
        return userId;
    }

    public void setUserId(Object userId) {
        this.userId = (Long) userId;
    }
}