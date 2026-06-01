const { t } = require('./i18n')

function syncStatusMeta(syncStatus) {
  const status = syncStatus || 'none'
  const map = {
    none: { text: '', tag: '' },
    pending: { textKey: 'sync.statusPending', tag: 'tag-warning' },
    synced: { textKey: 'sync.statusSynced', tag: 'tag-success' },
    sync_failed: { textKey: 'sync.statusFailed', tag: 'tag-error' }
  }
  const item = map[status] || map.none
  if (!item.textKey) {
    return { text: '', tag: '', status }
  }
  return { text: t(item.textKey), tag: item.tag, status }
}

function appendSyncToStatusText(statusText, taskStatus, syncStatus) {
  if (taskStatus !== 'confirmed' || !syncStatus || syncStatus === 'none') {
    return statusText
  }
  const sync = syncStatusMeta(syncStatus)
  if (!sync.text) {
    return statusText
  }
  return `${statusText} · ${sync.text}`
}

module.exports = { syncStatusMeta, appendSyncToStatusText }
