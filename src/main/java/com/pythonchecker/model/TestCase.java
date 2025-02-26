package com.pythonchecker.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private String errorInfo;
    private boolean passed;
} 