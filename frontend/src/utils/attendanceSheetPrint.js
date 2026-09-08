/** 每日签到表打印：日期格式、纸面语言、仓库最近使用 */

export const SHEET_BLANK_ROWS = 15
export const RECENT_WAREHOUSE_KEY = 'attendance.signInSheet.recentWarehouses'
export const LAST_WAREHOUSE_KEY = 'attendance.signInSheet.lastWarehouse'
const MAX_RECENT_WAREHOUSES = 12

const SHEET_COPY = {
  'zh-CN': {
    punchZone: '（打孔区 — 请勿书写）',
    title: '签 到 表',
    declarationTitle: '员工诚信声明与证件合规申明',
    declaration: [
      '1. 本人确认所填写的出勤时间真实、准确、有效，并确认无误。',
      '2. 本人确认公司已依法检查并核实本人有效居留证件（居留许可/护照/工作许可等）。',
      '3. 本人确认拥有合法工作权利，并承诺所提供信息真实有效，如有虚假愿承担相应责任。',
    ],
    country: '国家：',
    warehouse: '仓库：',
    date: '日期：',
    supervisorSign: '仓库主管确认签字：_________________________________',
    supervisorPage: '第 ___ 页',
    supervisorDate: '签字日期：____年____月____日',
    columns: ['序号', '姓名', '供应商名称', '班次', '签到时间', '签退时间', '休息 时长 (min)', '员工签名', '备注'],
  },
  'en-US': {
    punchZone: '(Punch zone — do not write)',
    title: 'SIGN-IN SHEET',
    declarationTitle: 'Employee integrity and document compliance declaration',
    declaration: [
      '1. I confirm that the working hours entered are true, accurate and valid.',
      '2. I confirm that the company has checked and verified my valid residence documents (residence permit / passport / work permit).',
      '3. I confirm that I have a legal right to work and that the information provided is true. I accept liability for any false statement.',
    ],
    country: 'Country:',
    warehouse: 'Warehouse:',
    date: 'Date:',
    supervisorSign: 'Warehouse supervisor signature: _________________________________',
    supervisorPage: 'Page ___',
    supervisorDate: 'Signature date: ____ / ____ / ________',
    columns: ['No.', 'Full name', 'Agency', 'Shift', 'Arrival', 'Departure', 'Break (min)', 'Signature', 'Remarks'],
  },
  'fr-FR': {
    punchZone: '(Zone de perforation — Ne pas écrire)',
    title: 'FEUILLE DE PRESENCE',
    declarationTitle: "DECLARATION D'INTEGRITE ET CONFORMITE DES DOCUMENTS",
    declaration: [
      '1. Je confirme que les horaires de travail renseignés sont réels, exacts et valides.',
      "2. Je confirme que l'entreprise a vérifié mon titre de séjour (titre de séjour/passeport/permis de travail).",
      '3. Je confirme détenir un droit de travail légal et que les informations fournies sont exactes.',
    ],
    country: 'Pays：',
    warehouse: 'Entrepôt：',
    date: 'Date：',
    supervisorSign: "Signature du responsable d'entrepôt ：_________________________________",
    supervisorPage: 'Page ___',
    supervisorDate: 'Date de signature : ____/____/________',
    columns: ['N°', 'Nom Prénom', 'Agence', 'Horaire', 'Arrivée', 'Départ', 'Pause (min)', 'Signature', 'Observations'],
  },
  'de-DE': {
    punchZone: '(Lochungsbereich — nicht beschreiben)',
    title: 'ANWESENHEITSLISTE',
    declarationTitle: 'Erklärung zur Integrität und Dokumentenkonformität',
    declaration: [
      '1. Ich bestätige, dass die angegebenen Arbeitszeiten wahr, genau und gültig sind.',
      '2. Ich bestätige, dass das Unternehmen meine gültigen Aufenthaltsdokumente (Aufenthaltstitel / Reisepass / Arbeitserlaubnis) geprüft hat.',
      '3. Ich bestätige, dass ich ein rechtmäßiges Arbeitsrecht habe und die Angaben wahr sind. Für falsche Angaben übernehme ich die Verantwortung.',
    ],
    country: 'Land:',
    warehouse: 'Lager:',
    date: 'Datum:',
    supervisorSign: 'Unterschrift Lagerleiter: _________________________________',
    supervisorPage: 'Seite ___',
    supervisorDate: 'Datum der Unterschrift: ____ / ____ / ________',
    columns: ['Nr.', 'Name', 'Agentur', 'Schicht', 'Ankunft', 'Abgang', 'Pause (Min.)', 'Unterschrift', 'Bemerkungen'],
  },
  'nl-NL': {
    punchZone: '(Perforatiezone — niet schrijven)',
    title: 'PRESENTIELIJST',
    declarationTitle: 'Verklaring van integriteit en documentconformiteit',
    declaration: [
      '1. Ik bevestig dat de ingevulde werktijden waarheidsgetrouw, nauwkeurig en geldig zijn.',
      '2. Ik bevestig dat het bedrijf mijn geldige verblijfsdocumenten (verblijfsvergunning / paspoort / werkvergunning) heeft gecontroleerd.',
      '3. Ik bevestig dat ik wettelijk mag werken en dat de verstrekte informatie juist is. Bij onjuiste gegevens aanvaard ik de verantwoordelijkheid.',
    ],
    country: 'Land:',
    warehouse: 'Magazijn:',
    date: 'Datum:',
    supervisorSign: 'Handtekening magazijnverantwoordelijke: _________________________________',
    supervisorPage: 'Pagina ___',
    supervisorDate: 'Datum handtekening: ____ / ____ / ________',
    columns: ['Nr.', 'Naam', 'Uitzendbureau', 'Dienst', 'Aankomst', 'Vertrek', 'Pauze (min)', 'Handtekening', 'Opmerkingen'],
  },
  'es-ES': {
    punchZone: '(Zona de perforación — no escribir)',
    title: 'HOJA DE ASISTENCIA',
    declarationTitle: 'Declaración de integridad y conformidad de documentos',
    declaration: [
      '1. Confirmo que los horarios indicados son reales, exactos y válidos.',
      '2. Confirmo que la empresa ha verificado mis documentos de residencia válidos (permiso de residencia / pasaporte / permiso de trabajo).',
      '3. Confirmo que tengo derecho legal a trabajar y que la información facilitada es veraz. Asumo la responsabilidad en caso de datos falsos.',
    ],
    country: 'País:',
    warehouse: 'Almacén:',
    date: 'Fecha:',
    supervisorSign: 'Firma del responsable de almacén: _________________________________',
    supervisorPage: 'Página ___',
    supervisorDate: 'Fecha de firma: ____ / ____ / ________',
    columns: ['N.º', 'Nombre y apellidos', 'Agencia', 'Turno', 'Entrada', 'Salida', 'Pausa (min)', 'Firma', 'Observaciones'],
  },
  'pl-PL': {
    punchZone: '(Strefa perforacji — nie pisać)',
    title: 'LISTA OBECNOŚCI',
    declarationTitle: 'Oświadczenie o rzetelności i zgodności dokumentów',
    declaration: [
      '1. Potwierdzam, że podane godziny pracy są prawdziwe, dokładne i ważne.',
      '2. Potwierdzam, że firma sprawdziła moje ważne dokumenty pobytowe (zezwolenie na pobyt / paszport / zezwolenie na pracę).',
      '3. Potwierdzam, że mam legalne prawo do pracy, a podane informacje są prawdziwe. Ponoszę odpowiedzialność za dane nieprawdziwe.',
    ],
    country: 'Kraj:',
    warehouse: 'Magazyn:',
    date: 'Data:',
    supervisorSign: 'Podpis kierownika magazynu: _________________________________',
    supervisorPage: 'Strona ___',
    supervisorDate: 'Data podpisu: ____ / ____ / ________',
    columns: ['Lp.', 'Imię i nazwisko', 'Agencja', 'Zmiana', 'Przybycie', 'Wyjście', 'Przerwa (min)', 'Podpis', 'Uwagi'],
  },
  'cs-CZ': {
    punchZone: '(Oblast perforace — nepsat)',
    title: 'PREZENČNÍ LIST',
    declarationTitle: 'Prohlášení o bezúhonnosti a shodě dokladů',
    declaration: [
      '1. Potvrzuji, že uvedené pracovní doby jsou pravdivé, přesné a platné.',
      '2. Potvrzuji, že společnost ověřila mé platné pobytové doklady (povolení k pobytu / pas / pracovní povolení).',
      '3. Potvrzuji, že mám zákonné právo pracovat a že uvedené informace jsou pravdivé. Za nepravdivé údaje nesu odpovědnost.',
    ],
    country: 'Země:',
    warehouse: 'Sklad:',
    date: 'Datum:',
    supervisorSign: 'Podpis vedoucího skladu: _________________________________',
    supervisorPage: 'Strana ___',
    supervisorDate: 'Datum podpisu: ____ / ____ / ________',
    columns: ['Č.', 'Jméno a příjmení', 'Agentura', 'Směna', 'Příchod', 'Odchod', 'Přestávka (min)', 'Podpis', 'Poznámky'],
  },
}

