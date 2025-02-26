document.addEventListener('DOMContentLoaded', function() {
    // 初始化CodeMirror编辑器
    const problemEditor = CodeMirror.fromTextArea(document.getElementById('problemDescription'), {
        mode: 'text',
        theme: 'monokai',
        lineNumbers: true,
        lineWrapping: true,
        viewportMargin: Infinity
    });

    const codeEditor = CodeMirror.fromTextArea(document.getElementById('pythonCode'), {
        mode: 'python',
        theme: 'monokai',
        lineNumbers: true,
        lineWrapping: true,
        viewportMargin: Infinity,
        indentUnit: 4
    });

    // 获取DOM元素
    const testButton = document.getElementById('testButton');
    const resetButton = document.getElementById('resetButton');
    const statusIndicator = document.getElementById('statusIndicator');
    const statusText = document.getElementById('statusText');
    const problemAnalysis = document.getElementById('problemAnalysis');
    const solution = document.getElementById('solution');
    const codeCorrection = document.getElementById('codeCorrection');
    const spinner = testButton.querySelector('.spinner-border');
    const testInputs = document.getElementById('testInputs');
    const expectedOutputs = document.getElementById('expectedOutputs');
    const actualOutputs = document.getElementById('actualOutputs');

    // 添加中断标志
    let isTestingCancelled = false;
    let currentController = null;

    // 更新状态函数
    function updateStatus(status) {
        statusIndicator.className = 'status-indicator ' + status;
        switch(status) {
            case 'idle':
                statusText.textContent = '空闲中';
                break;
            case 'processing':
                statusText.textContent = '代码检测中';
                break;
            case 'analyzing':
                statusText.textContent = '正在分析问题中';
                break;
            case 'completed':
                statusText.textContent = '检测完毕';
                break;
        }
    }

    // 清空结果
    function clearResults() {
        problemAnalysis.textContent = '';
        solution.textContent = '';
        codeCorrection.textContent = '';
        testInputs.textContent = '';
        expectedOutputs.textContent = '';
        actualOutputs.textContent = '';
    }

    // 重置函数
    function resetTest() {
        isTestingCancelled = true;
        if (currentController) {
            currentController.abort(); // 中断当前的API请求
        }
        // 发送请求中断Python代码执行
        fetch('/api/cancel-execution', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        }).catch(error => console.error('取消执行请求失败:', error));
        
        clearResults();
        setLoading(false);
        updateStatus('idle');
        resetButton.disabled = true;
        testButton.disabled = false;
        isTestingCancelled = false;
    }

    // 设置加载状态
    function setLoading(loading) {
        testButton.disabled = loading;
        resetButton.disabled = !loading;
        if (loading) {
            spinner.classList.remove('d-none');
        } else {
            spinner.classList.add('d-none');
        }
    }

    // 显示测试数据
    function displayTestData(testData) {
        let inputsText = '';
        let expectedText = '';

        console.log('显示测试数据:', testData); // 调试日志

        // 显示AI生成的测试数据
        if (testData && testData.testCases && Array.isArray(testData.testCases)) {
            testData.testCases.forEach((test, index) => {
                if (test) {
                    inputsText += `#${index + 1}号测试数据\n${test.input || '无数据'}\n\n`;
                    expectedText += `#${index + 1}号测试数据\n${test.expectedOutput || '等待执行...'}\n\n`;
                }
            });
        }

        // 更新显示内容
        testInputs.textContent = inputsText || '暂无测试数据';
        expectedOutputs.textContent = expectedText || '暂无测试数据';
        actualOutputs.textContent = '正在执行测试...';
    }

    // 处理代码测试
    async function handleCodeTest() {
        const problem = problemEditor.getValue();
        const code = codeEditor.getValue();

        if (!problem.trim() || !code.trim()) {
            alert('请填写题目描述和Python代码！');
            return;
        }

        clearResults();
        setLoading(true);
        updateStatus('processing');
        isTestingCancelled = false;
        currentController = new AbortController();

        try {
            // 如果测试被取消，直接返回
            if (isTestingCancelled) {
                return;
            }

            // 获取测试数据
            const testDataResponse = await fetch('/api/generate-tests', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ problem, code }),
                signal: currentController.signal
            });

            if (isTestingCancelled) {
                return;
            }

            if (!testDataResponse.ok) {
                throw new Error('生成测试数据请求失败');
            }

            const testData = await testDataResponse.json();
            
            if (isTestingCancelled) {
                return;
            }

            if (!testData || !Array.isArray(testData.testCases)) {
                throw new Error('生成测试数据格式错误');
            }

            // 显示AI生成的测试数据
            displayTestData(testData);
            
            if (isTestingCancelled) {
                return;
            }

            // 运行测试
            const testResult = await fetch('/api/run-tests', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ code, testCases: testData.testCases, problem }),
                signal: currentController.signal
            });

            if (isTestingCancelled) {
                return;
            }

            if (!testResult.ok) {
                throw new Error('代码执行请求失败');
            }

            const result = await testResult.json();

            if (isTestingCancelled) {
                return;
            }

            // 更新显示测试结果
            let actualText = '';
            let allTestsPassed = true;
            let failedTests = [];

            // 检查result的格式并显示结果
            if (result && result.error) {
                actualText = `执行错误：${result.error}\n`;
                actualOutputs.textContent = actualText;
                allTestsPassed = false;
                failedTests.push({
                    error: result.error
                });
            } else if (result && Array.isArray(result.testCases)) {
                // 使用testCases数组显示结果
                result.testCases.forEach((testCase, index) => {
                    actualText += `#${index + 1}号测试数据\n`;
                    
                    // 显示实际输出，包括错误信息
                    const output = testCase.actualOutput;
                    const errorInfo = testCase.errorInfo;
                    
                    if (output) {
                        actualText += output;
                    } else if (errorInfo) {
                        actualText += errorInfo;
                    } else {
                        actualText += '无输出';
                    }

                    actualText += '\n\n';
                    
                    if (!testCase.passed) {
                        allTestsPassed = false;
                        failedTests.push(testCase);
                    }
                });
                actualOutputs.textContent = actualText;

                // 更新预期输出显示
                let expectedText = '';
                result.testCases.forEach((testCase, index) => {
                    expectedText += `#${index + 1}号测试数据\n${testCase.expectedOutput || '无输出'}\n\n`;
                });
                expectedOutputs.textContent = expectedText;
            } else {
                actualOutputs.textContent = '执行失败，请检查代码或重试';
                allTestsPassed = false;
            }

            if (isTestingCancelled) {
                return;
            }

            // 根据测试结果决定下一步操作
            if (!allTestsPassed || failedTests.length > 0) {
                updateStatus('analyzing');

                const analyzeResponse = await fetch('/api/analyze', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ 
                        problem, 
                        code,
                        failedTests: failedTests.map(test => ({
                            input: test.input,
                            expectedOutput: test.expectedOutput,
                            actualOutput: test.actualOutput
                        }))
                    }),
                    signal: currentController.signal
                });

                if (isTestingCancelled) {
                    return;
                }

                if (!analyzeResponse.ok) {
                    throw new Error('代码分析请求失败');
                }

                const analyzeResult = await analyzeResponse.json();

                if (isTestingCancelled) {
                    return;
                }

                // 显示分析结果
                problemAnalysis.textContent = analyzeResult.analysis || '分析过程中出现错误';
                solution.textContent = analyzeResult.solution || '无法提供解决方案';
                
                // 处理代码纠正内容
                let correctionText = analyzeResult.correction || '';
                if (correctionText) {
                    correctionText = correctionText.replace(/^['"]|['"]$/g, '');
                    const codeMatch = correctionText.match(/```python\n([\s\S]*?)```/);
                    if (codeMatch) {
                        correctionText = codeMatch[1];
                    }
                    codeCorrection.textContent = correctionText;
                } else {
                    codeCorrection.textContent = '无法提供代码修正建议';
                }
                
                updateStatus('completed');
            } else {
                const successMessage = "代码准确无误，无需进行修改";
                problemAnalysis.textContent = successMessage;
                solution.textContent = successMessage;
                codeCorrection.textContent = successMessage;
                updateStatus('completed');
            }
        } catch (error) {
            if (error.name === 'AbortError') {
                console.log('操作已被用户取消');
                return;
            }
            console.error('Error:', error);
            const errorMessage = error.message || '未知错误';
            problemAnalysis.textContent = '执行出错：' + errorMessage;
            solution.textContent = '请检查代码或刷新页面重试';
            codeCorrection.textContent = '无法提供代码修正建议';
            actualOutputs.textContent = '执行出错：' + errorMessage;
            updateStatus('idle');
        } finally {
            if (!isTestingCancelled) {
                setLoading(false);
            }
            currentController = null;
        }
    }

    // 绑定按钮点击事件
    testButton.addEventListener('click', handleCodeTest);
    resetButton.addEventListener('click', resetTest);

    // 初始化状态
    updateStatus('idle');
});