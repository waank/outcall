<template>
  <div class="task-detail">
    <!-- 返回按钮和标题 -->
    <div class="page-header">
      <div class="header-left">
        <el-button 
          :icon="ArrowLeft" 
          @click="goBack"
          circle
        />
        <div class="title-info">
          <h2 class="page-title">任务详情</h2>
          <p class="page-subtitle" v-if="task">{{ task.taskName }} ({{ task.taskCode }})</p>
        </div>
      </div>
      <div class="header-actions">
        <el-tag :type="getStatusTag(task?.taskStatus)" size="large">
          {{ getStatusText(task?.taskStatus) }}
        </el-tag>
      </div>
    </div>

    <!-- 队列统计卡片 -->
    <div class="stats-grid">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#3b82f6' }">
            <el-icon><List /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">总队列数</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card waiting" shadow="hover" @click="filterByStatus('WAITING')">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#f59e0b' }">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.waiting }}</div>
            <div class="stat-label">等待中</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card running" shadow="hover" @click="filterByStatus('PROCESSING')">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#10b981' }">
            <el-icon><Loading /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.running }}</div>
            <div class="stat-label">执行中</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card success" shadow="hover" @click="filterByStatus('SUCCESS')">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#22c55e' }">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.success }}</div>
            <div class="stat-label">成功</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card fail" shadow="hover" @click="filterByStatus('FAILED')">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#ef4444' }">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.fail }}</div>
            <div class="stat-label">失败</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card stop" shadow="hover" @click="filterByStatus('STOP')">
        <div class="stat-content">
          <div class="stat-icon" :style="{ backgroundColor: '#6b7280' }">
            <el-icon><VideoPause /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.stop }}</div>
            <div class="stat-label">已停止</div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 筛选条件 -->
    <el-card class="filter-card">
      <div class="filter-bar">
        <el-radio-group v-model="statusFilter" @change="handleFilterChange">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="WAITING">等待中</el-radio-button>
          <el-radio-button label="PROCESSING">执行中</el-radio-button>
          <el-radio-button label="SUCCESS">成功</el-radio-button>
          <el-radio-button label="FAILED">失败</el-radio-button>
          <el-radio-button label="STOP">已停止</el-radio-button>
        </el-radio-group>
        
        <el-input
          v-model="searchKeyword"
          placeholder="搜索被叫号码"
          style="width: 250px"
          clearable
          :prefix-icon="Search"
        />
      </div>
    </el-card>

    <!-- 队列列表 -->
    <el-card class="queue-list-card">
      <template #header>
        <div class="card-header">
          <span>队列列表</span>
          <el-button 
            type="primary" 
            :icon="Refresh" 
            @click="loadQueues"
            :loading="loading"
          >
            刷新
          </el-button>
        </div>
      </template>

      <el-table 
        :data="filteredQueues" 
        stripe 
        style="width: 100%"
        v-loading="loading"
        @row-click="showQueueDetail"
      >
        <el-table-column prop="queueCode" label="队列编码" width="180" />
        <el-table-column prop="callee" label="被叫号码" width="150" />
        <el-table-column prop="caller" label="主叫号码" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getQueueStatusTag(row.status)">
              {{ getQueueStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="callCount" label="呼叫次数" width="100" />
        <el-table-column prop="callStartTime" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.callStartTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="callEndTime" label="结束时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.callEndTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="gmtCreate" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.gmtCreate) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-if="row.status === 'WAITING'"
              size="small" 
              type="danger"
              @click.stop="stopQueue(row)"
            >
              停止
            </el-button>
            <el-button 
              v-if="row.status === 'STOP'"
              size="small" 
              type="success"
              @click.stop="resumeQueue(row)"
            >
              恢复
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
        />
      </div>
    </el-card>

    <!-- 队列详情对话框 -->
    <el-dialog 
      v-model="detailDialogVisible" 
      title="队列详情"
      width="600px"
    >
      <el-descriptions :column="2" border v-if="selectedQueue">
        <el-descriptions-item label="队列编码">{{ selectedQueue.queueCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getQueueStatusTag(selectedQueue.status)">
            {{ getQueueStatusText(selectedQueue.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="主叫号码">{{ selectedQueue.caller }}</el-descriptions-item>
        <el-descriptions-item label="被叫号码">{{ selectedQueue.callee }}</el-descriptions-item>
        <el-descriptions-item label="呼叫次数">{{ selectedQueue.callCount }}</el-descriptions-item>
        <el-descriptions-item label="通话ID">{{ selectedQueue.acid || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDate(selectedQueue.callStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatDate(selectedQueue.callEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(selectedQueue.gmtCreate) }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ formatDate(selectedQueue.gmtModified) }}</el-descriptions-item>
        <el-descriptions-item label="扩展信息" :span="2">
          <pre>{{ JSON.stringify(selectedQueue.extInfo || {}, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Search,
  Refresh,
  List,
  Clock,
  Loading,
  CircleCheck,
  CircleClose,
  VideoPause
} from '@element-plus/icons-vue'
import { useTaskStore } from '@/stores/task'
import { queueApi } from '@/services/api'

const route = useRoute()
const router = useRouter()
const taskStore = useTaskStore()

// 响应式数据
const loading = ref(false)
const queues = ref([])
const statusFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const detailDialogVisible = ref(false)
const selectedQueue = ref(null)

// 从路由获取任务信息
const taskCode = route.params.taskCode
const task = computed(() => 
  taskStore.tasks.find(t => t.taskCode === taskCode)
)

// 统计数据
const stats = computed(() => {
  const allQueues = queues.value
  return {
    total: allQueues.length,
    waiting: allQueues.filter(q => q.status === 'WAITING').length,
    running: allQueues.filter(q => q.status === 'PROCESSING').length,
    success: allQueues.filter(q => q.status === 'SUCCESS').length,
    fail: allQueues.filter(q => q.status === 'FAILED').length,
    stop: allQueues.filter(q => q.status === 'STOP').length
  }
})

// 过滤后的队列列表
const filteredQueues = computed(() => {
  let result = queues.value
  
  if (statusFilter.value) {
    result = result.filter(q => q.status === statusFilter.value)
  }
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(q => 
      q.callee?.toLowerCase().includes(keyword) ||
      q.queueCode?.toLowerCase().includes(keyword)
    )
  }
  
  // 分页
  const start = (currentPage.value - 1) * pageSize.value
  return result.slice(start, start + pageSize.value)
})

const total = computed(() => {
  let result = queues.value
  if (statusFilter.value) {
    result = result.filter(q => q.status === statusFilter.value)
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(q => 
      q.callee?.toLowerCase().includes(keyword)
    )
  }
  return result.length
})

// 方法
const goBack = () => {
  router.push('/tasks')
}

const loadQueues = async () => {
  loading.value = true
  try {
    const response = await queueApi.getByTaskCode(taskCode)
    if (response.success) {
      queues.value = response.data || []
    } else {
      ElMessage.error('加载队列失败')
    }
  } catch (error) {
    console.error('加载队列失败:', error)
    ElMessage.error(`加载队列失败: ${error.message}`)
  } finally {
    loading.value = false
  }
}

const filterByStatus = (status) => {
  statusFilter.value = status === statusFilter.value ? '' : status
}

const handleFilterChange = () => {
  currentPage.value = 1
}

const showQueueDetail = (row) => {
  selectedQueue.value = row
  detailDialogVisible.value = true
}

const stopQueue = async (row) => {
  try {
    await queueApi.updateStatus(row.instanceId, row.queueCode, row.envId, 'STOP')
    ElMessage.success('停止队列成功')
    await loadQueues()
  } catch (error) {
    ElMessage.error(`停止队列失败: ${error.message}`)
  }
}

const resumeQueue = async (row) => {
  try {
    await queueApi.updateStatus(row.instanceId, row.queueCode, row.envId, 'WAITING')
    ElMessage.success('恢复队列成功')
    await loadQueues()
  } catch (error) {
    ElMessage.error(`恢复队列失败: ${error.message}`)
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

const getStatusTag = (status) => {
  const map = {
    0: 'info',
    1: 'success',
    2: 'warning',
    3: 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    0: '未开始',
    1: '运行中',
    2: '已完成',
    3: '已停止'
  }
  return map[status] || '未知'
}

const getQueueStatusTag = (status) => {
  const map = {
    'WAITING': 'warning',
    'PLANNING': 'info',
    'PROCESSING': 'primary',
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'STOP': 'info'
  }
  return map[status] || 'info'
}

const getQueueStatusText = (status) => {
  const map = {
    'WAITING': '等待中',
    'PLANNING': '规划中',
    'PROCESSING': '执行中',
    'SUCCESS': '成功',
    'FAILED': '失败',
    'STOP': '已停止'
  }
  return map[status] || status
}

// 生命周期
onMounted(async () => {
  // 加载任务列表（如果还没有加载）
  if (taskStore.tasks.length === 0) {
    await taskStore.fetchTasks()
  }
  // 加载队列列表
  await loadQueues()
})
</script>

<style scoped lang="scss">
.task-detail {
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
      display: flex;
      align-items: center;
      gap: 16px;
      
      .title-info {
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
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 16px;
    margin-bottom: 24px;

    .stat-card {
      border-radius: 12px;
      overflow: hidden;
      transition: all 0.3s ease;
      cursor: pointer;

      &:hover {
        transform: translateY(-4px);
      }

      .stat-content {
        display: flex;
        align-items: center;
        gap: 12px;

        .stat-icon {
          width: 48px;
          height: 48px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;

          .el-icon {
            font-size: 20px;
          }
        }

        .stat-info {
          .stat-value {
            font-size: 24px;
            font-weight: 700;
            color: var(--text-primary);
            margin-bottom: 4px;
          }

          .stat-label {
            font-size: 13px;
            color: var(--text-secondary);
          }
        }
      }
    }
  }

  .filter-card {
    margin-bottom: 16px;
    border-radius: 12px;
    
    .filter-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .queue-list-card {
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
    
    :deep(.el-table__row) {
      cursor: pointer;
      
      &:hover {
        background-color: #f5f7fa;
      }
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
