package com.pythonchecker.model;

import lombok.Data;
import java.util.List;

@Data
public class RunTestsRequest {
    private String code;
    private List<TestCase> testCases;
    private String problem;
}