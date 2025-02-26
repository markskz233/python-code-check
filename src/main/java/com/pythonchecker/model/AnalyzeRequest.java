package com.pythonchecker.model;

import lombok.Data;
import java.util.List;

@Data
public class AnalyzeRequest {
    private String problem;
    private String code;
    private List<TestCase> failedTests;
} 