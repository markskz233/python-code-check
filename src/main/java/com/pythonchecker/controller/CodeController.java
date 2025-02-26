package com.pythonchecker.controller;

import com.pythonchecker.model.*;
import com.pythonchecker.service.CodeExecutionService;
import com.pythonchecker.service.CodeAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CodeController {

    private final CodeExecutionService codeExecutionService;
    private final CodeAnalysisService codeAnalysisService;

    @PostMapping("/generate-tests")
    public ResponseEntity<GenerateTestsResponse> generateTests(@RequestBody AnalyzeRequest request) {
        GenerateTestsResponse response = codeAnalysisService.generateTestCases(request.getProblem(), request.getCode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/run-tests")
    public ResponseEntity<RunTestsResponse> runTests(@RequestBody RunTestsRequest request) {
        RunTestsResponse response = codeExecutionService.runTests(request.getCode(), request.getTestCases(), request.getProblem());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeCode(@RequestBody AnalyzeRequest request) {
        if (request.getFailedTests() == null || request.getFailedTests().isEmpty()) {
            return ResponseEntity.ok(AnalyzeResponse.builder()
                    .isCorrect(true)
                    .analysis("代码准确无误，无需进行修改")
                    .solution("代码准确无误，无需进行修改")
                    .correction("代码准确无误，无需进行修改")
                    .build());
        }
        
        AnalyzeResponse response = codeAnalysisService.analyzeCode(
            request.getProblem(), 
            request.getCode(),
            request.getFailedTests()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel-execution")
    public ResponseEntity<Void> cancelExecution() {
        codeExecutionService.cancelCurrentExecution();
        return ResponseEntity.ok().build();
    }
}