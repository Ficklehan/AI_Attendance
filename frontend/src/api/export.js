import request from './index'
import { getToken } from '@/utils/auth'
import { API_BASE_PATH } from '@/constants/apiBase'

export function createTaskListExport(query) {
  return request.post('/exports/task-list', query || {})
}

export function createEmployeeRecordsExport(query) {
  return request.post('/exports/employee-records', query || {})
}

export function listExportJobs(params) {
  return request.get('/exports', { params })
}

/** @param {'active'|'all'} scope active=最近未清空, all=全部历史 */
export function listExportJobsByScope(params, scope = 'active') {
  return request.get('/exports', { params: { ...params, scope } })
}

export function getExportSummary() {
  return request.get('/exports/summary')
}

export function clearFinishedExports() {
  return request.post('/exports/clear')
}

export function getExportJob(jobId) {
  return request.get(`/exports/${jobId}`)
}

export async function downloadExportJob(jobId, fileName) {
  const token = getToken()
  const response = await fetch(`${API_BASE_PATH}/exports/${jobId}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error(`download failed: ${response.status}`)
  }
  const blob = await response.blob()
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || `export_${jobId}.xlsx`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
