package com.pythonchecker.service;

import com.pythonchecker.model.RunTestsResponse;
import com.pythonchecker.model.TestCase;
import com.pythonchecker.model.ErrorRecord;
import com.pythonchecker.repository.ErrorRecordRepository;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
public class CodeExecutionService {

    @Value("${python.command}")
    private String pythonCommand;

    @Value("${python.timeout}")
    private long timeout;

    private final ErrorRecordRepository errorRecordRepository;
    private final UserService userService;

    public CodeExecutionService(ErrorRecordRepository errorRecordRepository, UserService userService) {
        this.errorRecordRepository = errorRecordRepository;
        this.userService = userService;
    }

    private volatile Process currentProcess;
    private volatile ExecutorService currentExecutor;
    private volatile boolean isCancelled;

    private String[] getPythonCommands() {
        return new String[]{"python", "py", "python3"};
    }

    public void cancelCurrentExecution() {
        isCancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
        }
        if (currentExecutor != null && !currentExecutor.isShutdown()) {
            currentExecutor.shutdownNow();
        }
    }

    private String executeWithCommand(String command, Path tempFile, TestCase testCase) throws Exception {
        if (isCancelled) {
            throw new InterruptedException("执行已被取消");
        }

        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command, tempFile.toString());
        processBuilder.redirectErrorStream(true);
        
        currentProcess = processBuilder.start();
        currentExecutor = Executors.newFixedThreadPool(2);
        
        try {
            // 获取输入输出流
            BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(currentProcess.getOutputStream()));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(currentProcess.getInputStream(), "UTF-8"));

            // 处理输入的任务
            Future<?> inputTask = currentExecutor.submit(() -> {
                try {
                    if (testCase.getInput() != null && !testCase.getInput().trim().isEmpty()) {
                        String[] inputLines = testCase.getInput().split("\n");
                        for (String line : inputLines) {
                            if (isCancelled) {
                                throw new InterruptedException("执行已被取消");
                            }
                            stdin.write(line);
                            stdin.newLine();
                            stdin.flush();
                        }
                    }
                } catch (Exception e) {
                    log.error("写入输入数据时出错", e);
                } finally {
                    try {
                        stdin.close();
                    } catch (IOException e) {
                        log.error("关闭输入流时出错", e);
                    }
                }
            });

            // 处理输出的任务
            Future<String> outputTask = currentExecutor.submit(() -> {
                StringBuilder output = new StringBuilder();
                String line;
                try {
                    while ((line = stdout.readLine()) != null) {
                        if (isCancelled) {
                            throw new InterruptedException("执行已被取消");
                        }
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("读取输出数据时出错", e);
                }
                return output.toString();
            });

            // 等待输入完成
            try {
                inputTask.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | InterruptedException e) {
                if (isCancelled) {
                    throw new InterruptedException("执行已被取消");
                }
                throw e;
            }
            
            // 等待输出完成
            String output;
            try {
                output = outputTask.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | InterruptedException e) {
                if (isCancelled) {
                    throw new InterruptedException("执行已被取消");
                }
                throw e;
            }
            
            // 等待进程完成
            boolean completed = currentProcess.waitFor(1, TimeUnit.SECONDS);
            int exitCode = completed ? currentProcess.exitValue() : 1;
            
            // 检查输出和退出码
            if (output == null || output.trim().isEmpty()) {
                if (exitCode != 0) {
                    throw new RuntimeException("Python执行错误 (退出码: " + exitCode + ")");
                }
                return "";
            }
            return output;
            
        } finally {
            if (currentProcess.isAlive()) {
                currentProcess.destroyForcibly();
            }
            currentExecutor.shutdownNow();
            currentProcess = null;
            currentExecutor = null;
        }
    }

    public RunTestsResponse runTests(String code, List<TestCase> testCases, String problem) {
        isCancelled = false;
        List<TestCase> failedTests = new ArrayList<>();
        boolean allTestsPassed = true;

        if (testCases == null || testCases.isEmpty()) {
            return RunTestsResponse.builder()
                    .allTestsPassed(false)
                    .failedTests(failedTests)
                    .testCases(new ArrayList<>())
                    .build();
        }

        try {
            // 创建临时Python文件
            Path tempFile = Files.createTempFile("code_", ".py");
            Files.write(tempFile, code.getBytes());

            String[] pythonCommands = getPythonCommands();
            boolean pythonFound = false;
            
            // 首先测试Python命令是否可用
            for (String command : pythonCommands) {
                if (isCancelled) {
                    throw new InterruptedException("执行已被取消");
                }
                try {
                    ProcessBuilder testBuilder = new ProcessBuilder("cmd", "/c", command, "--version");
                    Process testProcess = testBuilder.start();
                    if (testProcess.waitFor(1, TimeUnit.SECONDS) && testProcess.exitValue() == 0) {
                        pythonFound = true;
                        pythonCommand = command;
                        break;
                    }
                } catch (Exception e) {
                    log.warn("命令 {} 不可用", command);
                }
            }

            if (!pythonFound) {
                throw new RuntimeException("未找到可用的Python解释器，请确保Python已正确安装");
            }
            
            for (TestCase testCase : testCases) {
                if (isCancelled) {
                    throw new InterruptedException("执行已被取消");
                }
                try {
                    String output = executeWithCommand(pythonCommand, tempFile, testCase);
                    String trimmedOutput = output.trim();
                    testCase.setActualOutput(trimmedOutput);
                    
                    if (trimmedOutput.contains("Error") || 
                        trimmedOutput.contains("Exception") || 
                        trimmedOutput.contains("Traceback")) {
                        testCase.setErrorInfo(trimmedOutput);
                        testCase.setPassed(false);
                        failedTests.add(testCase);
                        allTestsPassed = false;
                    } else {
                        testCase.setPassed(trimmedOutput.equals(testCase.getExpectedOutput().trim()));
                        if (!testCase.isPassed()) {
                            failedTests.add(testCase);
                            allTestsPassed = false;
                        }
                    }
                } catch (Exception e) {
                    if (isCancelled) {
                        throw new InterruptedException("执行已被取消");
                    }
                    log.error("测试用例执行错误", e);
                    testCase.setActualOutput(e.getMessage());
                    testCase.setErrorInfo(e.getMessage());
                    testCase.setPassed(false);
                    failedTests.add(testCase);
                    allTestsPassed = false;

                    // 不在这里记录错误信息到数据库
                }
            }

            // 清理临时文件
            Files.delete(tempFile);

            // 只在所有测试完成后，如果有失败的测试用例才记录一次错误
            if (!allTestsPassed && !failedTests.isEmpty()) {
                TestCase firstFailedTest = failedTests.get(0);
                String errorType = firstFailedTest.getErrorInfo() != null ? "RuntimeError" : "OutputMismatch";
                String errorMessage = firstFailedTest.getErrorInfo() != null ? 
                    firstFailedTest.getErrorInfo() : 
                    String.format("预期输出：%s，实际输出：%s", 
                        firstFailedTest.getExpectedOutput().trim(), 
                        firstFailedTest.getActualOutput().trim());

                ErrorRecord errorRecord = ErrorRecord.builder()
                        .userId(userService.getCurrentUser().getId())
                        .codeContent(code)
                        .errorType(errorType)
                        .errorMessage(errorMessage)
                        .problemDescription(problem)
                        .submitTime(LocalDateTime.now())
                        .build();
                errorRecordRepository.save(errorRecord);
            }

            return RunTestsResponse.builder()
                    .allTestsPassed(allTestsPassed)
                    .failedTests(failedTests)
                    .testCases(testCases)
                    .build();

        } catch (InterruptedException e) {
            log.info("代码执行被取消");
            return RunTestsResponse.builder()
                    .allTestsPassed(false)
                    .error("执行已被取消")
                    .build();
        } catch (Exception e) {
            log.error("代码执行错误", e);
            String errorMsg = e.getMessage();
            TestCase errorCase = new TestCase();
            errorCase.setPassed(false);
            errorCase.setActualOutput(errorMsg != null ? errorMsg : "执行错误");
            errorCase.setErrorInfo("执行错误");
            failedTests.add(errorCase);
            
            return RunTestsResponse.builder()
                    .allTestsPassed(false)
                    .failedTests(failedTests)
                    .testCases(testCases)
                    .build();
        }
    }
}