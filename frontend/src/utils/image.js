export async function compressImage(file, options = {}) {
  const {
    maxSizeKB = 2000,
    maxWidth = 2000,
    maxHeight = 2000,
    quality = 0.8
  } = options

  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = async (e) => {
      const img = new Image()
      img.onload = async () => {
        try {
          const compressed = await processImage(img, file.type, {
            maxSizeKB,
            maxWidth,
            maxHeight,
            quality
          })
          resolve(compressed)
        } catch (err) {
          reject(err)
        }
      }
      img.onerror = () => reject(new Error('图片加载失败'))
      img.src = e.target.result
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsDataURL(file)
  })
}

async function processImage(img, mimeType, options) {
  const { maxWidth, maxHeight, quality } = options
  let { maxSizeKB } = options

  let width = img.width
  let height = img.height

  if (width > maxWidth || height > maxHeight) {
    const ratio = Math.min(maxWidth / width, maxHeight / height)
    width = Math.round(width * ratio)
    height = Math.round(height * ratio)
  }

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const ctx = canvas.getContext('2d')
  ctx.drawImage(img, 0, 0, width, height)

  // 确保使用支持的MIME类型，优先使用image/jpeg
  const supportedMimeTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp']
  let actualMimeType = mimeType
  if (!supportedMimeTypes.includes(mimeType)) {
    actualMimeType = 'image/jpeg'
  }

  let qualityLevel = quality
  let blob = await canvasToBlob(canvas, actualMimeType, qualityLevel)

  while (blob.size > maxSizeKB * 1024 && qualityLevel > 0.1) {
    qualityLevel -= 0.1
    blob = await canvasToBlob(canvas, actualMimeType, qualityLevel)
  }

  if (blob.size > maxSizeKB * 1024 && width > 800) {
    const newWidth = Math.round(width * 0.7)
    const newHeight = Math.round(height * 0.7)
    canvas.width = newWidth
    canvas.height = newHeight
    ctx.drawImage(img, 0, 0, newWidth, newHeight)
    blob = await canvasToBlob(canvas, actualMimeType, 0.6)
  }

  return new File([blob], 'image.jpg', {
    type: actualMimeType,
    lastModified: Date.now()
  })
}

function canvasToBlob(canvas, mimeType, quality) {
  return new Promise((resolve) => {
    canvas.toBlob(
      (blob) => resolve(blob),
      mimeType,
      quality
    )
  })
}

export async function getImageHash(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = async (e) => {
      try {
        const buffer = e.target.result
        const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
        const hashArray = Array.from(new Uint8Array(hashBuffer))
        const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
        resolve(hashHex)
      } catch (err) {
        reject(err)
      }
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsArrayBuffer(file)
  })
}

export function getFileSizeDisplay(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
