export const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '简体中文', flag: '🇨🇳' },
  { value: 'en-US', label: 'English', flag: '🇺🇸' },
  { value: 'fr-FR', label: 'Français', flag: '🇫🇷' },
  { value: 'nl-NL', label: 'Nederlands', flag: '🇳🇱' },
  { value: 'cs-CZ', label: 'Čeština', flag: '🇨🇿' },
  { value: 'pl-PL', label: 'Polski', flag: '🇵🇱' },
  { value: 'de-DE', label: 'Deutsch', flag: '🇩🇪' },
  { value: 'es-ES', label: 'Español', flag: '🇪🇸' },
]

export const SUPPORTED_LOCALES = LANGUAGE_OPTIONS.map((item) => item.value)

export function buildLanguageSelectOptions() {
  return LANGUAGE_OPTIONS.map((item) => ({
    value: item.value,
    label: `${item.flag} ${item.label}`,
  }))
}
