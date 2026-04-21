<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-content">
        <h1 class="logo">
          <el-icon><Phone /></el-icon>
          智能外呼调度系统
        </h1>
        <nav class="nav-menu">
          <el-button 
            type="primary" 
            :icon="Refresh"
            @click="refreshData"
          >
            刷新数据
          </el-button>
          <el-button 
            type="success" 
            @click="forceRefresh"
          >
            强制刷新
          </el-button>
        </nav>
      </div>
    </header>
    
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { Refresh, Phone } from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'

const taskStore = useTaskStore()

const refreshData = () => {
  taskStore.fetchTasks()
}

const forceRefresh = async () => {
  console.log('强制刷新开始')
  await taskStore.fetchTasks()
  console.log('任务数据:', taskStore.tasks)
}
</script>

<style scoped lang="scss">
.app-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
}

.app-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-color);
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--shadow-sm);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 64px;
  max-width: 1400px;
  margin: 0 auto;
}

.logo {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
  display: flex;
  align-items: center;
  gap: 12px;
  
  .el-icon {
    font-size: 28px;
  }
}

.nav-menu {
  display: flex;
  gap: 16px;
}

.main-content {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}
</style>
