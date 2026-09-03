function sanitizeFileName(name) {
  const raw = String(name || '').trim()
  if (!raw) return ''
  return raw.replace(/[\\/:*?"<>|]+/g, '_')
}

export function resolvePreviewDownloadName(src, explicitName, index = 0) {
  const named = sanitizeFileName(explicitName)
  if (named) return named

  if (typeof src === 'string' && src.startsWith('blob:')) {
    return `image-${index + 1}.jpg`
  }

  try {
    const path = new URL(src, window.location.href).pathname
    const base = decodeURIComponent(path.split('/').pop() || '')
    if (base && base.includes('.')) return base
  } catch {
    /* ignore */
  }

  return `image-${index + 1}.jpg`
}

export async function downloadPreviewImage(src, filename) {
  if (!src) {
    throw new Error('missing image source')
  }

  const name = sanitizeFileName(filename) || 'image.jpg'

  if (typeof src === 'string' && src.startsWith('blob:')) {
    const link = document.createElement('a')
    link.href = src
    link.download = name
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    link.remove()
    return
  }

  const response = await fetch(src, { credentials: 'include' })
  if (!response.ok) {
    throw new Error(`download failed: ${response.status}`)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  try {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = name
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}
