function formatRecognitionEngine(engine, promptCountry) {
  if (!engine) {
    return promptCountry && promptCountry !== 'default'
      ? `MiMo（${promptCountry}）`
      : 'MiMo 大模型'
  }
  if (engine === 'simulated') {
    return '模拟数据（非真实 AI，请关闭 ALLOW_SIMULATED_RECOGNITION）'
  }
  if (engine.indexOf('mimo:') === 0) {
    const cc = engine.slice(5) || promptCountry || 'default'
    return cc === 'default' ? 'MiMo（全局 default 提示词）' : `MiMo（${cc} 国家提示词）`
  }
  if (engine === 'mimo') {
    return promptCountry && promptCountry !== 'default'
      ? `MiMo（${promptCountry} 国家提示词）`
      : 'MiMo（全局 default 提示词）'
  }
  if (engine.length > 80 || engine.indexOf('[') >= 0 || engine.indexOf('张三') >= 0) {
    return 'MiMo 大模型'
  }
  return engine
}

module.exports = { formatRecognitionEngine }
