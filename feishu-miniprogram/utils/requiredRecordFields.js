/** @see shared/js/requiredRecordFields.cjs */
const { markContains } = require('./recognitionLabels')
const { isPlaceholderValue } = require('./fieldPlaceholder')
const { createRequiredRecordFields } = require('../shared-js/requiredRecordFields')

module.exports = createRequiredRecordFields({ isPlaceholderValue, markContains })
