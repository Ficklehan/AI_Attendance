import { getToken } from './auth'
import { API_BASE_PATH } from '@/constants/apiBase'

/** Append JWT for authenticated image endpoints (img tags cannot send Authorization headers). */
export function withAuthToken(url) {
  if (!url || typeof url !== 'string') {
    return url
  }
  if (!url.includes('/local/image/')) {
    return url
  }
  const token = getToken()
  if (!token || url.includes('token=')) {
    return url
  }
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}token=${encodeURIComponent(token)}`
}

/** Build authenticated preview URLs for a task's stored image keys. */
export function resolveTaskImageUrls(imageUrls, fileKey) {
  const raw = []
  if (imageUrls) {
    try {
      const parsed = typeof imageUrls === 'string' ? JSON.parse(imageUrls) : imageUrls
      if (Array.isArray(parsed)) {
        for (const entry of parsed) {
          const trimmed = entry != null ? String(entry).trim() : ''
          if (trimmed && !raw.includes(trimmed)) {
            raw.push(trimmed)
          }
        }
      }
    } catch {
      // ignore parse errors
    }
  }
  const key = fileKey != null ? String(fileKey).trim() : ''
  if (key && !raw.includes(key)) {
    raw.unshift(key)
  }
  return raw.map((url) => {
    if (url.startsWith('http') || url.startsWith(API_BASE_PATH)) {
      return withAuthToken(url)
    }
    return withAuthToken(`${API_BASE_PATH}/local/image/${url}`)
  })
}

export function fileNameFromImageUrl(url) {
  if (!url) return ''
  const withoutQuery = String(url).split('?')[0]
  const parts = withoutQuery.split('/')
  const filename = parts[parts.length - 1] || ''
  return filename.replace(/\.[^/.]+$/, '') || filename
}
