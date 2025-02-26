package com.pythonchecker.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class GenerateTestsResponse {
    private List<TestCase> testCases;
    private String error;
} 