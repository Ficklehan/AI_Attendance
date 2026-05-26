import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { getTaskList, getTaskDetail, confirmTask, deleteTask } from '@/api/task'

export function useTask() {
  const tasks = ref([])
  const currentTask = ref(null)
  const loading = ref(false)
  const total = ref(0)
  
  const loadTasks = async (params = {}) => {
    loading.value = true
    try {
      const response = await getTaskList(params)
      tasks.value = response.data.records || []
      total.value = response.data.total || 0
      return response
    } catch (error) {
      message.error('加载任务列表失败')
      throw error
    } finally {
      loading.value = false
    }
  }
  
  const loadTaskDetail = async (taskId) => {
    loading.value = true
    try {
      const response = await getTaskDetail(taskId)
      currentTask.value = response.data
      return response
    } catch (error) {
      message.error('加载任务详情失败')
      throw error
    } finally {
      loading.value = false
    }
  }
  
  const submitTask = async (taskId, data) => {
    loading.value = true
    try {
      await confirmTask(taskId, { data })
      message.success('任务提交成功')
      return true
    } catch (error) {
      message.error('任务提交失败')
      throw error
    } finally {
      loading.value = false
    }
  }
  
  const removeTask = async (taskId) => {
    try {
      await deleteTask(taskId)
      message.success('任务删除成功')
      tasks.value = tasks.value.filter(t => t.taskId !== taskId)
      total.value--
      return true
    } catch (error) {
      message.error('任务删除失败')
      throw error
    }
  }
  
  const parseTaskData = (task) => {
    if (!task) return []
    const data = task.confirmedData || task.rawData
    if (!data) return []
    
    try {
      return JSON.parse(data)
    } catch (e) {
      console.error('解析任务数据失败', e)
      return []
    }
  }
  
  return {
    tasks,
    currentTask,
    loading,
    total,
    loadTasks,
    loadTaskDetail,
    submitTask,
    removeTask,
    parseTaskData,
  }
}