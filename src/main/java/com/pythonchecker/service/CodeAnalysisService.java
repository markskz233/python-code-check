package com.pythonchecker.service;

import com.pythonchecker.model.AnalyzeResponse;
import com.pythonchecker.model.GenerateTestsResponse;
import com.pythonchecker.model.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.ProcessBuilder;

@Slf4j
@Service
public class CodeAnalysisService {

    @Value("${tongyi.api.key}")
    private String apiKey;

    @Value("${tongyi.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public CodeAnalysisService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = configureRestTemplate();
        this.objectMapper = objectMapper;
        this.executorService = Executors.newCachedThreadPool();
    }

    private RestTemplate configureRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);    // 60秒连接超时
        factory.setReadTimeout(300000);      // 300秒读取超时
        return new RestTemplate(factory);
    }

    public GenerateTestsResponse generateTestCases(String problem, String code) {
        int maxRetries = 3;
        int currentRetry = 0;
        
        while (currentRetry < maxRetries) {
            final int retryNumber = currentRetry;
            try {
                CompletableFuture<GenerateTestsResponse> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("Authorization", "Bearer " + apiKey);

                        if (apiKey == null || apiKey.trim().isEmpty()) {
                            throw new RuntimeException("API密钥未配置");
                        }

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("model", "qwen-coder-turbo");
                        requestBody.put("temperature", 0.01);
                        requestBody.put("top_p", 0.1);
                        requestBody.put("result_format", "json");
                        
                        String prompt = String.format(
                            "你是一个Python算法专家。请按照以下步骤操作：\n\n" +
                            "1. 分析问题描述：\n%s\n\n" +
                            "2. 请完成两个任务：\n" +
                            "   a. 生成一个完全正确的Python参考代码\n" +
                            "   b. 生成10组测试用例的输入数据\n\n" +
                            "3. 生成测试用例要求：\n" +
                            "   - 输入数据必须严格按照题目要求的格式，包括换行\n" +
                            "   - 如果题目要求多行输入，确保每行数据之间用换行符分隔\n" +
                            "   - 包含边界情况和特殊情况\n" +
                            "   - 确保输入数据完整且有效\n" +
                            "   - 不要生成预期输出（由本地执行得出）\n\n" +
                            "请按照以下JSON格式返回（不要包含任何其他内容）：\n" +
                            "{\n" +
                            "  \"referenceCode\": \"完全正确的Python代码\",\n" +
                            "  \"testCases\": [\n" +
                            "    {\n" +
                            "      \"input\": \"测试输入数据（如果有多行，用\\n分隔）\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}\n\n" +
                            "注意：\n" +
                            "1. referenceCode必须是完全正确的可运行代码\n" +
                            "2. 测试用例的input字段必须包含完整的输入数据，多行数据用\\n分隔\n" +
                            "3. 确保输入格式严格符合题目要求",
                            problem);

                        Map<String, Object> input = new HashMap<>();
                        input.put("prompt", prompt);
                        requestBody.put("input", input);

                        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
                        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RuntimeException("API请求失败，状态码：" + response.getStatusCode());
                        }

                        String responseBody = response.getBody();
                        if (responseBody == null || responseBody.isEmpty()) {
                            throw new RuntimeException("API返回为空");
                        }

                        JsonNode responseJson = objectMapper.readTree(responseBody);
                        String aiResponse = responseJson.path("output").path("text").asText();
                        
                        if (aiResponse == null || aiResponse.isEmpty()) {
                            throw new RuntimeException("API返回的输出为空");
                        }
                        
                        int jsonStart = aiResponse.indexOf("{");
                        int jsonEnd = aiResponse.lastIndexOf("}") + 1;
                        if (jsonStart < 0 || jsonEnd <= jsonStart) {
                            throw new RuntimeException("API返回格式错误，无法解析JSON内容");
                        }
                        
                        String jsonStr = aiResponse.substring(jsonStart, jsonEnd);
                        JsonNode rootNode = objectMapper.readTree(jsonStr);
                        
                        // 检查必要字段
                        if (!rootNode.has("referenceCode") || !rootNode.has("testCases")) {
                            throw new RuntimeException("API返回数据缺少必要字段");
                        }
                        
                        // 获取参考代码
                        String referenceCode = rootNode.path("referenceCode").asText();
                        if (referenceCode.trim().isEmpty()) {
                            throw new RuntimeException("生成的参考代码为空");
                        }
                        
                        // 获取测试用例输入
                        List<TestCase> testCases = new ArrayList<>();
                        JsonNode testCasesNode = rootNode.path("testCases");
                        if (!testCasesNode.isArray() || testCasesNode.size() == 0) {
                            throw new RuntimeException("未生成有效的测试用例");
                        }

                        for (JsonNode testCase : testCasesNode) {
                            String testInput = testCase.path("input").asText();
                            if (testInput.trim().isEmpty()) {
                                continue;
                            }
                            
                            // 使用参考代码在本地执行获取预期输出
                            String expectedOutput = executeCodeLocally(referenceCode, testInput);
                            if (expectedOutput.startsWith("执行错误：")) {
                                log.error("测试用例执行失败：{}", expectedOutput);
                                continue;
                            }
                            
                            testCases.add(TestCase.builder()
                                .input(testInput)
                                .expectedOutput(expectedOutput)
                                .build());
                        }

                        if (testCases.isEmpty()) {
                            throw new RuntimeException("所有测试用例执行失败");
                        }

                        return GenerateTestsResponse.builder()
                                .testCases(testCases)
                                .build();

                    } catch (Exception e) {
                        log.error("第{}次尝试生成测试用例失败: {}", retryNumber + 1, e.getMessage());
                        throw new CompletionException("生成测试用例失败：" + e.getMessage(), e);
                    }
                }, executorService);

                return future.get(30, TimeUnit.SECONDS);

            } catch (TimeoutException e) {
                log.error("生成测试用例超时", e);
                return GenerateTestsResponse.builder()
                        .testCases(new ArrayList<>())
                        .error("生成测试用例超时，请稍后重试")
                        .build();
            } catch (Exception e) {
                currentRetry++;
                String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                log.error("第{}次尝试失败: {}", currentRetry, errorMessage);
                
                if (currentRetry >= maxRetries) {
                    log.error("达到最大重试次数", e);
                    return GenerateTestsResponse.builder()
                            .testCases(new ArrayList<>())
                            .error("生成测试用例失败：" + errorMessage)
                            .build();
                }
                
                try {
                    Thread.sleep(1000 * (long)Math.pow(2, currentRetry));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return GenerateTestsResponse.builder()
                .testCases(new ArrayList<>())
                .error("多次尝试生成测试用例均失败，请稍后重试")
                .build();
    }

    public AnalyzeResponse analyzeCode(String problem, String code, List<TestCase> failedTests) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                CompletableFuture<AnalyzeResponse> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("Authorization", "Bearer " + apiKey);

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("model", "qwen-coder-turbo");
                        requestBody.put("temperature", 0.01);
                        requestBody.put("top_p", 0.1);
                        requestBody.put("result_format", "json");
                        
                        StringBuilder failedTestsStr = new StringBuilder();
                        int maxFailedTests = Math.min(failedTests.size(), 5);
                        for (int i = 0; i < maxFailedTests; i++) {
                            TestCase test = failedTests.get(i);
                            String actualOutput = test.getActualOutput();
                            String errorInfo = test.getErrorInfo();
                            
                            failedTestsStr.append(String.format(
                                "#%d号测试数据\n输入：%s\n预期输出：%s\n%s：%s\n\n",
                                i + 1,
                                test.getInput(),
                                test.getExpectedOutput(),
                                errorInfo != null ? "运行错误" : "实际输出",
                                errorInfo != null ? errorInfo : actualOutput
                            ));
                        }
                        
                        String prompt = String.format(
                            "请分析以下Python代码的问题并给出改进建议。代码在测试时%s。\n" +
                            "请严格按照以下JSON格式返回，确保每个字段的内容符合要求：\n\n" +
                            "{\n" +
                            "  \"isCorrect\": false,\n" +
                            "  \"analysis\": \"仅描述代码中存在的具体问题，例如：变量命名冲突、逻辑错误等\",\n" +
                            "  \"solution\": \"仅描述解决问题的具体步骤，例如：1. 修改变量名 2. 调整逻辑等\",\n" +
                            "  \"correction\": \"完整的修正后代码，包含所有必要的修改\"\n" +
                            "}\n\n" +
                            "注意事项：\n" +
                            "1. analysis必须具体指出代码中的问题（变量冲突、逻辑错误等）\n" +
                            "2. solution必须提供清晰的解决步骤\n" +
                            "3. correction必须提供完整的、可直接运行的代码\n" +
                            "4. 如果是变量名冲突，必须提供修改建议\n\n" +
                            "问题描述：\n%s\n\n" +
                            "代码：\n%s\n\n" +
                            "测试结果：\n%s",
                            failedTests.get(0).getErrorInfo() != null ? "出现运行错误" : "输出结果与预期不符",
                            problem, code, failedTestsStr.toString());

                        Map<String, Object> input = new HashMap<>();
                        input.put("prompt", prompt);
                        requestBody.put("input", input);

                        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
                        ResponseEntity<String> responseEntity = restTemplate.postForEntity(apiUrl, request, String.class);
                        
                        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                            throw new RuntimeException("API调用失败，HTTP状态码: " + responseEntity.getStatusCode());
                        }
                        
                        String response = responseEntity.getBody();
                        if (response == null || response.isEmpty()) {
                            throw new RuntimeException("API返回为空");
                        }

                        JsonNode responseJson = objectMapper.readTree(response);
                        String aiResponse = responseJson.path("output").path("text").asText();
                        
                        if (aiResponse == null || aiResponse.isEmpty()) {
                            throw new RuntimeException("API返回的输出为空");
                        }
                        
                        int jsonStart = aiResponse.indexOf("{");
                        int jsonEnd = aiResponse.lastIndexOf("}") + 1;
                        if (jsonStart < 0 || jsonEnd <= jsonStart) {
                            throw new RuntimeException("API返回的格式不正确");
                        }
                        
                        String jsonStr = aiResponse.substring(jsonStart, jsonEnd);
                        
                        try {
                            JsonNode analysisJson = objectMapper.readTree(jsonStr);
                            
                            // 确保分析和解决方案不重复且内容正确
                            String analysis = analysisJson.path("analysis").asText().trim();
                            String solution = analysisJson.path("solution").asText().trim();
                            String correction = analysisJson.path("correction").asText();
                            
                            // 如果字段为空，尝试使用正则表达式提取
                            if (analysis.isEmpty()) {
                                analysis = extractWithRegex(jsonStr, "analysis");
                            }
                            if (solution.isEmpty()) {
                                solution = extractWithRegex(jsonStr, "solution");
                            }
                            if (correction.isEmpty()) {
                                correction = extractWithRegex(jsonStr, "correction");
                            }
                            
                            // 处理Python代码格式
                            correction = formatPythonCode(correction);
                            
                            // 确保内容不重复
                            if (isSimilar(analysis, solution)) {
                                // 如果内容相似，重新构造solution
                                solution = "解决步骤：\n" + solution.replaceAll("(?m)^", "- ");
                            }
                            
                            // 移除可能的交叉引用和不相关内容
                            analysis = analysis.replaceAll("(?i)(解决方案|修改方法|具体代码|参考代码|修正代码|修改如下|建议修改|可以修改).*", "")
                                            .replaceAll("(?s)```.*?```", "")
                                            .trim();
                            solution = solution.replaceAll("(?i)(问题分析|代码问题|错误原因|存在问题|问题在于).*", "")
                                            .replaceAll("(?s)```.*?```", "")
                                            .trim();
                            
                            // 如果内容为空，提供默认值
                            if (analysis.isEmpty()) {
                                analysis = "无法提取问题分析";
                            }
                            if (solution.isEmpty()) {
                                solution = "无法提取解决方案";
                            }
                            if (correction.isEmpty()) {
                                correction = "无法提取修正代码";
                            }

                            return AnalyzeResponse.builder()
                                    .isCorrect(false)
                                    .analysis(analysis)
                                    .solution(solution)
                                    .correction(correction)
                                    .build();
                        } catch (Exception e) {
                            log.error("JSON解析错误，尝试使用正则表达式提取", e);
                            
                            String analysis = extractWithRegex(jsonStr, "analysis");
                            String solution = extractWithRegex(jsonStr, "solution");
                            String correction = extractWithRegex(jsonStr, "correction");
                            
                            // 处理Python代码格式
                            correction = formatPythonCode(correction);
                            
                            if (analysis.isEmpty() && solution.isEmpty() && correction.isEmpty()) {
                                throw new RuntimeException("无法解析API返回的内容");
                            }
                            
                            return AnalyzeResponse.builder()
                                    .isCorrect(false)
                                    .analysis(analysis)
                                    .solution(solution)
                                    .correction(correction)
                                    .build();
                        }
                    } catch (Exception e) {
                        log.error("代码分析错误", e);
                        throw new CompletionException(e);
                    }
                }, executorService);

                return future.get(60, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.error("第{}次重试失败", retryCount + 1, e);
                retryCount++;
                
                if (retryCount >= maxRetries) {
                    String errorMessage = "通义千问API调用失败：" + 
                        (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    
                    return AnalyzeResponse.builder()
                            .isCorrect(false)
                            .analysis(errorMessage)
                            .solution("请稍后重试或检查网络连接")
                            .correction("无法提供代码纠正建议")
                            .build();
                }
                
                try {
                    Thread.sleep(2000 * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return AnalyzeResponse.builder()
                .isCorrect(false)
                .analysis("多次重试后仍然失败，请稍后再试")
                .solution("建议检查网络连接或联系管理员")
                .correction("无法提供代码纠正建议")
                .build();
    }

    private String executeCodeLocally(String code, String input) {
        try {
            // 创建临时Python文件
            Path tempDir = Files.createTempDirectory("python_test");
            Path codePath = tempDir.resolve("test.py");
            Path inputPath = tempDir.resolve("input.txt");
            
            // 确保代码和输入都以换行符结束
            code = code.trim() + "\n";
            input = input.trim() + "\n";
            
            // 写入代码和输入
            Files.writeString(codePath, code);
            Files.writeString(inputPath, input);
            
            // 尝试不同的Python命令
            String[] pythonCommands = {"python", "python3", "py"};
            Process process = null;
            String error = "";
            
            for (String cmd : pythonCommands) {
                try {
                    ProcessBuilder pb = new ProcessBuilder();
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        pb.command("cmd", "/c", cmd, codePath.toString());
                    } else {
                        pb.command(cmd, codePath.toString());
                    }
                    
                    // 设置工作目录和重定向
                    pb.directory(tempDir.toFile());
                    pb.redirectInput(inputPath.toFile());
                    
                    process = pb.start();
                    
                    // 等待进程完成，设置5秒超时
                    if (process.waitFor(5, TimeUnit.SECONDS)) {
                        int exitCode = process.exitValue();
                        if (exitCode == 0) {
                            // 命令执行成功，跳出循环
                            break;
                        }
                    } else {
                        // 超时，销毁进程
                        process.destroy();
                        continue;
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                    log.warn("尝试执行 {} 失败: {}", cmd, error);
                    continue;
                }
            }
            
            if (process == null) {
                throw new RuntimeException("未找到可用的Python解释器，请确保Python已正确安装并添加到PATH中");
            }
            
            // 读取输出
            String output = new String(process.getInputStream().readAllBytes(), "UTF-8").trim();
            error = new String(process.getErrorStream().readAllBytes(), "UTF-8").trim();
            
            // 检查执行状态
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python执行错误: {}", error);
                throw new RuntimeException(error.isEmpty() ? "程序执行失败(退出码:" + exitCode + ")" : error);
            }
            
            // 清理临时文件
            try {
                Files.deleteIfExists(codePath);
                Files.deleteIfExists(inputPath);
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                log.warn("清理临时文件失败", e);
            }
            
            // 如果输出为空，返回特殊标记
            if (output.trim().isEmpty()) {
                return "无输出";
            }
            
            return output;
            
        } catch (Exception e) {
            log.error("本地执行代码错误: {}", e.getMessage());
            return "执行错误：" + e.getMessage();
        }
    }

    private String extractWithRegex(String jsonStr, String field) {
        try {
            // 预处理三引号字符串
            java.util.regex.Pattern tripleQuotePattern = java.util.regex.Pattern.compile("\"\"\"([\\s\\S]*?)\"\"\"");
            java.util.regex.Matcher tripleQuoteMatcher = tripleQuotePattern.matcher(jsonStr);
            StringBuffer sb = new StringBuffer();
            while (tripleQuoteMatcher.find()) {
                String content = tripleQuoteMatcher.group(1);
                String replacement = "\"" + content.replace("\"", "\\\"").replace("\n", "\\n") + "\"";
                tripleQuoteMatcher.appendReplacement(sb, replacement);
            }
            tripleQuoteMatcher.appendTail(sb);
            String processedJson = sb.toString();
            
            // 使用更精确的正则表达式来匹配JSON字段
            String pattern = String.format(
                "\"%s\"\\s*:\\s*(?:\"([^\"]*(?:\\\\\"[^\"]*)*)\"|([^,}\\n]*))",
                field
            );
            java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = r.matcher(processedJson);
            if (m.find()) {
                // 按优先级检查捕获组
                for (int i = 1; i <= m.groupCount(); i++) {
                    String result = m.group(i);
                    if (result != null && !result.isEmpty()) {
                        return result.replaceAll("\\\\\"", "\"")
                                   .replaceAll("\\\\n", "\n")
                                   .replaceAll("^[\"']|[\"']$", "")
                                   .trim();
                    }
                }
            }
            return "";
        } catch (Exception e) {
            log.error("正则表达式提取失败: {}", e.getMessage());
            return "";
        }
    }

    private String formatPythonCode(String code) {
        if (code == null || code.isEmpty()) {
            return "无法提供代码修正建议";
        }
        
        // 移除代码块标记和注释
        code = code.replaceAll("```python\\n?|```", "")
                  .replaceAll("\"\"\".*?\"\"\"", "")
                  .replaceAll("#.*$", "")
                  .trim();
        
        // 如果代码为空，返回默认消息
        if (code.trim().isEmpty()) {
            return "无法提供代码修正建议";
        }
        
        // 处理缩进
        String[] lines = code.split("\n");
        StringBuilder formatted = new StringBuilder();
        int baseIndent = -1;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 计算当前行的缩进级别
            int indent = line.indexOf(trimmed);
            if (baseIndent == -1 && indent > 0) {
                baseIndent = indent;
            }
            
            // 规范化缩进（使用4个空格）
            if (indent > 0) {
                int spaces = baseIndent > 0 ? (indent / baseIndent) * 4 : 4;
                formatted.append(" ".repeat(spaces));
            }
            
            formatted.append(trimmed).append("\n");
        }
        
        String result = formatted.toString().trim();
        return result.isEmpty() ? "无法提供代码修正建议" : result;
    }
    
    private boolean isSimilar(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return false;
        }
        
        // 简单的相似度检查
        String s1 = str1.replaceAll("\\s+", "").toLowerCase();
        String s2 = str2.replaceAll("\\s+", "").toLowerCase();
        
        if (s1.length() == 0 || s2.length() == 0) {
            return false;
        }
        
        // 如果较长字符串包含较短字符串的80%以上，认为它们相似
        String longer = s1.length() > s2.length() ? s1 : s2;
        String shorter = s1.length() > s2.length() ? s2 : s1;
        
        int matchCount = 0;
        for (int i = 0; i < shorter.length(); i++) {
            if (longer.indexOf(shorter.charAt(i)) >= 0) {
                matchCount++;
            }
        }
        
        return (double) matchCount / shorter.length() > 0.8;
    }
} 