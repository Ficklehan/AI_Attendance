/** @deprecated use recognizedTextNormalizer.cjs — kept for miniprogram sync compatibility */
const { normalizeWorkerNo, isWorkerNoExtractable } = require('./recognizedTextNormalizer.cjs')

module.exports = {
  normalizeWorkerNo,
  isWorkerNoExtractable,
}
