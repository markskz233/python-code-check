document.addEventListener('DOMContentLoaded', function() {
    // 初始化CodeMirror编辑器
    const problemEditor = CodeMirror.fromTextArea(document.getElementById('problemDescription'), {
        mode: 'text',
        theme: 'monokai',
        lineNumbers: true,
        lineWrapping: true,
        viewportMargin: 10,
        scrollbarStyle: null,
        autoRefresh: true,
        fixedGutter: true,
        lineWiseCopyCut: true
    });

    const codeEditor = CodeMirror.fromTextArea(document.getElementById('pythonCode'), {
        mode: 'python',
        theme: 'monokai',
        lineNumbers: true,
        lineWrapping: true,
        viewportMargin: 10,
        indentUnit: 4,
        scrollbarStyle: null,
        autoRefresh: true,
        fixedGutter: true,
        lineWiseCopyCut: true
    });

    // 添加高度限制类
    problemEditor.getWrapperElement().classList.add('input-limited');
    codeEditor.getWrapperElement().classList.add('input-limited');
    
    // 设置编辑器高度
    problemEditor.setSize(null, 300);
    codeEditor.setSize(null, 300);
    
    // 添加内容变化监听器，确保内容变化时刷新编辑器
    problemEditor.on('change', function() {
        document.getElementById('problemDescription').value = problemEditor.getValue();
        setTimeout(function() {
            forceRefresh(problemEditor);
        }, 10);
    });
    
    codeEditor.on('change', function() {
        document.getElementById('pythonCode').value = codeEditor.getValue();
        setTimeout(function() {
            forceRefresh(codeEditor);
        }, 10);
    });
    
    // 添加额外的刷新函数
    function forceRefresh(editor) {
        editor.refresh();
        // 强制更新滚动条
        const wrapper = editor.getWrapperElement();
        const scrollbar = wrapper.querySelector('.CodeMirror-vscrollbar');
        if (scrollbar) {
            scrollbar.style.display = 'block';
            scrollbar.style.right = '0';
            scrollbar.style.bottom = '0';
            scrollbar.style.top = '0';
        }
        
        // 隐藏横向滚动条
        const hscrollbar = wrapper.querySelector('.CodeMirror-hscrollbar');
        if (hscrollbar) {
            hscrollbar.style.display = 'none';
        }
        
        // 确保内容可滚动
        const scroll = wrapper.querySelector('.CodeMirror-scroll');
        if (scroll) {
            scroll.style.overflowY = 'scroll';
            scroll.style.overflowX = 'hidden';
            scroll.style.height = '300px';
            scroll.style.minHeight = '300px';
            scroll.style.maxHeight = '300px';
            scroll.style.paddingBottom = '30px';
            scroll.style.boxSizing = 'border-box';
        }
        
        // 确保滚动条可见并限制高度
        editor.setSize(null, 300);
        
        // 确保编辑器容器高度固定
        wrapper.style.height = '300px';
        wrapper.style.minHeight = '300px';
        wrapper.style.maxHeight = '300px';
        wrapper.style.overflow = 'hidden';
    }
    
    // 初始化后强制刷新
    setTimeout(function() {
        forceRefresh(problemEditor);
        forceRefresh(codeEditor);
        
        // 额外的刷新以确保内容正确显示
        setTimeout(function() {
            forceRefresh(problemEditor);
            forceRefresh(codeEditor);
        }, 500);
    }, 100);
    
    // 监听窗口大小变化，刷新编辑器
    window.addEventListener('resize', function() {
        forceRefresh(problemEditor);
        forceRefresh(codeEditor);
    });

    // 添加鼠标滚轮事件监听器
    problemEditor.getWrapperElement().addEventListener('wheel', function(e) {
        const scroll = problemEditor.getWrapperElement().querySelector('.CodeMirror-scroll');
        if (scroll) {
            // 确保滚动条可见
            scroll.style.overflowY = 'scroll';
            scroll.style.overflowX = 'hidden';
            scroll.style.maxHeight = '300px';
            scroll.style.minHeight = '300px';
            scroll.style.height = '300px';
        }
        
        // 隐藏横向滚动条
        const hscrollbar = problemEditor.getWrapperElement().querySelector('.CodeMirror-hscrollbar');
        if (hscrollbar) {
            hscrollbar.style.display = 'none';
        }
        
        // 确保编辑器容器高度固定
        const wrapper = problemEditor.getWrapperElement();
        wrapper.style.height = '300px';
        wrapper.style.minHeight = '300px';
        wrapper.style.maxHeight = '300px';
        wrapper.style.overflow = 'hidden';
    });
    
    codeEditor.getWrapperElement().addEventListener('wheel', function(e) {
        const scroll = codeEditor.getWrapperElement().querySelector('.CodeMirror-scroll');
        if (scroll) {
            // 确保滚动条可见
            scroll.style.overflowY = 'scroll';
            scroll.style.overflowX = 'hidden';
            scroll.style.maxHeight = '300px';
            scroll.style.minHeight = '300px';
            scroll.style.height = '300px';
        }
        
        // 隐藏横向滚动条
        const hscrollbar = codeEditor.getWrapperElement().querySelector('.CodeMirror-hscrollbar');
        if (hscrollbar) {
            hscrollbar.style.display = 'none';
        }
        
        // 确保编辑器容器高度固定
        const wrapper = codeEditor.getWrapperElement();
        wrapper.style.height = '300px';
        wrapper.style.minHeight = '300px';
        wrapper.style.maxHeight = '300px';
        wrapper.style.overflow = 'hidden';
    });
    
    // 添加鼠标点击事件监听器
    problemEditor.getWrapperElement().addEventListener('click', function() {
        setTimeout(function() {
            forceRefresh(problemEditor);
        }, 10);
    });
    
    codeEditor.getWrapperElement().addEventListener('click', function() {
        setTimeout(function() {
            forceRefresh(codeEditor);
        }, 10);
    });
    
    // 添加键盘事件监听器
    problemEditor.on('keydown', function() {
        setTimeout(function() {
            forceRefresh(problemEditor);
        }, 10);
    });
    
    codeEditor.on('keydown', function() {
        setTimeout(function() {
            forceRefresh(codeEditor);
        }, 10);
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
                    try {
                        // 移除可能的引号
                        correctionText = correctionText.replace(/^['"]|['"]$/g, '');
                        
                        // 处理HTML实体
                        correctionText = correctionText
                            .replace(/&gt;/g, '>')
                            .replace(/&lt;/g, '<')
                            .replace(/&amp;/g, '&')
                            .replace(/&quot;/g, '"')
                            .replace(/&apos;/g, "'");
                        
                        // 处理转义字符
                        correctionText = correctionText
                            .replace(/\\n/g, '\n')
                            .replace(/\\\\/g, '\\')
                            .replace(/\\"/g, '"')
                            .replace(/\\'/g, "'")
                            .replace(/\\t/g, '\t');
                        
                        // 直接设置文本内容，不使用CodeMirror
                        codeCorrection.textContent = correctionText;
                        
                    } catch (e) {
                        console.error('处理代码纠正时出错:', e);
                        codeCorrection.textContent = '代码格式化失败，原始内容: ' + correctionText;
                    }
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