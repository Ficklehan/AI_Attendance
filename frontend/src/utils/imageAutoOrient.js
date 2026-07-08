import { countHeaderKeywordHits } from '@/constants/tableHeaderKeywords'

const ORIENT_ANGLES = [0, 90, 180, 270]
const HEURISTIC_MAX_DIM = 560
const OCR_MAX_WIDTH = 560
const OCR_TOP_MAX_HEIGHT = 96
const orientationCache = new Map()

let ocrWorkerPromise = null

async function getOcrWorker() {
  if (!ocrWorkerPromise) {
    ocrWorkerPromise = (async () => {
      const { createWorker } = await import('tesseract.js')
      const langs = ['eng', 'fra+eng']
      for (const lang of langs) {
        try {
          const worker = await createWorker(lang, 1, { logger: () => {} })
          await worker.setParameters({
            tessedit_pageseg_mode: '6',
            tessedit_char_whitelist: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789°ÉÈÊËÀÂÄÙÛÜÇÑ',
          })
          return worker
        } catch {
          // try next language pack
        }
      }
      throw new Error('ocr worker unavailable')
    })()
  }
  return ocrWorkerPromise
}

/** 进入考勤/预览页时预热 OCR，避免首次扶正等待 worker 下载 */
export function warmupOcrWorker() {
  getOcrWorker().catch(() => {})
}

function isSameOriginUrl(src) {
  try {
    const url = new URL(src, window.location.href)
    return url.origin === window.location.origin
  } catch {
    return false
  }
}

function loadImageFromSrc(src, useCrossOrigin) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    if (useCrossOrigin) {
      img.crossOrigin = 'anonymous'
    }
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = src
  })
}

async function loadImageElement(src) {
  if (isSameOriginUrl(src)) {
    try {
      return await loadImageFromSrc(src, false)
    } catch {
      // fall through to cross-origin strategies
    }
  }

  try {
    return await loadImageFromSrc(src, true)
  } catch {
    try {
      const response = await fetch(src, { credentials: 'include' })
      if (!response.ok) {
        throw new Error('fetch failed')
      }
      const blob = await response.blob()
      const objectUrl = URL.createObjectURL(blob)
      try {
        return await loadImageFromSrc(objectUrl, false)
      } finally {
        URL.revokeObjectURL(objectUrl)
      }
    } catch {
      throw new Error('image load failed')
    }
  }
}

function drawRotatedImage(img, angleDeg, maxDim = HEURISTIC_MAX_DIM) {
  const radians = (angleDeg * Math.PI) / 180
  const rawW = angleDeg % 180 === 0 ? img.naturalWidth : img.naturalHeight
  const rawH = angleDeg % 180 === 0 ? img.naturalHeight : img.naturalWidth
  const scale = Math.min(1, maxDim / Math.max(rawW, rawH))
  const width = Math.max(1, Math.round(rawW * scale))
  const height = Math.max(1, Math.round(rawH * scale))

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) {
    return { canvas, width, height, ctx: null }
  }

  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, width, height)
  ctx.translate(width / 2, height / 2)
  ctx.rotate(radians)
  ctx.drawImage(
    img,
    (-img.naturalWidth * scale) / 2,
    (-img.naturalHeight * scale) / 2,
    img.naturalWidth * scale,
    img.naturalHeight * scale,
  )
  return { canvas, width, height, ctx }
}

function scoreInkBand(data, width, bandHeight, startY, step = 2) {
  let score = 0
  const rowInk = new Array(bandHeight).fill(0)

  for (let y = 0; y < bandHeight; y += step) {
    const srcY = startY + y
    for (let x = 0; x < width; x += step) {
      const idx = (srcY * width + x) * 4
      const gray = data[idx] * 0.299 + data[idx + 1] * 0.587 + data[idx + 2] * 0.114
      if (gray < 175) {
        rowInk[y] += 1
      }
    }
  }

  for (let y = 2; y < bandHeight - 2; y += 1) {
    const ink = rowInk[y]
    if (ink < (width / step) * 0.06) continue
    const topWeight = 1.35 - y / bandHeight
    const lineBonus = ink > (width / step) * 0.2 ? 1.3 : 1
    score += ink * topWeight * lineBonus
  }
  return score
}

