import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 可以在这里添加认证token
    // const token = localStorage.getItem('token')
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`
    // }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    // 统一处理响应数据
    return response.data
  },
  (error) => {
    console.error('API 请求错误:', error.config?.url, error.message)
    
    // 统一错误处理
    if (error.response) {
      const { status, data } = error.response
      switch (status) {
        case 400:
          error.message = data?.message || '请求参数错误'
          break
        case 401:
          error.message = '未授权，请重新登录'
          // 可以跳转到登录页
          // window.location.href = '/login'
          break
        case 403:
          error.message = '拒绝访问'
          break
        case 404:
          error.message = '请求资源不存在'
          break
        case 500:
          error.message = data?.message || '服务器内部错误'
          break
        default:
          error.message = data?.message || `连接错误 ${status}`
      }
    } else if (error.request) {
      if (error.code === 'ECONNREFUSED') {
        error.message = `连接被拒绝：无法连接到后端服务 (http://localhost:8080)`
      } else {
        error.message = '网络连接异常'
      }
    } else {
      error.message = error.message || '未知错误'
    }
    
    return Promise.reject(error)
  }
)

// 队列相关 API
export const queueApi = {
  // 根据任务编码获取队列列表
  getByTaskCode: (taskCode, instanceId = 'INSTANCE_001', envId = 'test') => 
    api.get(`/v1/outcall-queue/by-task/${taskCode}`, {
      params: { instanceId, envId }
    }),
  
  // 更新队列状态
  updateStatus: (instanceId, queueCode, envId, status) =>
    api.put('/v1/outcall-queue/status', null, {
      params: { instanceId, queueCode, envId, status }
    }),
  
  // 分页查询队列
  getPage: (instanceId, pageNum = 1, pageSize = 20) =>
    api.get('/v1/outcall-queue/page', {
      params: { instanceId, pageNum, pageSize }
    })
}

export default api
