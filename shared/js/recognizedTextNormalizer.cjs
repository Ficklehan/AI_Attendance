/**
 * 识别文本字段清洗：工号、姓名、仓库、中介机构。
 */

const BRACKET_QUOTE_RE = /[\[\]\{\}\"'【】「」]/g
const LABEL_PAREN_RE = /[()（）]/g

function collapseSpaces(text) {
  return String(text || '').replace(/\s+/g, ' ').trim()
}

/** 工号：仅保留数字与字母 */
function normalizeWorkerNo(raw) {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  const stripped = text.replace(BRACKET_QUOTE_RE, '').replace(/[^a-zA-Z0-9]/g, '')
  return stripped
}

function isWorkerNoExtractable(raw) {
  return normalizeWorkerNo(raw).length > 0
}

/** 姓名：保留有内容的括号；删除空括号 Jean() / Jean（） → Jean */
function normalizePersonName(raw) {
  let s = String(raw ?? '').trim()
  if (!s) return ''
  s = s.replace(BRACKET_QUOTE_RE, '')
  s = s.replace(/\(\s*\)/g, '')
  s = s.replace(/（\s*）/g, '')
  s = s.replace(/（\s*\)/g, '')
  s = s.replace(/\(\s*）/g, '')
  return collapseSpaces(s)
}

/** 仓库 / 中介：去除括号及引号类符号 */
function normalizeLabelText(raw) {
  let s = String(raw ?? '').trim()
  if (!s) return ''
  s = s.replace(BRACKET_QUOTE_RE, '').replace(LABEL_PAREN_RE, '')
  return collapseSpaces(s)
}

function normalizeRecordTextFields(record) {
  if (!record || typeof record !== 'object') return record
  if (record.NO !== undefined && record.NO !== null) {
    record.NO = normalizeWorkerNo(record.NO)
  }
  if (record.NOM_PRENOM !== undefined && record.NOM_PRENOM !== null) {
    record.NOM_PRENOM = normalizePersonName(record.NOM_PRENOM)
  }
  if (record.Name !== undefined && record.Name !== null) {
    record.Name = normalizePersonName(record.Name)
  }
  if (record.Entrepot !== undefined && record.Entrepot !== null) {
    record.Entrepot = normalizeLabelText(record.Entrepot)
  }
  if (record.AGENCE_INTERIMAIRE !== undefined && record.AGENCE_INTERIMAIRE !== null) {
    record.AGENCE_INTERIMAIRE = normalizeLabelText(record.AGENCE_INTERIMAIRE)
  }
  return record
}

module.exports = {
  normalizeWorkerNo,
  isWorkerNoExtractable,
  normalizePersonName,
  normalizeLabelText,
  normalizeRecordTextFields,
}
