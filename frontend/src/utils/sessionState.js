let sessionValidated = false

export function isSessionValidated() {
  return sessionValidated
}

export function markSessionValidated() {
  sessionValidated = true
}

export function resetSessionValidation() {
  sessionValidated = false
}
