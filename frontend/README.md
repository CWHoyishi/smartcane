# 智能拐杖系统 - 前端

## 技术栈

- Vue 3
- Vue Router 4
- Element Plus
- Vite
- Axios

## 项目结构

```
frontend/
├── public/
├── src/
│   ├── api/          # API接口
│   ├── assets/       # 静态资源
│   ├── components/   # 组件
│   ├── router/       # 路由配置
│   ├── utils/        # 工具函数
│   ├── views/        # 页面组件
│   ├── App.vue       # 根组件
│   └── main.js       # 入口文件
├── index.html
├── package.json
└── vite.config.js
```

## 安装依赖

```bash
cd frontend
npm install
```

## 启动开发服务

```bash
npm run dev
```

访问 http://localhost:3000

## 构建生产版本

```bash
npm run build
```

## 功能模块

- GPS轨迹管理
- 报警管理
- 电子围栏管理
- 设备指令管理
