/** 单元格内容过长时缩小字号，按列宽用文字实测宽度缩放，尽量单行完整展示。 */

const DEFAULT_MAX = 12
const DEFAULT_MIN = 8
const WIDTH_HYSTERESIS_PX = 8
const TARGET_SELECTOR = [
  'input.ant-input',
  'textarea.ant-input',
  '.ant-input-number-input',
  '.ant-picker-input > input',
  '.ant-select-selection-item',
  '.cell-text',
  '.work-hours',
  '.recognition-note-list__text',
  'a.task-id-link',
  'button.ant-btn',
  'a.ant-btn',
  '.ant-tag',
].join(', ')
const INTRINSIC_SIZE_SELECTOR = 'button, .ant-btn, .ant-tag, a.task-id-link, a.ant-btn'
const FONT_SYNC_SELECTOR = 'button.ant-btn, a.ant-btn, .ant-tag, a.task-id-link'

let measureCanvas

function getMeasureContext() {
  if (typeof document === 'undefined') return null
  if (!measureCanvas) measureCanvas = document.createElement('canvas')
  return measureCanvas.getContext('2d')
}

function measureTextPx(text, font) {
  const ctx = getMeasureContext()
  const value = String(text || '')
  if (!ctx) {
    let width = 0
    for (const ch of value) width += ch.charCodeAt(0) > 255 ? 14 : 8
    return width
  }
  ctx.font = font
  return ctx.measureText(value).width
}

function readOptions(binding) {
  const value = binding?.value
  if (value == null || typeof value === 'boolean') {
    return { max: DEFAULT_MAX, min: DEFAULT_MIN, wrap: true, reserveChars: 0, enabled: value !== false }
  }
  if (typeof value === 'number') {
    return { max: value, min: DEFAULT_MIN, wrap: true, reserveChars: 0, enabled: true }
  }
  return {
    max: Number(value.max) > 0 ? Number(value.max) : DEFAULT_MAX,
    min: Number(value.min) > 0 ? Number(value.min) : DEFAULT_MIN,
    wrap: value.wrap !== false,
    reserveChars: Number(value.reserveChars) > 0 ? Number(value.reserveChars) : 0,
    enabled: value.enabled !== false,
  }
}

function availableWidth(el) {
  if (!el) return 0
  const td = el.closest('td, th')
  let width = 0
  if (td && td.clientWidth > 0) {
    const cs = window.getComputedStyle(td)
    const pad = (parseFloat(cs.paddingLeft) || 0) + (parseFloat(cs.paddingRight) || 0)
    width = Math.max(0, td.clientWidth - pad)
  } else {
    width = el.clientWidth
  }
  const copyBtn = el.closest('.copyable-cell')?.querySelector('.copyable-cell__btn')
  if (copyBtn) {
    width = Math.max(0, width - (copyBtn.offsetWidth || 18) - 4)
  }
  return width
}

function nodeText(node) {
  if (!node) return ''
  if (node.tagName === 'INPUT' || node.tagName === 'TEXTAREA') {
    return String(node.value || '')
  }
  return String(node.textContent || '').replace(/\s+/g, ' ').trim()
}

function nodeFont(node, sizePx) {
  const cs = window.getComputedStyle(node)
  const style = cs.fontStyle || 'normal'
  const weight = cs.fontWeight || '400'
  const family = cs.fontFamily || 'sans-serif'
  return `${style} ${weight} ${sizePx}px ${family}`
}

function isIntrinsicSized(node) {
  return Boolean(node?.matches?.(INTRINSIC_SIZE_SELECTOR))
}

function textFitWidth(node, cellWidth) {
  const cs = window.getComputedStyle(node)
  const pad = (parseFloat(cs.paddingLeft) || 0) + (parseFloat(cs.paddingRight) || 0)
  const border = (parseFloat(cs.borderLeftWidth) || 0) + (parseFloat(cs.borderRightWidth) || 0)
  // 按钮/链接等按内容撑开时，必须用单元格可用宽度，否则永远判定为“刚好放下”
  const raw = (cellWidth > 0 && (isIntrinsicSized(node) || node.clientWidth <= 8))
    ? cellWidth
    : (node.clientWidth > 8 ? Math.min(cellWidth || node.clientWidth, node.clientWidth) : cellWidth)
  return Math.max(0, raw - pad - border)
}

function reserveWidth(node, sizePx, reserveChars) {
  if (!(reserveChars > 0)) return 0
  return measureTextPx('M'.repeat(reserveChars), nodeFont(node, sizePx))
}

function targetNodes(el) {
  const nodes = [...el.querySelectorAll(TARGET_SELECTOR)].filter((node) => node !== el)
  if (nodes.length) return nodes
  if (el.matches?.(TARGET_SELECTOR) || el.classList.contains('cell-text')) return [el]
  return [el]
}

