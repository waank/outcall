import { defineStore } from 'pinia'
import api from '@/services/api'

export const useTaskStore = defineStore('task', {
  state: () => ({
    tasks: [],
    loading: false
  }),

  getters: {
    taskById: (state) => (id) => state.tasks.find(task => task.id === id)
  },

  actions: {
    // 获取任务列表
    async fetchTasks() {
      this.loading = true
      try {
        const response = await api.get('/v1/outbound-task/page?instanceId=INSTANCE_001&pageNum=1&pageSize=100')
        this.tasks = response.data?.records || []
        return this.tasks
      } catch (error) {
        console.error('获取任务列表失败:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    // 创建任务
    async createTask(taskData) {
      try {
        const response = await api.post('/v1/outbound-task', taskData)
        // 重新获取列表以更新状态
        await this.fetchTasks()
        return response.data
      } catch (error) {
        console.error('创建任务失败:', error)
        throw error
      }
    },

    // 更新任务
    async updateTask(id, taskData) {
      try {
        const response = await api.put('/v1/outbound-task', taskData)
        // 更新本地状态
        const index = this.tasks.findIndex(task => task.id === id)
        if (index !== -1) {
          this.tasks[index] = { ...this.tasks[index], ...taskData }
        }
        return response.data
      } catch (error) {
        console.error('更新任务失败:', error)
        throw error
      }
    },

    // 删除任务
    async deleteTask(id) {
      try {
        await api.delete(`/v1/outbound-task/${id}`)
        // 从本地状态移除
        this.tasks = this.tasks.filter(task => task.id !== id)
      } catch (error) {
        console.error('删除任务失败:', error)
        throw error
      }
    },

    // 更新任务状态（启动/停止）
    async updateTaskStatus(taskCode, status) {
      try {
        const response = await api.put(`/v1/outbound-task/status?instanceId=INSTANCE_001&taskCode=${taskCode}&status=${status}`)
        // 更新本地状态
        const index = this.tasks.findIndex(task => task.taskCode === taskCode)
        if (index !== -1) {
          this.tasks[index].taskStatus = status
        }
        return response.data
      } catch (error) {
        console.error('更新任务状态失败:', error)
        throw error
      }
    },

    // 获取任务规则列表
    async fetchTaskRules() {
      try {
        const response = await api.get('/v1/outbound-call-task-rules/page?instanceId=INSTANCE_001&pageNum=1&pageSize=100')
        return response.data || []
      } catch (error) {
        console.error('获取任务规则失败:', error)
        return []
      }
    },

    // 上传呼叫名单
    async uploadCallList(taskId, callList) {
      try {
        // TODO: 需要实现文件上传接口
        // const response = await api.post(`/v1/outbound-task/upload/${taskId}`, {
        //   callList: callList
        // })
        // return response.data
        console.log('TODO: 实现文件上传功能')
        return { success: true }
      } catch (error) {
        console.error('上传呼叫名单失败:', error)
        throw error
      }
    },

    // 获取队列组列表
    async fetchQueueGroups(taskCode) {
      try {
        const response = await api.get(`/v1/outcall-queue-group/page?instanceId=INSTANCE_001&taskCode=${taskCode}&pageNum=1&pageSize=100`)
        return response.data || []
      } catch (error) {
        console.error('获取队列组失败:', error)
        return []
      }
    },

    // 创建队列组
    async createQueueGroup(groupData) {
      try {
        const response = await api.post('/v1/outcall-queue-group', groupData)
        return response.data
      } catch (error) {
        console.error('创建队列组失败:', error)
        throw error
      }
    },

    // 更新队列组状态
    async updateQueueGroupStatus(groupId, status) {
      try {
        const response = await api.put(`/v1/outcall-queue-group/${groupId}/status`, { 
          groupStatus: status 
        })
        return response.data
      } catch (error) {
        console.error('更新队列组状态失败:', error)
        throw error
      }
    }
  }
})
