# 校园智能实验室预约与设备管理系统

面向 Java 编程巅峰赛决赛题的可运行基础实现。后端采用 Java 17、Spring Boot 3、Spring Security、JDBC 和 MySQL 8；前端使用轻量 HTML/CSS/JavaScript，提供资源查询、预约、报修、耗材领用和实验报告入口。

## 已实现功能

- 多角色账号：学生、教师、实验室管理员、维护员、系统管理员；API 按角色控制管理操作。
- 实验室与设备查询、登记，支持按实验室类型和关键词筛选。
- 预约规则：学生时长最多 4 小时、最多 3 台设备且只能有一个有效预约；教师最多 8 小时。预约会检查实验室、设备时段冲突并在事务中锁定相关资源。
- 预约取消、签到、签退；预约开始 30 分钟仍未签到会自动记违约，累计 3 次限制预约 7 天。
- 故障报修、维护员接单与完工更新设备状态。
- 耗材领用申请与管理员审批，审批时再次校验库存并扣减；低库存信息可查询，危险耗材有角色限制。
- PDF/DOC/DOCX 实验报告上传、学生查看、教师评分与评语。
- 基础统计卡片、操作日志、Docker Compose 部署和 MySQL 初始化数据。

## 本机运行

需要 JDK 17、Maven 3.9+ 和 MySQL 8。创建数据库 `campus_lab`，按需设置环境变量后启动：

```bash
export DB_URL='jdbc:mysql://localhost:3306/campus_lab?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USER='lab'
export DB_PASSWORD='请替换为你的数据库密码'
mvn spring-boot:run
```

打开 <http://localhost:8080>。首次启动会创建表并写入示例数据与账号。演示账号：

| 角色 | 账号 | 初始密码 |
|---|---|---|
| 学生 | `student01` | `Student@123` |
| 教师 | `teacher01` | `Teacher@123` |
| 实验室管理员 | `labadmin` | `LabAdmin@123` |
| 维护员 | `maintainer` | `Repair@123` |
| 系统管理员 | `admin` | `Admin@123` |

> 初始账号只用于本地演示。对外部署前请修改密码并使用 HTTPS、密钥管理及受控文件存储。

## Docker 部署

在项目目录执行：

```bash
docker compose up --build -d
```

访问 `http://localhost:8080`。Compose 会启动 MySQL 8.4 与应用，表和示例数据在首次启动时自动初始化。部署环境可在 `.env` 中设置 `DB_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`，不要把生产口令提交到 Git。Linux 服务器开放 TCP 8080（或将应用放到反向代理之后）；Windows 终端使用浏览器访问服务器地址。

## API 简表

除公开的资源查询接口外，其余接口需通过 HTTP Basic 登录。请求和响应均为 JSON，日期时间格式为 `yyyy-MM-dd'T'HH:mm:ss`。

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| GET | `/api/labs?type=计算机实验室&q=智能` | 公开 | 查询实验室 |
| GET/POST | `/api/equipment` | 查询公开；新增管理员/实验室管理员 | 设备管理 |
| POST | `/api/reservations` | 已登录 | 创建预约 |
| GET | `/api/reservations` | 已登录 | 查看本人预约；教师和管理员查看全体 |
| POST | `/api/reservations/{id}/cancel` | 本人或管理员 | 取消预约 |
| POST | `/api/reservations/{id}/check-in` | 预约本人 | 签到 |
| POST | `/api/reservations/{id}/check-out` | 预约本人 | 签退 |
| GET/POST | `/api/repairs` | 已登录 | 查询或提交报修 |
| POST | `/api/repairs/{id}/assign`、`/finish` | 维护员/管理员 | 接单、完工 |
| GET | `/api/consumables` | 公开 | 查询库存及预警 |
| POST | `/api/consumables/{id}/issuance` | 已登录 | 申请领用 |
| POST | `/api/issuances/{id}/approve` | 管理员/实验室管理员 | 审批并扣库存 |
| POST | `/api/uploads`、`/api/reports` | 已登录 | 上传并提交报告 |
| POST | `/api/reports/{id}/review` | 教师/管理员 | 评分和评语 |
| GET | `/api/reports/summary` | 教师/管理员 | 查看概览统计 |

请求示例：

```json
{
  "labId": 1,
  "equipmentIds": [1],
  "startTime": "2026-11-02T09:00:00",
  "endTime": "2026-11-02T11:00:00"
}
```

## 文档

- [系统设计、ER 关系、流程图和功能测试用例](docs/design.md)
- [部署说明](docs/deployment.md)
- [功能覆盖与后续增强项](docs/scope.md)