function requiredFontSize(el, max, min, cellWidth, reserveChars) {
  let next = max
  for (const node of targetNodes(el)) {
    const text = nodeText(node)
    if (!text) continue
    const fitWidth = textFitWidth(node, cellWidth)
    if (fitWidth <= 0) continue
    const font = nodeFont(node, max)
    const needed = measureTextPx(text, font) + reserveWidth(node, max, reserveChars)
    if (needed <= fitWidth) continue
    next = Math.min(next, max * (fitWidth / needed))
  }
  return Math.max(min, Math.min(max, next))
}

function applyFontSize(el, sizePx) {
  el.style.fontSize = `${sizePx}px`
  el.style.setProperty('--cell-auto-fs', `${sizePx}px`)
  el.querySelectorAll(FONT_SYNC_SELECTOR).forEach((node) => {
    node.style.fontSize = `${sizePx}px`
    node.style.lineHeight = '1.25'
  })
}

function clearSyncedFontSize(el) {
  el.querySelectorAll(FONT_SYNC_SELECTOR).forEach((node) => {
    node.style.fontSize = ''
    node.style.lineHeight = ''
  })
}

function contentSignature(el) {
  const input = el.querySelector('input.ant-input, textarea.ant-input, .ant-input-number-input')
  if (input) return String(input.value || '')
  return String(el.textContent || '').replace(/\s+/g, ' ').trim()
}

function prepareInputs(el) {
  el.querySelectorAll('input.ant-input').forEach((input) => {
    if (input.getAttribute('size') !== '1') input.setAttribute('size', '1')
  })
}

function stillOverflows(el, sizePx, cellWidth, reserveChars) {
  for (const node of targetNodes(el)) {
    const text = nodeText(node)
    if (!text) continue
    const needed = measureTextPx(text, nodeFont(node, sizePx)) + reserveWidth(node, sizePx, reserveChars)
    if (needed > textFitWidth(node, cellWidth) + 0.5) return true
  }
  return false
}

function fitElement(el, options) {
  if (!el || options.enabled === false) return
  const max = Math.max(options.min, options.max)
  const min = Math.min(options.min, options.max)
  const width = availableWidth(el)
  const reserveChars = options.reserveChars || 0
  if (width <= 0) return

  prepareInputs(el)
  el.style.whiteSpace = 'nowrap'
  const next = requiredFontSize(el, max, min, width, reserveChars)
  applyFontSize(el, next)

  if (next <= min && options.wrap !== false && stillOverflows(el, min, width, reserveChars)) {
    el.style.whiteSpace = 'normal'
  }
}

function shouldSkipFit(el, state) {
  const width = availableWidth(el)
  const signature = contentSignature(el)
  if (state.fittedSignature !== signature) return false
  if (state.fittedWidth < 0) return false
  return Math.abs(width - state.fittedWidth) < WIDTH_HYSTERESIS_PX
}

function scheduleFit(el, force = false) {
  const state = el.__autoShrinkFont
  if (!state || state.raf || state.fitting) return
  state.raf = window.requestAnimationFrame(() => {
    state.raf = 0
    if (!state || state.fitting) return
    if (!force && shouldSkipFit(el, state)) return
    state.fitting = true
    try {
      fitElement(el, state.options)
      state.fittedWidth = availableWidth(el)
      state.fittedSignature = contentSignature(el)
    } finally {
      state.fitting = false
    }
  })
}

function bind(el, binding) {
  const options = readOptions(binding)
  let state = el.__autoShrinkFont
  if (!state) {
    state = {
      options,
      raf: 0,
      fitting: false,
      fittedWidth: -1,
      fittedSignature: '',
      lastObservedWidth: -1,
      observer: null,
      onInput: () => {
        state.fittedWidth = -1
        state.fittedSignature = ''
        scheduleFit(el, true)
      },
    }
    el.__autoShrinkFont = state
    el.classList.add('cell-auto-shrink')
    el.addEventListener('input', state.onInput, true)
    el.addEventListener('change', state.onInput, true)
    if (typeof ResizeObserver !== 'undefined') {
      state.observer = new ResizeObserver((entries) => {
        if (state.fitting) return
        const width = Math.round(entries[0]?.contentRect?.width ?? availableWidth(el))
        if (state.lastObservedWidth >= 0 && Math.abs(width - state.lastObservedWidth) < WIDTH_HYSTERESIS_PX) {
          return
        }
        state.lastObservedWidth = width
        scheduleFit(el)
      })
      state.observer.observe(el)
      const td = el.closest('td, th')
      if (td && td !== el) state.observer.observe(td)
    }
  } else {
    state.options = options
  }
  scheduleFit(el)
}

function unbind(el) {
  const state = el.__autoShrinkFont
  if (!state) return
  if (state.raf) window.cancelAnimationFrame(state.raf)
  state.observer?.disconnect()
  el.removeEventListener('input', state.onInput, true)
  el.removeEventListener('change', state.onInput, true)
  el.classList.remove('cell-auto-shrink')
  el.style.fontSize = ''
  el.style.whiteSpace = ''
  el.style.removeProperty('--cell-auto-fs')
  clearSyncedFontSize(el)
  delete el.__autoShrinkFont
}

export const vAutoShrinkFont = {
  mounted: bind,
  updated: bind,
  unmounted: unbind,
}

export default vAutoShrinkFont
