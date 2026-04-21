# 🚀 快速部署指南

## 📋 一分钟快速部署

### 开发环境（最快部署）

```bash
# 1. 启动后端（使用H2内存数据库）
mvn spring-boot:run

# 2. 启动前端（新终端窗口）
cd frontend
npm install
npm run dev
```

**访问地址**：
- 后端API: http://localhost:8080
- 前端界面: http://localhost:5173
- 数据库控制台: http://localhost:8080/h2-console

### 生产环境（Docker部署）

```bash
# 1. 构建后端
mvn clean package

# 2. 构建前端Docker镜像
cd frontend
docker build -t outcall-frontend .

# 3. 运行（使用docker-compose）
docker-compose up -d
```

## 🔧 配置文件模板

### 后端生产配置 (application-prod.properties)
```properties
# 数据库配置
app.database.type=mysql
spring.datasource.mysql.url=jdbc:mysql://localhost:3306/outcall_prod
spring.datasource.mysql.username=${DB_USER}
spring.datasource.mysql.password=${DB_PASS}

# 缓存配置
app.cache.type=redis
app.lock.type=redis
spring.redis.host=localhost
spring.redis.port=6379

# 服务器配置
server.port=8080
```

### 前端生产配置 (.env.production)
```bash
VITE_API_BASE_URL=https://your-domain.com/api
VITE_APP_TITLE=外呼任务管理系统
```

## 📊 默认端口汇总

| 服务 | 开发端口 | 生产端口 |
|------|----------|----------|
| 后端API | 8080 | 8080 |
| 前端开发 | 5173 | 80 |
| 数据库 | 内置H2 | 3306 |
| Redis | 无 | 6379 |

## 🔐 环境变量清单

```bash
# 必需变量
DB_USER=数据库用户名
DB_PASS=数据库密码
REDIS_HOST=Redis主机地址

# 可选变量
APP_SECRET=应用密钥
LOG_LEVEL=日志级别
```

## 🎯 部署检查清单

- [ ] JDK 8+ 已安装
- [ ] Maven 3.6+ 已安装  
- [ ] Node.js 16+ 已安装
- [ ] 数据库已创建（非H2模式）
- [ ] Redis已安装（可选）
- [ ] 端口未被占用
- [ ] 防火墙已配置

## 🆘 常见问题快速解决

**后端启动失败**：
```bash
# 检查端口占用
lsof -i :8080

# 清理并重新构建
mvn clean compile
```

**前端白屏**：
```bash
# 清理node_modules
rm -rf node_modules package-lock.json
npm install
```

**数据库连接失败**：
```bash
# 测试数据库连接
telnet localhost 3306
```

**注意：** 前端启动后，需要在页面点击启动任务，才能触发调度逻辑
---
**需要帮助？** 查看完整文档：[WIKI.md](WIKI.md) | [frontend/DEPLOYMENT.md](frontend/DEPLOYMENT.md)