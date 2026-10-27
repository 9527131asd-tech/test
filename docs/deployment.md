# 部署手册

## Docker Compose（推荐）

安装 Docker Engine 与 Compose 插件后，在目录内创建 `.env`：

```dotenv
DB_PASSWORD=替换成强密码
MYSQL_ROOT_PASSWORD=替换成另一个强密码
```

启动：

```bash
docker compose up --build -d
docker compose logs -f app
```

浏览器访问 `http://服务器地址:8080`。停机使用 `docker compose down`；保留数据库卷。删除数据卷会清除数据库内容。

## Linux 手动运行

安装 OpenJDK 17、Maven 3.9+、MySQL 8；创建 `campus_lab` 数据库和应用专用账号。配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，运行 `mvn clean package -DskipTests`，然后 `java -jar target/campus-lab-system-1.0.0.jar`。建议使用 systemd 和 Nginx/Caddy HTTPS 反向代理。

## Windows 学生/教师终端

终端无需安装 Java；校园网内用 Edge/Chrome 打开服务器地址。若需本地演示，在 Windows 安装 JDK 17、Maven 和 MySQL 8，再按 Linux 环境变量配置说明启动服务。

## 文件和安全

实验报告保存于应用运行目录 `uploads/`，容器环境应挂载持久卷。示例账号仅供演示；生产环境请替换口令、启用 HTTPS、定期备份数据库与报告目录，并把上传文件放在受控存储中。
