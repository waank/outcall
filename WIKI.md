# 外呼任务管理系统部署说明

## 📋 项目概述

外呼任务管理系统是一个基于 Spring Boot + Vue 3 的前后端分离项目，支持任务管理、队列调度、数据统计等功能。系统支持双模式部署：开发模式（H2内存数据库）和生产模式（MySQL数据库）。

## 🏗️ 技术架构

### 后端技术栈
- **框架**: Spring Boot 2.7.18
- **Java版本**: JDK 8
- **ORM框架**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0.33 / H2 内存数据库
- **缓存**: Redis / 本地缓存（双模式）
- **分布式锁**: Redis / 本地锁（双模式）
- **构建工具**: Maven 3.x

### 前端技术栈
- **框架**: Vue 3.4
- **路由**: Vue Router 4.2.5
- **状态管理**: Pinia 2.1.7
- **UI组件库**: Element Plus 2.4.0
- **HTTP客户端**: Axios 1.6.0
- **构建工具**: Vite 5.0
- **图表库**: ECharts 5.4.0
- **数据处理**: XLSX 0.18.5

## ⚙️ 环境准备

### 后端环境要求
- **JDK**: 8 或更高版本
- **Maven**: 3.6 或更高版本
- **数据库**: MySQL 5.7+ 或使用内置H2数据库
- **Redis**: 可选（用于缓存和分布式锁）

### 前端环境要求
- **Node.js**: 16.0 或更高版本
- **npm**: 8.0 或更高版本
- **现代浏览器**: Chrome/Firefox/Safari最新版

## 🔧 开发环境部署

### 1. 后端部署

#### 克隆项目
```bash
git clone <repository-url>
cd outcall
```

#### 配置数据库（可选）
如果使用 MySQL，修改 `src/main/resources/application.properties`：
```properties
# 数据库类型配置: mysql 或 h2
app.database.type=mysql

# MySQL DataSource 配置
spring.datasource.mysql.url=jdbc:mysql://localhost:3306/outcall?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.mysql.username=your_username
spring.datasource.mysql.password=your_password
```

#### 启动后端服务
```bash
# 使用开发模式（H2内存数据库）
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/outcall-schedule-1.0-SNAPSHOT.jar
```

**默认访问地址**: `http://localhost:8080`

### 2. 前端部署

#### 安装依赖
```bash
cd frontend
npm install
```

#### 启动开发服务器
```bash
npm run dev
```

**默认访问地址**: `http://localhost:5173`

#### 构建生产版本
```bash
npm run build
```

构建产物位于 `frontend/dist` 目录。

## 🏭 生产环境部署

### 1. 后端生产部署

#### 环境配置
创建生产环境配置文件 `application-prod.properties`：
```properties
# 生产环境配置
spring.profiles.active=prod
server.port=8080

# 数据库配置
app.database.type=mysql
spring.datasource.mysql.url=jdbc:mysql://prod-db-host:3306/outcall_prod?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=Asia/Shanghai
spring.datasource.mysql.username=${DB_USERNAME}
spring.datasource.mysql.password=${DB_PASSWORD}

# Redis配置（可选）
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.redis.password=${REDIS_PASSWORD:}

# 缓存和锁配置
app.cache.type=redis
app.lock.type=redis

# 日志配置
logging.level.root=INFO
logging.file.name=/var/log/outcall/app.log
```

#### 打包部署
```bash
# 生产环境打包
mvn clean package -Pprod

# 运行生产环境
java -jar target/outcall-schedule-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

#### 系统服务配置（Linux）
创建 systemd 服务文件 `/etc/systemd/system/outcall.service`：
```ini
[Unit]
Description=Outcall Task Management System
After=network.target

[Service]
Type=simple
User=app
WorkingDirectory=/opt/outcall
ExecStart=/usr/bin/java -jar /opt/outcall/outcall-schedule-1.0-SNAPSHOT.jar --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable outcall
sudo systemctl start outcall
```

### 2. 前端生产部署

#### 构建优化
```bash
cd frontend
npm run build
```

#### Nginx 配置
创建 Nginx 配置文件 `/etc/nginx/sites-available/outcall`：
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 前端静态文件
    location / {
        root /var/www/outcall/frontend/dist;
        try_files $uri $uri/ /index.html;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    # API 代理
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # WebSocket 支持（如有需要）
    location /ws/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

启用站点：
```bash
sudo ln -s /etc/nginx/sites-available/outcall /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 🔐 安全配置

### HTTPS 配置
使用 Let's Encrypt 免费 SSL 证书：
```bash
sudo certbot --nginx -d your-domain.com
```

### 环境变量管理
创建 `.env` 文件管理敏感配置：
```bash
# 数据库配置
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_db_password

# Redis配置
export REDIS_HOST=redis-server
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password

# 应用配置
export APP_SECRET=your_app_secret_key
```

## 📊 监控与日志

### 日志配置
后端日志配置在 `logback-spring.xml` 中，支持按级别和文件分割。

### 健康检查端点
```bash
# 应用健康状态
curl http://localhost:8080/actuator/health

# 应用信息
curl http://localhost:8080/actuator/info
```

### 性能监控
可集成 Prometheus 和 Grafana 进行性能监控。

## 🔧 故障排除

### 常见问题

1. **数据库连接失败**
   ```bash
   # 检查数据库服务状态
   sudo systemctl status mysql
   
   # 检查连接配置
   telnet localhost 3306
   ```

2. **前端无法访问API**
   ```bash
   # 检查后端服务状态
   curl http://localhost:8080/api/health
   
   # 检查Nginx配置
   sudo nginx -t
   ```

3. **内存不足**
   ```bash
   # 调整JVM内存参数
   export JAVA_OPTS="-Xms512m -Xmx2g"
   ```

### 调试技巧

1. **启用调试日志**
   ```properties
   logging.level.org.qianye=DEBUG
   logging.level.org.mybatis=DEBUG
   ```

2. **H2数据库控制台**
   访问 `http://localhost:8080/h2-console` 查看内存数据库内容

## 🔄 升级维护

### 版本升级流程
1. 备份数据库
2. 停止服务
3. 部署新版本
4. 运行数据库迁移脚本
5. 启动服务
6. 验证功能

### 回滚方案
```bash
# 停止当前服务
sudo systemctl stop outcall

# 部署旧版本
cp outcall-backup.jar target/outcall-schedule-1.0-SNAPSHOT.jar

# 启动服务
sudo systemctl start outcall
```

## 📞 技术支持

如遇部署问题，请提供以下信息：
- 操作系统版本
- Java/Maven/Node版本
- 错误日志内容
- 配置文件截图

---
**文档版本**: 1.0  
**最后更新**: 2026.03 
**适用版本**: outcall-schedule 1.0-SNAPSHOT