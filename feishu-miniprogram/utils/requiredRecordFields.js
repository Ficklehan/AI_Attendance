/** @see shared/js/requiredRecordFields.cjs */
const { markContains } = require('./recognitionLabels')
const { isPlaceholderValue } = require('./fieldPlaceholder')
const shared = require('../shared-js/requiredRecordFields')
const api = shared.createRequiredRecordFields({ isPlaceholderValue, markContains })

module.exports = {
  ...api,
}
