import { createRouter, createWebHistory } from 'vue-router'
import TaskManagement from '@/pages/TaskManagement.vue'
import TaskDetail from '@/pages/TaskDetail.vue'

const routes = [
  {
    path: '/',
    redirect: '/tasks'
  },
  {
    path: '/tasks',
    name: 'TaskManagement',
    component: TaskManagement
  },
  {
    path: '/tasks/:taskCode',
    name: 'TaskDetail',
    component: TaskDetail
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
