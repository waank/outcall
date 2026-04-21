<template>
  <el-dialog
    v-model="visible"
    title="上传呼叫名单"
    width="600px"
    :before-close="handleClose"
  >
    <div class="upload-container">
      <div class="task-info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务编码">
            {{ task?.taskCode }}
          </el-descriptions-item>
          <el-descriptions-item label="任务名称">
            {{ task?.taskName }}
          </el-descriptions-item>
          <el-descriptions-item label="外呼号码">
            {{ task?.outboundCaller }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusTag(task?.taskStatus)">
              {{ getStatusText(task?.taskStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="upload-area">
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :limit="1"
          accept=".xlsx,.xls,.csv"
          v-model:file-list="fileList"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              请上传 Excel 文件(.xlsx/.xls) 或 CSV 文件(.csv)，文件大小不超过 10MB
            </div>
          </template>
        </el-upload>
      </div>

      <div v-if="previewData.length > 0" class="preview-section">
        <h4>数据预览</h4>
        <el-table 
          :data="previewData.slice(0, 5)" 
          max-height="300"
          size="small"
        >
          <el-table-column 
            v-for="(col, index) in previewColumns" 
            :key="index"
            :prop="col"
            :label="col"
            min-width="120"
          />
        </el-table>
        <div class="preview-info">
          共检测到 {{ totalRows }} 行数据，显示前 5 行预览
        </div>
      </div>

      <div v-if="uploadResult" class="result-section">
        <el-alert
          :type="uploadResult.success ? 'success' : 'error'"
          :title="uploadResult.message"
          show-icon
          :closable="false"
        />
        <div v-if="uploadResult.details" class="result-details">
          <p><strong>成功:</strong> {{ uploadResult.details.successCount }} 条</p>
          <p><strong>失败:</strong> {{ uploadResult.details.failCount }} 条</p>
          <p v-if="uploadResult.details.errorMessage">
            <strong>错误信息:</strong> {{ uploadResult.details.errorMessage }}
          </p>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleUpload"
          :loading="uploadLoading"
          :disabled="fileList.length === 0"
        >
          开始上传
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { useTaskStore } from '@/stores/task'

// Props
const props = defineProps({
  visible: Boolean,
  task: Object
})

// Emits
const emit = defineEmits(['update:visible', 'success'])

// Store
const taskStore = useTaskStore()

// 响应式数据
const uploadRef = ref()
const fileList = ref([])
const previewData = ref([])
const previewColumns = ref([])
const totalRows = ref(0)
const uploadLoading = ref(false)
const uploadResult = ref(null)

// 计算属性
const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// Watch
watch(visible, (val) => {
  if (!val) {
    resetState()
  }
})

// 方法
const resetState = () => {
  fileList.value = []
  previewData.value = []
  previewColumns.value = []
  totalRows.value = 0
  uploadResult.value = null
  uploadRef.value?.clearFiles()
}

const handleClose = () => {
  visible.value = false
}

const handleFileChange = (file, fileList) => {
  if (fileList.length > 1) {
    fileList.splice(0, 1)
  }
  
  const rawFile = file.raw
  if (!rawFile) return

  // 验证文件大小
  if (rawFile.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    uploadRef.value?.abort(file)
    uploadRef.value?.handleRemove(file)
    return
  }

  // 预览文件内容
  previewFile(rawFile)
}

const handleFileRemove = () => {
  previewData.value = []
  previewColumns.value = []
  totalRows.value = 0
}

const previewFile = (file) => {
  const reader = new FileReader()
  
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target.result)
      const workbook = XLSX.read(data, { type: 'array' })
      const firstSheetName = workbook.SheetNames[0]
      const worksheet = workbook.Sheets[firstSheetName]
      const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 })
      
      if (jsonData.length === 0) {
        ElMessage.warning('文件为空')
        return
      }

      // 提取列名（第一行）
      previewColumns.value = jsonData[0].map((_, index) => `列${index + 1}`)
      
      // 提取数据（从第二行开始）
      previewData.value = jsonData.slice(1, 6).map(row => {
        const obj = {}
        row.forEach((cell, index) => {
          obj[`列${index + 1}`] = cell ?? ''
        })
        return obj
      })
      
      totalRows.value = jsonData.length - 1
      
      if (totalRows.value === 0) {
        ElMessage.warning('文件中没有数据行')
      }
    } catch (error) {
      ElMessage.error('文件解析失败，请检查文件格式')
      console.error(error)
    }
  }
  
  reader.readAsArrayBuffer(file)
}

const handleUpload = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }

  const file = fileList.value[0].raw
  if (!file) return

  uploadLoading.value = true
  uploadResult.value = null

  try {
    // 读取完整文件数据
    const reader = new FileReader()
    reader.onload = async (e) => {
      try {
        const data = new Uint8Array(e.target.result)
        const workbook = XLSX.read(data, { type: 'array' })
        const firstSheetName = workbook.SheetNames[0]
        const worksheet = workbook.Sheets[firstSheetName]
        const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 })

        // 转换为上传格式
        const uploadData = jsonData.slice(1).map(row => ({
          callee: row[0]?.toString() || '', // 假设第一列是电话号码
          name: row[1]?.toString() || '',   // 假设第二列是姓名
          remark: row[2]?.toString() || ''  // 假设第三列是备注
        })).filter(item => item.callee && /^\d{11}$/.test(item.callee)) // 只保留有效的手机号

        if (uploadData.length === 0) {
          throw new Error('文件中没有有效的电话号码数据')
        }

        // 调用上传接口
        const result = await taskStore.uploadCallList(props.task.id, uploadData)
        
        uploadResult.value = {
          success: true,
          message: `上传成功！共处理 ${uploadData.length} 条数据`,
          details: result
        }
        
        ElMessage.success('上传成功')
        setTimeout(() => {
          emit('success')
          handleClose()
        }, 1500)
        
      } catch (error) {
        uploadResult.value = {
          success: false,
          message: '上传失败',
          details: {
            errorMessage: error.message
          }
        }
        ElMessage.error(error.message)
      } finally {
        uploadLoading.value = false
      }
    }
    
    reader.readAsArrayBuffer(file)
    
  } catch (error) {
    uploadLoading.value = false
    ElMessage.error('上传失败: ' + error.message)
  }
}

const getStatusTag = (status) => {
  const map = {
    0: 'info',
    1: 'success',
    2: 'warning'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    0: '已停止',
    1: '运行中',
    2: '已完成'
  }
  return map[status] || '未知'
}
</script>

<style scoped lang="scss">
.upload-container {
  .task-info {
    margin-bottom: 24px;
  }

  .upload-area {
    margin-bottom: 24px;
  }

  .preview-section {
    margin-bottom: 24px;
    
    h4 {
      margin-bottom: 12px;
      color: var(--text-primary);
    }
    
    .preview-info {
      margin-top: 8px;
      font-size: 14px;
      color: var(--text-secondary);
    }
  }

  .result-section {
    .result-details {
      margin-top: 12px;
      padding: 12px;
      background: var(--bg-primary);
      border-radius: 4px;
      
      p {
        margin: 4px 0;
        font-size: 14px;
      }
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