const LOCALE_PREFIX = {
  zh: 'zh-CN',
  en: 'en-US',
  fr: 'fr-FR',
  de: 'de-DE',
  nl: 'nl-NL',
  es: 'es-ES',
  pl: 'pl-PL',
  cs: 'cs-CZ',
}

export const SHEET_LOCALES = Object.keys(SHEET_COPY)

export function resolveSheetLocale(locale) {
  const raw = String(locale || '').trim()
  if (SHEET_COPY[raw]) return raw
  const prefix = raw.slice(0, 2).toLowerCase()
  return LOCALE_PREFIX[prefix] || 'zh-CN'
}

export function getSheetCopy(sheetLocale) {
  return SHEET_COPY[resolveSheetLocale(sheetLocale)] || SHEET_COPY['zh-CN']
}

export function isCjkSheet(sheetLocale) {
  return resolveSheetLocale(sheetLocale) === 'zh-CN'
}

export function todayIsoDate(now = new Date()) {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 纸面日期：MM/DD/YYYY，与识别页头规则一致 */
export function formatSheetDateMdY(iso) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(iso || '').trim())
  if (!match) return ''
  return `${match[2]}/${match[3]}/${match[1]}`
}

export function resolvePrintIsoDate(iso, dateTouched, now = new Date()) {
  if (dateTouched && /^\d{4}-\d{2}-\d{2}$/.test(String(iso || '').trim())) {
    return String(iso).trim()
  }
  return todayIsoDate(now)
}

