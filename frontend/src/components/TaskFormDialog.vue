<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑任务' : '新建任务'"
    width="600px"
    :before-close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      @submit.prevent
    >
      <el-form-item label="任务名称" prop="taskName">
        <el-input 
          v-model="formData.taskName" 
          placeholder="请输入任务名称"
        />
      </el-form-item>

      <el-form-item label="任务编码" prop="taskCode">
        <el-input 
          v-model="formData.taskCode" 
          placeholder="请输入任务编码"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item label="任务类型" prop="taskType">
        <el-select 
          v-model="formData.taskType" 
          placeholder="请选择任务类型"
          style="width: 100%"
        >
          <el-option label="预测式外呼" value="PREDICTIVE" />
          <el-option label="定时外呼" value="TIMED" />
          <el-option label="手动外呼" value="MANUAL" />
        </el-select>
      </el-form-item>

      <el-form-item label="外呼号码" prop="outboundCaller">
        <el-input 
          v-model="formData.outboundCaller" 
          placeholder="请输入外呼显示号码"
        />
      </el-form-item>

      <el-form-item label="任务规则" prop="taskRulesCode">
        <el-select 
          v-model="formData.taskRulesCode" 
          placeholder="请选择任务规则"
          style="width: 100%"
          filterable
        >
          <el-option 
            v-for="rule in taskRules" 
            :key="rule.taskRulesCode"
            :label="`${rule.taskRulesCode} - ${rule.taskRulesName}`"
            :value="rule.taskRulesCode"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="转接类型" prop="taskTransferType">
        <el-select 
          v-model="formData.taskTransferType" 
          placeholder="请选择转接类型"
          style="width: 100%"
        >
          <el-option label="直接转接" value="DIRECT" />
          <el-option label="语音导航" value="IVR" />
          <el-option label="人工坐席" value="AGENT" />
        </el-select>
      </el-form-item>

      <el-form-item label="环境标识" prop="envFlag">
        <el-input 
          v-model="formData.envFlag" 
          placeholder="请输入环境标识（如：dev/prod）"
        />
      </el-form-item>

      <el-form-item label="备注说明">
        <el-input 
          v-model="formData.remarks" 
          type="textarea"
          :rows="3"
          placeholder="请输入任务备注"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleSubmit"
          :loading="submitLoading"
        >
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, reactive } from 'vue'
import { ElMessage } from 'element-plus'
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
const formRef = ref()
const submitLoading = ref(false)
const taskRules = ref([])

const formData = reactive({
  taskName: '',
  taskCode: '',
  taskType: '',
  outboundCaller: '',
  taskRulesCode: '',
  taskTransferType: '',
  envFlag: 'prod',
  remarks: ''
})

const rules = {
  taskName: [
    { required: true, message: '请输入任务名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  taskCode: [
    { required: true, message: '请输入任务编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]+$/, message: '只能包含大写字母、数字和下划线', trigger: 'blur' }
  ],
  taskType: [
    { required: true, message: '请选择任务类型', trigger: 'change' }
  ],
  outboundCaller: [
    { required: true, message: '请输入外呼号码', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ],
  taskRulesCode: [
    { required: true, message: '请选择任务规则', trigger: 'change' }
  ],
  taskTransferType: [
    { required: true, message: '请选择转接类型', trigger: 'change' }
  ],
  envFlag: [
    { required: true, message: '请输入环境标识', trigger: 'blur' }
  ]
}

// 计算属性
const isEdit = computed(() => !!props.task)
const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// Watch
watch(() => props.task, (newTask) => {
  if (newTask) {
    Object.assign(formData, {
      taskName: newTask.taskName || '',
      taskCode: newTask.taskCode || '',
      taskType: newTask.taskType || '',
      outboundCaller: newTask.outboundCaller || '',
      taskRulesCode: newTask.taskRulesCode || '',
      taskTransferType: newTask.taskTransferType || '',
      envFlag: newTask.envFlag || 'prod',
      remarks: newTask.remarks || ''
    })
  } else {
    resetForm()
  }
})

watch(visible, (val) => {
  if (val) {
    fetchTaskRules()
  }
})

// 方法
const fetchTaskRules = async () => {
  try {
    taskRules.value = await taskStore.fetchTaskRules()
  } catch (error) {
    ElMessage.error('获取任务规则失败')
  }
}

const resetForm = () => {
  Object.assign(formData, {
    taskName: '',
    taskCode: '',
    taskType: '',
    outboundCaller: '',
    taskRulesCode: '',
    taskTransferType: '',
    envFlag: 'prod',
    remarks: ''
  })
  formRef.value?.resetFields()
}

const handleClose = () => {
  visible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await taskStore.updateTask(props.task.id, formData)
        ElMessage.success('更新任务成功')
      } else {
        await taskStore.createTask(formData)
        ElMessage.success('创建任务成功')
      }
      emit('success')
      handleClose()
    } catch (error) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 初始化
if (props.task) {
  Object.assign(formData, props.task)
}
</script>

<style scoped lang="scss">
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
