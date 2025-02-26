package com.pythonchecker.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ExecuteResponse {
    private String output;
    private boolean success;
    private String error;
} 