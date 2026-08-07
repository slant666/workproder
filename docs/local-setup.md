# 本地开发配置

## MySQL

本项目初期使用本机 MySQL80，不使用 Docker。

当前已确认 MySQL 监听端口：

```text
3306
```

## 创建数据库

请在你本机执行下面命令。命令会提示你输入 MySQL 密码，密码只输入在本机终端，不要发送到聊天中。

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p
```

进入 MySQL 后执行：

```sql
CREATE DATABASE IF NOT EXISTS work_order_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

## 本地环境变量

后端数据库密码不得写入 Git。请在 Windows 用户环境变量中新增：

```text
WORK_ORDER_DB_URL=jdbc:mysql://localhost:3306/work_order_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
WORK_ORDER_DB_USERNAME=root
WORK_ORDER_DB_PASSWORD=你的本机 MySQL 密码
```

设置位置：

```text
Windows 设置 -> 系统 -> 关于 -> 高级系统设置 -> 环境变量 -> 用户变量
```

设置完成后，关闭并重新打开 PowerShell 或 IDE 终端。

也可以只在当前 PowerShell 临时设置：

```powershell
$env:WORK_ORDER_DB_URL='jdbc:mysql://localhost:3306/work_order_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:WORK_ORDER_DB_USERNAME='root'
$env:WORK_ORDER_DB_PASSWORD='你的本机 MySQL 密码'
```

## 启动后端

```powershell
cd D:\CodexWork\projects\work-order-system\backend
mvn spring-boot:run
```

## 验证

后端是否活着：

```text
http://localhost:8080/api/system/status
```

数据库是否连通：

```text
http://localhost:8080/api/system/database
```

Spring Boot 健康检查：

```text
http://localhost:8080/actuator/health
```

Flyway 成功执行后，MySQL 中会出现 `flyway_schema_history` 表。