# Python代码检查系统环境搭建指南

## 一、必需的软件安装

### 1. Java开发环境（JDK）安装
1. 访问Oracle官网下载页面：https://www.oracle.com/java/technologies/downloads/
2. 下载JDK 17（或更高版本）Windows x64 Installer
3. 双击下载的安装包，按照提示完成安装
4. 验证安装：
   - 按下`Win + R`键
   - 输入`cmd`并回车
   - 在命令行中输入：`java -version`
   - 如果显示版本信息，说明安装成功

### 2. MySQL数据库安装
1. 访问MySQL下载页面：https://dev.mysql.com/downloads/installer/
2. 下载"MySQL Installer for Windows"
3. 双击安装包运行
4. 选择"Custom"安装类型
5. 选择以下组件：
   - MySQL Server
   - MySQL Workbench
6. 安装过程中的重要配置：
   - 端口号：保持默认3306
   - 认证方式：选择"Use Legacy Authentication Method"
   - root密码：设置为`123456`
   
### 3. Python环境安装
1. 访问Python官网：https://www.python.org/downloads/
2. 下载Python 3.x版本（建议3.8或更高）
3. 运行安装程序
4. 重要：勾选"Add Python to PATH"选项
5. 选择"Install Now"

## 二、数据库配置

### 1. 创建数据库
1. 打开MySQL Workbench
2. 点击主页上的"Local instance MySQL"（可能需要输入root密码：123456）
3. 复制并执行以下SQL语句：
```sql
CREATE DATABASE IF NOT EXISTS pythonchecker;
```
4. 点击闪电图标执行SQL

## 三、应用程序配置

### 1. 检查配置文件
确保`application.properties`文件包含以下配置（已经配置好，无需修改）：
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pythonchecker?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
```

## 四、启动应用程序

### 1. 使用IDE（推荐方式）
1. 下载并安装IntelliJ IDEA Community Edition
   - 访问：https://www.jetbrains.com/idea/download/
   - 下载Community版本
   - 运行安装程序

2. 导入项目
   - 打开IDEA
   - 选择"Open"
   - 选择项目文件夹
   - 等待项目加载和依赖下载

3. 运行项目
   - 找到`PythonCodeCheckerApplication.java`文件
   - 右键点击并选择"Run"

### 2. 使用命令行（备选方式）
1. 打开命令提示符
2. 进入项目目录
3. 运行命令：
```bash
./mvnw spring-boot:run
```

## 五、验证安装

1. 打开浏览器
2. 访问：`http://localhost:25555`
3. 如果看到网页界面，说明安装成功

## 六、常见问题解决

### 1. MySQL连接问题
- 确保MySQL服务已启动
- 检查密码是否正确
- 确认端口号是否为3306

### 2. Java相关问题
- 确保JAVA_HOME环境变量设置正确
- 确保使用JDK 17或更高版本

### 3. 端口占用问题
如果25555端口被占用：
1. 修改`application.properties`中的`server.port`值
2. 使用其他未被占用的端口

## 七、技术支持

如果遇到问题：
1. 检查错误日志
2. 确保所有必需服务都在运行
3. 验证配置文件的正确性

## 八、环境要求清单

### 必需软件
- JDK 17或更高版本
- MySQL 8.0或更高版本
- Python 3.8或更高版本
- IntelliJ IDEA（推荐）或其他Java IDE

### 端口要求
- MySQL：3306
- Web应用：25555

### 系统要求
- 操作系统：Windows 10或更高版本
- 内存：至少8GB RAM
- 硬盘空间：至少1GB可用空间 