/** @see shared/js/recordFieldFormatRules.cjs */
const { markContains } = require('./recognitionLabels')
const { isPlaceholderValue } = require('./fieldPlaceholder')
const shared = require('../shared-js/recordFieldFormatRules')
const api = shared.createRecordFieldFormatRules({ isPlaceholderValue, markContains })

module.exports = {
  ...api,
}
