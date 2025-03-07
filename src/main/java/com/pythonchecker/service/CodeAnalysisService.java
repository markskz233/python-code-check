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
                            "      \"input\": \"测试输入数据（如果有多行，用\\\\n分隔）\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}\n\n" +
                            "注意：\n" +
                            "1. referenceCode必须是完全正确的可运行代码\n" +
                            "2. 测试用例的input字段必须包含完整的输入数据，多行数据用\\\\n分隔\n" +
                            "3. 确保输入格式严格符合题目要求\n" +
                            "4. 返回的JSON必须是标准格式，字段名和字段值都必须用双引号\n" +
                            "5. 代码中的换行符必须使用\\\\n转义，不要使用原始换行符\n" +
                            "6. 不要在JSON中使用注释或多余的空格\n" +
                            "7. 所有反斜杠(\\)必须正确转义为双反斜杠(\\\\)，特别是在代码中的字符串内\n" +
                            "8. 不要使用三引号，只使用双引号并正确转义\n" +
                            "9. 确保JSON中没有未转义的控制字符",
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
                        
                        // 预处理JSON字符串，尝试修复常见格式问题
                        jsonStr = preprocessJsonString(jsonStr);
                        
                        log.info("处理后的JSON字符串: {}", jsonStr);
                        
                        JsonNode rootNode;
                        try {
                            // 尝试使用宽松的解析器解析JSON
                            rootNode = parseJsonWithLenientSettings(jsonStr);
                        } catch (Exception e) {
                            log.warn("宽松JSON解析失败，尝试使用备用方法: {}", e.getMessage());
                            rootNode = parseJsonFallback(jsonStr);
                        }
                        
                        // 检查必要字段
                        if (!rootNode.has("referenceCode") || !rootNode.has("testCases")) {
                            throw new RuntimeException("API返回数据缺少必要字段");
                        }
                        
                        // 获取参考代码
                        String referenceCode = rootNode.path("referenceCode").asText();
                        if (referenceCode.trim().isEmpty()) {
                            throw new RuntimeException("生成的参考代码为空");
                        }
                        
                        // 获取测试用例输入并预处理
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
                            
                            // 预处理测试输入，修复转义字符问题
                            testInput = preprocessTestInput(testInput);
                            
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
                            "  \"correction\": \"只包含修正后的完整代码，不要添加任何注释、说明或格式标记\"\n" +
                            "}\n\n" +
                            "注意事项：\n" +
                            "1. analysis必须具体指出代码中的问题（变量冲突、逻辑错误等）\n" +
                            "2. solution必须提供清晰的解决步骤\n" +
                            "3. correction必须仅包含修正后的纯代码，不要使用```python或```标记，不要添加任何说明或注释\n" +
                            "4. correction字段中只提供纯代码，不要加任何标记或额外说明\n" +
                            "5. correction字段中的代码应该是原始格式，不需要转义换行符，直接使用实际的换行和缩进\n" +
                            "6. 如果是变量名冲突，必须提供修改建议\n" +
                            "7. 返回的JSON必须是标准格式，字段名和字段值都必须用双引号\n" +
                            "8. 所有反斜杠(\\)必须正确转义为双反斜杠(\\\\)，特别是在代码中的字符串内\n" +
                            "9. 不要使用三引号，只使用双引号并正确转义\n" +
                            "10. 确保JSON中没有未转义的控制字符\n" +
                            "11. 确保代码中的比较运算符（如>=, <=, ==）格式正确，不要使用HTML实体（如&gt;=）\n" +
                            "12. correction字段中的代码必须是完整的、可直接执行的Python代码，不能只是片段\n" +
                            "13. 确保代码中的所有语法都是正确的，不要有未闭合的括号、引号或缩进错误\n" +
                            "14. 如果代码中有中文注释或字符串，请确保它们被正确处理，不要丢失或损坏\n" +
                            "15. 确保代码中的所有变量都被正确定义和使用，不要有未定义的变量\n" +
                            "16. 特别注意：确保所有的列表、字典、元组等数据结构都正确闭合，不要有未闭合的括号或方括号\n" +
                            "17. 确保correction字段中的代码是完整的，可以直接复制粘贴到Python环境中运行，不会出现语法错误\n" +
                            "18. 不要在代码中使用不必要的转义字符，特别是在字符串中\n" +
                            "19. 确保代码中的所有引号都正确配对，不要有未闭合的引号\n" +
                            "20. 确保代码中的所有缩进都是一致的，使用4个空格作为标准缩进\n" +
                            "21. 在JSON中，correction字段的代码不要使用转义的换行符(\\n)，而是使用实际的换行符\n" +
                            "22. 不要在JSON中使用注释，所有内容必须是有效的JSON格式\n" +
                            "23. 确保JSON中的字符串值使用双引号，不要使用单引号\n" +
                            "24. 确保JSON中的字段名使用双引号\n" +
                            "25. 确保JSON中的布尔值使用小写的true或false，不要使用引号\n" +
                            "26. 确保JSON中的数字值不使用引号\n" +
                            "27. 确保JSON中的null值不使用引号\n" +
                            "28. 确保JSON中的数组使用方括号，对象使用大括号\n" +
                            "29. 确保JSON中的字符串值中的双引号被正确转义为\\\"\n" +
                            "30. 确保JSON中的字符串值中的反斜杠被正确转义为\\\\\n\n" +
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
                        
                        // 预处理JSON字符串，尝试修复常见格式问题
                        jsonStr = preprocessJsonString(jsonStr);
                        
                        log.info("处理后的JSON字符串: {}", jsonStr);
                        
                        try {
                            JsonNode analysisJson;
                            try {
                                // 尝试使用宽松的解析器解析JSON
                                analysisJson = parseJsonWithLenientSettings(jsonStr);
                            } catch (Exception e) {
                                log.warn("宽松JSON解析失败，尝试使用备用方法: {}", e.getMessage());
                                analysisJson = parseJsonFallback(jsonStr);
                            }
                            
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
            // 预处理Python代码，修复可能的语法错误
            code = preprocessPythonCode(code);
            
            // 预处理测试输入，修复转义字符问题
            input = preprocessTestInput(input);
            
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

    private String extractWithRegex(String jsonStr, String fieldName) {
        try {
            // 尝试匹配字段值，考虑多行内容和引号
            String pattern = "\"" + fieldName + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(jsonStr);
            
            if (m.find()) {
                return m.group(1);
            }
            
            // 尝试匹配没有引号的内容（针对代码块）
            pattern = "\"" + fieldName + "\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*[,}]";
            p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            m = p.matcher(jsonStr);
            
            if (m.find()) {
                return m.group(1);
            }
            
            // 尝试匹配任何内容
            pattern = "\"" + fieldName + "\"\\s*:\\s*([\\s\\S]*?)(?:,\\s*\"|})";
            p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            m = p.matcher(jsonStr);
            
            if (m.find()) {
                String result = m.group(1).trim();
                // 如果结果以引号开始和结束，去掉引号
                if (result.startsWith("\"") && result.endsWith("\"")) {
                    result = result.substring(1, result.length() - 1);
                }
                        return result;
                    }
        } catch (Exception e) {
            log.warn("提取{}字段失败: {}", fieldName, e.getMessage());
            }
            
            return "";
    }

    private String formatPythonCode(String code) {
        if (code == null || code.isEmpty()) {
            return "无法提供代码修正建议";
        }
        
        // 移除代码块标记和其他不必要的格式
        code = code.replaceAll("```python\\n?|```", "")
                  .trim();
        
        // 处理HTML实体
        code = code.replaceAll("&gt;", ">")
                  .replaceAll("&lt;", "<")
                  .replaceAll("&amp;", "&")
                  .replaceAll("&quot;", "\"")
                  .replaceAll("&apos;", "'");
        
        // 处理转义的换行符，将其转换为实际的换行符
        code = code.replaceAll("\\\\n", "\n");
        
        // 处理转义的引号
        code = code.replaceAll("\\\\\"", "\"")
                  .replaceAll("\\\\'", "'");
        
        // 如果结果为空，返回默认消息
        if (code.trim().isEmpty()) {
            return "无法提供代码修正建议";
        }
        
        return code;
    }
    
    /**
     * 预处理Python代码，修复常见的语法错误
     */
    private String preprocessPythonCode(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        
        // 修复函数定义中的换行问题
        code = code.replaceAll("def cou\n", "def count_");
        code = code.replaceAll("def cou\\s*\n", "def count_");
        code = code.replaceAll("def cou\\s*\\\\n", "def count_");
        code = code.replaceAll("def cou\\s*\\\\\\\\n", "def count_");
        code = code.replaceAll("def cou\\s*$", "def count_");
        
        // 处理countPonds函数名称断行问题
        code = code.replaceAll("def cou\ntPonds", "def countPonds");
        code = code.replaceAll("def cou\\s*\ntPonds", "def countPonds");
        
        // 处理其他可能的函数名称断行问题
        code = code.replaceAll("def (\\w+)\\s*\n", "def $1");
        code = code.replaceAll("def (\\w+)\\s*\\\\n", "def $1");
        
        // 修复变量名中的问题
        code = code.replaceAll("gird\\s*=", "grid =");
        
        // 修复print语句中的问题
        code = code.replaceAll("print\\(count_ponds\\(N\",\\s*M,\\s*field\\)\\)", "print(count_ponds(N, M, field))");
        
        // 修复数组索引问题
        code = code.replaceAll("data\\[1:\\s*\"N\\+1\\]", "data[1:N+1]");
        
        // 新增：修复函数名中的特殊换行问题
        code = code.replaceAll("def cou\nt_", "def count_");
        code = code.replaceAll("def cou\\\\nt_", "def count_");
        
        // 新增：修复函数调用中的名称问题
        code = code.replaceAll("count_t_ponds", "count_ponds");
        
        // 新增：直接替换整个函数名
        if (code.contains("def cou") && code.contains("_ponds")) {
            code = code.replaceAll("def cou[\\s\\n\\\\]*t_ponds", "def count_ponds");
        }
        
        // 新增：处理函数调用时的名称不匹配问题
        if (code.contains("count_t_ponds(") && !code.contains("def count_t_ponds")) {
            code = code.replaceAll("count_t_ponds\\(", "count_ponds(");
        }
        
        // 新增：修复Python字符串不可变问题
        if (code.contains("field[x][y] = '.'") || code.contains("field[x][y]=\".\"")) {
            // 检测是否需要将字符串列表转换为字符列表
            if (!code.contains("list(") && code.contains("input().strip()")) {
                code = code.replace("field = [input().strip() for _ in range(N)]", 
                                   "field = [list(input().strip()) for _ in range(N)]");
            } else if (!code.contains("list(") && code.contains("input()")) {
                code = code.replace("field = [input() for _ in range(N)]", 
                                   "field = [list(input()) for _ in range(N)]");
            }
            
            // 如果代码中有直接读取每行的模式
            if (code.contains("k = input()") && code.contains("temp = []") && code.contains("temp.append")) {
                // 这种模式已经正确处理了字符串不可变问题，不需要修改
            } else if (!code.contains("list(") && !code.contains("[list(")) {
                // 在代码中添加将字符串转换为列表的逻辑
                int readInputIndex = code.indexOf("field = [");
                if (readInputIndex > 0) {
                    int endOfLine = code.indexOf("\n", readInputIndex);
                    if (endOfLine > 0) {
                        String beforeCode = code.substring(0, endOfLine + 1);
                        String afterCode = code.substring(endOfLine + 1);
                        code = beforeCode + 
                              "# 将字符串转换为列表以便修改\n" +
                              "field = [list(row) for row in field]\n" + 
                              afterCode;
                    }
                }
            }
        }
        
        // 修复变量名错误
        code = code.replaceAll(",,m=map\\(i\nt,", "n,m=map(int,");
        
        return code;
    }
    
    /**
     * 预处理测试输入，修复转义字符问题
     */
    private String preprocessTestInput(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // 处理双反斜杠加n的情况 (\\n)，将其替换为真正的换行符
        input = input.replaceAll("\\\\\\\\n", "\n");
        
        // 处理反斜杠加n的情况 (\n)，将其替换为真正的换行符
        input = input.replaceAll("\\\\n", "\n");
        
        // 处理行尾的反斜杠
        input = input.replaceAll("\\\\$", "");
        
        // 处理行尾的双反斜杠
        input = input.replaceAll("\\\\\\\\$", "");
        
        // 移除多余的反斜杠
        input = input.replaceAll("\\\\\\\\", "\\");
        
        return input;
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

    /**
     * 预处理JSON字符串，尝试修复常见的格式问题
     */
    private String preprocessJsonString(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return jsonStr;
        }
        
        // 记录原始JSON字符串
            log.debug("原始JSON字符串: {}", jsonStr);
            
        // 修复常见的JSON格式问题
        String processed = jsonStr
            // 修复未转义的引号
            .replaceAll("(?<!\\\\)\\\\\"", "\\\\\\\"")
            // 修复未转义的反斜杠
            .replaceAll("(?<!\\\\)\\\\(?![\"\\\\/bfnrt])", "\\\\\\\\")
            // 修复多余的反斜杠
            .replaceAll("\\\\\\\\\"", "\\\\\"")
            // 修复HTML实体
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            // 修复控制字符
            .replaceAll("[\u0000-\u001F]", " ");
        
        // 确保JSON字段名使用双引号
        processed = processed.replaceAll("([{,]\\s*)([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
        
        // 记录处理后的JSON字符串
        log.debug("预处理后的JSON字符串: {}", processed);
        
        return processed;
    }

    /**
     * 当标准JSON解析失败时的备用解析方法
     */
    private JsonNode parseJsonFallback(String jsonStr) throws Exception {
        // 尝试使用正则表达式提取字段
        Map<String, String> fields = new HashMap<>();
        
        // 提取isCorrect字段
        try {
            String isCorrectPattern = "\"isCorrect\"\\s*:\\s*(true|false)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(isCorrectPattern);
            java.util.regex.Matcher matcher = pattern.matcher(jsonStr);
            if (matcher.find()) {
                fields.put("isCorrect", matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("提取isCorrect字段失败: {}", e.getMessage());
        }
        
        // 提取analysis字段
        fields.put("analysis", extractWithRegex(jsonStr, "analysis"));
        
        // 提取solution字段
        fields.put("solution", extractWithRegex(jsonStr, "solution"));
        
        // 提取correction字段
        fields.put("correction", extractWithRegex(jsonStr, "correction"));
        
        // 构建新的JSON字符串
        StringBuilder newJsonStr = new StringBuilder();
        newJsonStr.append("{");
        newJsonStr.append("\"isCorrect\": ").append(fields.getOrDefault("isCorrect", "false")).append(",");
        newJsonStr.append("\"analysis\": \"").append(escapeJsonString(fields.getOrDefault("analysis", ""))).append("\",");
        newJsonStr.append("\"solution\": \"").append(escapeJsonString(fields.getOrDefault("solution", ""))).append("\",");
        newJsonStr.append("\"correction\": \"").append(escapeJsonString(fields.getOrDefault("correction", ""))).append("\"");
        newJsonStr.append("}");
        
        log.debug("重构的JSON字符串: {}", newJsonStr.toString());
        
        // 使用标准ObjectMapper解析重构的JSON
        return objectMapper.readTree(newJsonStr.toString());
    }
    
    private String escapeJsonString(String input) {
                if (input == null) {
            return "";
        }
        
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f");
    }

    /**
     * 使用宽松设置解析JSON
     */
    private JsonNode parseJsonWithLenientSettings(String jsonStr) throws Exception {
        // 创建一个更宽松的ObjectMapper
        ObjectMapper lenientMapper = new ObjectMapper();
        
        // 配置更宽松的解析设置
        lenientMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        lenientMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        lenientMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
        
        try {
            return lenientMapper.readTree(jsonStr);
        } catch (Exception e) {
            log.warn("宽松解析失败: {}", e.getMessage());
            throw e;
        }
    }
} 