package com.pythonchecker.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class RunTestsResponse {
    private boolean allTestsPassed;
    private List<TestCase> failedTests;
    private List<String> outputs;
    private List<TestCase> testCases;
    private String error;
} 