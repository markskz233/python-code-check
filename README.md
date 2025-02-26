# Python代码检查器

这是一个基于Spring Boot的Web应用程序，用于检查Python算法题目的代码正确性。该应用程序集成了通义千问API，可以提供智能代码分析和改进建议。

## 功能特点

- Python代码在线运行和测试
- 实时代码执行状态显示
- 智能代码分析和改进建议
- 错误检测和纠正建议
- 现代简洁的用户界面

## 技术栈

- 后端：Spring Boot 2.7.5
- 前端：Thymeleaf, HTML5, CSS3, JavaScript
- API集成：通义千问API
- 其他：Python运行时环境

## 运行要求

- JDK 11或更高版本
- Maven 3.6或更高版本
- Python 3.x
- 通义千问API密钥

## 快速开始

1. 克隆项目到本地
2. 配置通义千问API密钥（在application.properties中）
3. 运行以下命令：
   ```bash
   mvn spring-boot:run
   ```
4. 访问 http://localhost:25555

## 使用说明

1. 在题目输入框中输入算法题目描述
2. 在代码输入框中输入Python代码
3. 点击"开始测试"按钮
4. 等待代码执行和分析结果
5. 查看执行输出和AI分析建议

## 注意事项

- 请确保Python代码不包含危险操作
- API调用可能需要几秒钟时间
- 建议使用现代浏览器以获得最佳体验 

## 项目运行逻辑

1. 点击开始测试按钮后将题目输入框中的内容调用通义千问api，要求AI给到正确代码及10组测试输入，尽量保证足够测试代码对于题目的正确性，然后在本地运行10次来得出对应的预期输出
2. 将AI给到的结果和本地运行得到的结果格式化并显示，而后在本地运行Python代码输入框中的内容，按照AI给到的测试输入的格式输入，将输出的结果显示在实际输出上，同时看输出是否与预期输出相同（相当于对代码进行10次黑盒测试）
3. 如果10次输出与答案相同，则在错误纠正区中都显示“代码准确无误，无需进行修改”，如果10次输出与答案不同则调用通义千问API，给AI题目、代码、错误数据来询问代码正确性，并让其给出问题分析、解决方案、代码纠正（注意向通义千问询问时要保证他返回内容的格式）并在错误纠正区中分别格式化显示三个回答