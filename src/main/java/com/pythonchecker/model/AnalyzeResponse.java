package com.pythonchecker.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class AnalyzeResponse {
    private boolean isCorrect;
    private String analysis;
    private String solution;
    private String correction;
} 