export function loadRecentWarehouses() {
  try {
    const raw = localStorage.getItem(RECENT_WAREHOUSE_KEY)
    const list = raw ? JSON.parse(raw) : []
    if (!Array.isArray(list)) return []
    return list.map((item) => String(item || '').trim()).filter(Boolean)
  } catch {
    return []
  }
}

export function loadLastWarehouse() {
  try {
    return String(localStorage.getItem(LAST_WAREHOUSE_KEY) || '').trim()
  } catch {
    return ''
  }
}

export function rememberWarehouse(name) {
  const value = String(name || '').trim()
  if (!value) return
  try {
    localStorage.setItem(LAST_WAREHOUSE_KEY, value)
    const next = [value, ...loadRecentWarehouses().filter((item) => item !== value)].slice(0, MAX_RECENT_WAREHOUSES)
    localStorage.setItem(RECENT_WAREHOUSE_KEY, JSON.stringify(next))
  } catch {
    /* ignore quota / private mode */
  }
}

export function mergeWarehouseSuggestions(recent, scoped) {
  const seen = new Set()
  const out = []
  for (const raw of [...(recent || []), ...(scoped || [])]) {
    const value = String(raw || '').trim()
    if (!value) continue
    const key = value.toUpperCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(value)
  }
  return out
}

export const SHEET_EXCEL_TEMPLATE_FILE = 'GEU劳务工签到表模板_（更新版0907）.xlsx'

export function downloadSheetExcel() {
  const link = document.createElement('a')
  link.href = `${import.meta.env.BASE_URL}templates/geu-sign-in-sheet.xlsx`
  link.download = SHEET_EXCEL_TEMPLATE_FILE
  document.body.appendChild(link)
  link.click()
  link.remove()
}
