import { ref } from 'vue'
import { getExportSummary } from '@/api/export'

const exportDrawerOpen = ref(false)
const activeExportCount = ref(0)
let summaryTimer = null

async function refreshExportSummary() {
  try {
    const res = await getExportSummary()
    activeExportCount.value = Number(res.data?.activeCount || 0)
  } catch {
    // ignore when unauthenticated or offline
  }
}

function startSummaryPolling() {
  stopSummaryPolling()
  refreshExportSummary()
  summaryTimer = window.setInterval(refreshExportSummary, 15000)
}

function stopSummaryPolling() {
  if (summaryTimer) {
    clearInterval(summaryTimer)
    summaryTimer = null
  }
}

export function useExportCenter() {
  const openExportCenter = () => {
    exportDrawerOpen.value = true
  }

  return {
    exportDrawerOpen,
    activeExportCount,
    openExportCenter,
    refreshExportSummary,
    startSummaryPolling,
    stopSummaryPolling,
  }
}

export { startSummaryPolling, stopSummaryPolling, refreshExportSummary }
