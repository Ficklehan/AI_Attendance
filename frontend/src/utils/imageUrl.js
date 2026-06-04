import request from '@/api/index'
import { API_BASE_PATH } from '@/constants/apiBase'

function collectImageKeys(imageUrls, fileKey) {
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
  return raw
}

function appendSignature(baseUrl, signature) {
  if (!signature || signature.exp == null || !signature.uid || !signature.sig) {
    return baseUrl
  }
  const sep = baseUrl.includes('?') ? '&' : '?'
  return `${baseUrl}${sep}exp=${signature.exp}&uid=${encodeURIComponent(signature.uid)}&sig=${encodeURIComponent(signature.sig)}`
}

/** Build short-lived signed preview URLs for task images (img tags cannot send Authorization headers). */
export async function resolveTaskImageUrls(imageUrls, fileKey) {
  const keys = collectImageKeys(imageUrls, fileKey)
  if (!keys.length) {
    return []
  }

  let signatures = {}
  try {
    const res = await request({
      url: '/local/image/signatures',
      method: 'post',
      data: { keys },
    })
    signatures = res.data || {}
  } catch (error) {
    console.error('加载图片签名失败:', error)
  }

  return keys.map((key) => {
    const normalized = key.startsWith('http') || key.startsWith(API_BASE_PATH)
      ? key
      : `${API_BASE_PATH}/local/image/${encodeURIComponent(key)}`
    return appendSignature(normalized, signatures[key])
  })
}

export function fileNameFromImageUrl(url) {
  if (!url) return ''
  const withoutQuery = String(url).split('?')[0]
  const parts = withoutQuery.split('/')
  const filename = parts[parts.length - 1] || ''
  return filename.replace(/\.[^/.]+$/, '') || filename
}
