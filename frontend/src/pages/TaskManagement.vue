<template>
  <div class="task-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">外呼任务管理</h2>
        <p class="page-subtitle">创建、管理和监控您的外呼任务</p>
      </div>
      <div class="header-actions">
        <el-button 
          type="primary" 
          :icon="Plus"
          size="large"
          @click="openCreateDialog"
        >
          新建任务
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#3b82f6' }">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">总任务数</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#10b981' }">
            <el-icon><VideoPlay /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.running }}</div>
            <div class="stat-label">运行中</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#f59e0b' }">
            <el-icon><VideoPause /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.notStarted }}</div>
            <div class="stat-label">未开始</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#6b7280' }">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.stopped }}</div>
            <div class="stat-label">已停止</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#ef4444' }">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.completed }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 任务列表 -->
    <el-card class="task-list-card">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索任务名称或编码"
            style="width: 300px"
            clearable
            :prefix-icon="Search"
          />
        </div>
      </template>

      <el-table 
        :data="filteredTasks" 
        stripe 
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="taskCode" label="任务编码" width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="200" />
        <el-table-column prop="taskType" label="任务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTaskTypeTag(row.taskType)">
              {{ row.taskType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.taskStatus)">
              {{ getStatusText(row.taskStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="outboundCaller" label="外呼号码" width="150" />
        <el-table-column prop="gmtCreate" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.gmtCreate) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button 
              size="small" 
              type="info" 
              :icon="View"
              @click="viewTaskDetail(row)"
            >
              详情
            </el-button>
            <el-button 
              v-if="row.taskStatus == 0 || row.taskStatus == 3"
              size="small" 
              type="success"
              :icon="VideoPlay"
              @click="startTask(row)"
            >
              启动
            </el-button>
            <el-button 
              v-else-if="row.taskStatus == 1"
              size="small" 
              type="danger"
              :icon="VideoPause"
              @click="stopTask(row)"
            >
              停止
            </el-button>
            <el-button 
              size="small" 
              type="primary" 
              :icon="Edit"
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
            <el-button 
              size="small" 
              type="warning" 
              :icon="Upload"
              @click="openUploadDialog(row)"
            >
              上传名单
            </el-button>
            <el-popconfirm
              title="确定删除此任务吗？"
              @confirm="deleteTask(row.id)"
            >
              <template #reference>
                <el-button size="small" type="danger" :icon="Delete">
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
        />
      </div>
    </el-card>

    <!-- 创建/编辑任务对话框 -->
    <TaskFormDialog 
      v-model:visible="dialogVisible"
      :task="editingTask"
      @success="handleTaskSuccess"
    />

    <!-- 上传名单对话框 -->
    <UploadDialog 
      v-model:visible="uploadDialogVisible"
      :task="currentTask"
      @success="handleUploadSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Plus,
  Search,
  Edit,
  Delete,
  Upload,
  VideoPlay,
  VideoPause,
  Collection,
  CircleClose,
  View
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import UploadDialog from '@/components/UploadDialog.vue'

// Store
const taskStore = useTaskStore()
const router = useRouter()

// 响应式数据
const loading = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const dialogVisible = ref(false)
const uploadDialogVisible = ref(false)
const editingTask = ref(null)
const currentTask = ref(null)

// 计算属性
const filteredTasks = computed(() => {
  const tasks = taskStore.tasks
  if (!searchKeyword.value) return tasks
  
  const keyword = searchKeyword.value.toLowerCase()
  return tasks.filter(task => 
    task.taskCode?.toLowerCase().includes(keyword) ||
    task.taskName?.toLowerCase().includes(keyword)
  )
})

const total = computed(() => filteredTasks.value.length)

const stats = computed(() => {
  const tasks = taskStore.tasks
  return {
    total: tasks.length,
    running: tasks.filter(t => t.taskStatus == 1).length,
    notStarted: tasks.filter(t => t.taskStatus == 0).length,
    completed: tasks.filter(t => t.taskStatus == 2).length,
    stopped: tasks.filter(t => t.taskStatus == 3).length
  }
})

// 方法
const openCreateDialog = () => {
  editingTask.value = null
  dialogVisible.value = true
}

const openEditDialog = (task) => {
  editingTask.value = { ...task }
  dialogVisible.value = true
}

const openUploadDialog = (task) => {
  currentTask.value = task
  uploadDialogVisible.value = true
}

const viewTaskDetail = (task) => {
  router.push(`/tasks/${task.taskCode}`)
}

const startTask = async (task) => {
  try {
    await taskStore.updateTaskStatus(task.taskCode, 1) // RUNNING
    ElMessage.success('启动任务成功')
  } catch (error) {
    ElMessage.error(`启动任务失败: ${error.message}`)
  }
}

const stopTask = async (task) => {
  try {
    await taskStore.updateTaskStatus(task.taskCode, 3) // STOP
    ElMessage.success('停止任务成功')
  } catch (error) {
    ElMessage.error(`停止任务失败: ${error.message}`)
  }
}

const toggleTaskStatus = async (task) => {
  try {
    const newStatus = task.taskStatus === 1 ? 0 : 1
    await taskStore.updateTaskStatus(task.taskCode, newStatus)
    ElMessage.success(`${newStatus === 1 ? '启动' : '停止'}任务成功`)
  } catch (error) {
    ElMessage.error(`操作失败: ${error.message}`)
  }
}

const deleteTask = async (id) => {
  try {
    await taskStore.deleteTask(id)
    ElMessage.success('删除任务成功')
  } catch (error) {
    ElMessage.error(`删除失败: ${error.message}`)
  }
}

const handleTaskSuccess = () => {
  dialogVisible.value = false
  taskStore.fetchTasks()
}

const handleUploadSuccess = () => {
  uploadDialogVisible.value = false
  taskStore.fetchTasks()
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString('zh-CN')
}

const getTaskTypeTag = (type) => {
  const map = {
    'PREDICTIVE': 'primary',
    'TIMED': 'success',
    'MANUAL': 'warning'
  }
  return map[type] || 'info'
}

const getStatusTag = (status) => {
  const map = {
    '0': 'info',
    '1': 'success',
    '2': 'warning',
    '3': 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    '0': '未开始',
    '1': '运行中',
    '2': '已完成',
    '3': '已停止'
  }
  return map[status] || '未知'
}

// 生命周期
onMounted(async () => {
  loading.value = true
  try {
    await taskStore.fetchTasks()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.task-management {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding: 24px;
    background: white;
    border-radius: 12px;
    box-shadow: var(--shadow-md);

    .header-left {
      .page-title {
        font-size: 28px;
        font-weight: 700;
        margin-bottom: 8px;
        color: var(--text-primary);
      }
      
      .page-subtitle {
        font-size: 16px;
        color: var(--text-secondary);
      }
    }
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 20px;
    margin-bottom: 24px;

    .stat-card {
      border-radius: 12px;
      overflow: hidden;
      transition: all 0.3s ease;

      &:hover {
        transform: translateY(-4px);
      }

      .stat-content {
        display: flex;
        align-items: center;
        gap: 16px;

        .stat-icon {
          width: 56px;
          height: 56px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;

          .el-icon {
            font-size: 24px;
          }
        }

        .stat-info {
          .stat-value {
            font-size: 28px;
            font-weight: 700;
            color: var(--text-primary);
            margin-bottom: 4px;
          }

          .stat-label {
            font-size: 14px;
            color: var(--text-secondary);
          }
        }
      }
    }
  }

  .task-list-card {
    border-radius: 12px;
    overflow: hidden;

    :deep(.el-card__header) {
      background: #f8fafc;
      border-bottom: 1px solid var(--border-color);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
    padding: 20px 0;
  }
}
</style>
