const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const { parsePromptCountryFromEngine, formatRecognitionEngine, resolveProgressEngine } = require('../engineLabel')

describe('parsePromptCountryFromEngine', () => {
  it('parses mimo and deepseek tags', () => {
    assert.equal(parsePromptCountryFromEngine('mimo:FR', 'DE'), 'FR')
    assert.equal(parsePromptCountryFromEngine('deepseek:NL', 'DE'), 'NL')
    assert.equal(parsePromptCountryFromEngine('deepseek', 'FR'), 'FR')
    assert.equal(parsePromptCountryFromEngine('', 'FR'), 'FR')
  })
})

describe('formatRecognitionEngine', () => {
  it('does not default empty engine to MiMo', () => {
    assert.equal(formatRecognitionEngine('', 'FR'), '')
    assert.equal(formatRecognitionEngine(null, 'FR'), '')
  })

  it('labels deepseek tags', () => {
    const label = formatRecognitionEngine('deepseek:FR', 'FR')
    assert.match(label, /DeepSeek/)
    assert.match(label, /FR/)
  })
})

describe('resolveProgressEngine', () => {
  it('prefers engine tag from aiRawOutput', () => {
    assert.equal(resolveProgressEngine('deepseek:FR', 'mimo:FR'), 'deepseek:FR')
  })

  it('falls back to preferredEngine when aiRawOutput is AI text', () => {
    const aiText = '[{"NO":"1","NOM_PRENOM":"张三"}]'
    assert.equal(resolveProgressEngine(aiText, 'deepseek:FR'), 'deepseek:FR')
  })

  it('returns empty when neither is an engine tag', () => {
    assert.equal(resolveProgressEngine('', ''), '')
  })
})
