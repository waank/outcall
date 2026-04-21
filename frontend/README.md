# 智能外呼调度系统前端

基于 Vue 3 + Element Plus 的现代化外呼任务管理前端界面。

## 🎨 设计特色

采用现代渐变背景 + 半透明毛玻璃效果，营造科技感十足的操作体验：

- **视觉层次丰富**：卡片悬浮动效、渐变统计面板
- **交互反馈即时**：按钮状态变化、加载动画、操作提示
- **数据可视化**：统计卡片直观展示任务状态分布
- **响应式布局**：适配不同屏幕尺寸

## 🚀 技术栈

- **框架**: Vue 3 (Composition API)
- **UI库**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP客户端**: Axios
- **构建工具**: Vite
- **样式**: SCSS
- **文件处理**: SheetJS (xlsx)

## 📁 项目结构

```
frontend/
├── src/
│   ├── components/          # 组件
│   │   ├── TaskFormDialog.vue   # 任务表单对话框
│   │   └── UploadDialog.vue     # 文件上传对话框
│   ├── pages/               # 页面
│   │   └── TaskManagement.vue   # 任务管理主页面
│   ├── stores/              # 状态管理
│   │   └── task.js              # 任务相关store
│   ├── services/            # 服务层
│   │   └── api.js               # API请求封装
│   ├── styles/              # 样式文件
│   │   └── global.scss          # 全局样式
│   ├── router/              # 路由配置
│   │   └── index.js
│   ├── App.vue              # 根组件
│   └── main.js              # 入口文件
├── public/                  # 静态资源
├── index.html               # HTML模板
├── vite.config.js           # Vite配置
└── package.json             # 依赖配置
```

## 🛠️ 安装与运行

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

默认访问地址：http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
```

构建文件将输出到 `dist/` 目录。

## 🔧 功能特性

### ✅ 任务管理
- [x] 任务列表展示（分页、搜索）
- [x] 新建任务
- [x] 编辑任务
- [x] 删除任务
- [x] 启动/停止任务
- [x] 任务状态统计

### ✅ 呼叫名单管理
- [x] Excel/CSV 文件上传
- [x] 文件内容预览
- [x] 数据格式校验
- [x] 批量导入电话号码

### ✅ 用户体验
- [x] 响应式设计
- [x] 加载状态提示
- [x] 操作成功/失败反馈
- [x] 表单验证
- [x] 错误边界处理

## 🎯 API 接口约定

后端需提供以下 RESTful API 接口：

### 任务管理
```
GET    /api/outbound-call-task/list           # 获取任务列表
POST   /api/outbound-call-task/create         # 创建任务
PUT    /api/outbound-call-task/update/{id}    # 更新任务
DELETE /api/outbound-call-task/delete/{id}    # 删除任务
PUT    /api/outbound-call-task/status/{id}    # 更新任务状态
```

### 任务规则
```
GET    /api/outbound-call-task-rules/list     # 获取任务规则列表
```

### 队列组管理
```
GET    /api/outcall-queue-group/list/{taskCode}  # 获取队列组列表
POST   /api/outcall-queue-group/create           # 创建队列组
PUT    /api/outcall-queue-group/status/{id}      # 更新队列组状态
```

### 文件上传
```
POST   /api/outbound-call-task/upload/{taskId}   # 上传呼叫名单
```

## 🎨 UI 组件说明

### 主要页面组件

1. **TaskManagement.vue** - 任务管理主页面
   - 顶部统计卡片（总数、运行中、已暂停、已完成）
   - 任务列表表格（带搜索、分页）
   - 操作按钮（启动/停止、编辑、上传、删除）

2. **TaskFormDialog.vue** - 任务表单对话框
   - 任务基本信息填写
   - 表单验证
   - 支持创建和编辑模式

3. **UploadDialog.vue** - 文件上传对话框
   - 文件拖拽上传
   - 数据预览
   - 格式校验
   - 上传结果反馈

### 状态管理

使用 Pinia 管理全局状态：
- `tasks`: 任务列表数据
- `loading`: 加载状态
- 提供 CRUD 操作方法
- 自动同步本地状态

## 📱 响应式适配

- 桌面端：1200px+ 完整功能展示
- 平板端：768px-1199px 适度压缩
- 手机端：<768px 垂直堆叠布局

## 🔒 安全考虑

- CSRF Token 支持（可在 api.js 中添加）
- 文件类型和大小限制
- 输入数据验证
- 错误信息脱敏

## 🚀 部署建议

1. **静态部署**：将 `dist/` 目录部署到 Nginx/Apache
2. **反向代理**：配置 `/api` 路径代理到后端服务
3. **HTTPS**：生产环境务必启用 HTTPS
4. **缓存策略**：合理设置静态资源缓存头

## 🤝 开发规范

- 使用 Composition API
- 组件命名采用 PascalCase
- 样式使用 SCSS 嵌套语法
- API 调用统一走 service 层
- 状态管理遵循单一职责原则

## 📄 License

MIT