function scoreInkLayout(ctx, width, height) {
  const step = 2
  const topHeight = Math.max(24, Math.floor(height * 0.28))
  const bottomHeight = Math.max(24, Math.floor(height * 0.24))
  const bottomStart = Math.max(0, height - bottomHeight)
  const full = ctx.getImageData(0, 0, width, height)
  const data = full.data

  const topScore = scoreInkBand(data, width, topHeight, 0, step)
  const bottomScore = scoreInkBand(data, width, bottomHeight, bottomStart, step)
  const asymmetry = topScore - bottomScore * 0.6
  const portraitBonus = height >= width * 1.05 ? 14 : width >= height * 1.12 ? -6 : 0
  return topScore + Math.max(0, asymmetry) * 0.75 + portraitBonus
}

async function ocrKeywordHits(canvas, width, height, worker) {
  const topHeight = Math.min(OCR_TOP_MAX_HEIGHT, Math.max(40, Math.floor(height * 0.3)))
  const ocrWidth = Math.min(OCR_MAX_WIDTH, width)
  const topCanvas = document.createElement('canvas')
  topCanvas.width = ocrWidth
  topCanvas.height = topHeight
  const topCtx = topCanvas.getContext('2d')
  if (!topCtx) return 0

  topCtx.fillStyle = '#fff'
  topCtx.fillRect(0, 0, ocrWidth, topHeight)
  topCtx.drawImage(canvas, 0, 0, width, topHeight, 0, 0, ocrWidth, topHeight)

  try {
    const { data } = await worker.recognize(topCanvas)
    return countHeaderKeywordHits(data?.text || '')
  } catch {
    return 0
  }
}

function compareCandidates(a, b) {
  if (b.keywordHits !== a.keywordHits) {
    return b.keywordHits - a.keywordHits
  }
  return b.inkScore - a.inkScore
}

function buildConfidence(best, second) {
  if (best.keywordHits >= 2 && best.keywordHits > (second?.keywordHits || 0)) {
    return 'high'
  }
  return 'low'
}

function pickBestCandidate(candidates) {
  const sorted = [...candidates].sort(compareCandidates)
  const best = sorted[0]
  if (!best || best.keywordHits < 1) {
    return null
  }
  const second = sorted[1]
  return {
    rotation: best.angle,
    confidence: buildConfidence(best, second),
  }
}

function shouldStopEarly(candidates, topInkAngle) {
  const sorted = [...candidates].sort(compareCandidates)
  const best = sorted[0]
  if (!best) return false
  if (best.keywordHits >= 2) return true
  if (best.keywordHits >= 1 && best.angle === topInkAngle) return true
  return false
}

const NO_HIT = { rotation: 0, confidence: 'none' }

/**
 * 检测使表头朝上的旋转角度（0/90/180/270 中取最优）。
 * 仅在 OCR 识别到表头关键词时返回旋转；未命中则保持原图不处理。
 */
export async function detectTableHeaderRotation(imageSrc) {
  if (!imageSrc) {
    return NO_HIT
  }

  const cached = orientationCache.get(imageSrc)
  if (cached) {
    return cached
  }

  let img
  try {
    img = await loadImageElement(imageSrc)
  } catch {
    return NO_HIT
  }

  let worker
  try {
    worker = await getOcrWorker()
  } catch {
    worker = null
  }

  const prepared = ORIENT_ANGLES.map((angle) => {
    const { canvas, width, height, ctx } = drawRotatedImage(img, angle, HEURISTIC_MAX_DIM)
    return {
      angle,
      canvas,
      width,
      height,
      inkScore: ctx ? scoreInkLayout(ctx, width, height) : 0,
    }
  }).sort((a, b) => b.inkScore - a.inkScore)

  const topInkAngle = prepared[0]?.angle
  const candidates = []

  const runOcrBatch = async (items) => {
    if (!worker) {
      return []
    }
    const results = await Promise.all(
      items.map(async (item) => ({
        angle: item.angle,
        inkScore: item.inkScore,
        keywordHits: await ocrKeywordHits(item.canvas, item.width, item.height, worker),
      })),
    )
    candidates.push(...results)
    return results
  }

  await runOcrBatch(prepared.slice(0, 2))
  if (!shouldStopEarly(candidates, topInkAngle)) {
    await runOcrBatch(prepared.slice(2))
  }

  const result = pickBestCandidate(candidates)
  if (!result) {
    orientationCache.set(imageSrc, NO_HIT)
    return NO_HIT
  }

  orientationCache.set(imageSrc, result)
  return result
}

export function clearOrientationCache() {
  orientationCache.clear()
}

export function invalidateOrientationCache(imageSrc) {
  if (imageSrc) {
    orientationCache.delete(imageSrc)
  }
}
