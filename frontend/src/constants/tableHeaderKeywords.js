/** 考勤表常见表头词（多国家/语言），用于自动扶正打分 */
export const TABLE_HEADER_KEYWORDS = [
  'NOM',
  'PRENOM',
  'DATE',
  'ARRIVEE',
  'DEPART',
  'PAGE',
  'HEURE',
  'HORAIRES',
  'TRAVAIL',
  'TRAVAILLEE',
  'AGENCE',
  'INTERIMAIRE',
  'INTERIM',
  'SIGNATURE',
  'ENTREPOT',
  'PAUSE',
  'PAYS',
  'SORTIE',
  'TOTAL',
  'EMPLOYE',
  'MATRICULE',
  'FEUILLE',
  'PRESENCE',
  'DUREE',
  'HEBDOMADAIRE',
  'SEMAINE',
  'NAME',
  'ARRIVAL',
  'DEPARTURE',
  'SHIFT',
  'WAREHOUSE',
  'AGENCY',
]

export const TABLE_HEADER_REGEXES = [
  /\bNO\b/,
  /\bN[°º]?\b/,
]

export function countHeaderKeywordHits(text) {
  if (!text) return 0
  const upper = String(text).toUpperCase().replace(/\s+/g, ' ')
  let hits = 0
  for (const keyword of TABLE_HEADER_KEYWORDS) {
    if (upper.includes(keyword)) {
      hits += 1
    }
  }
  for (const pattern of TABLE_HEADER_REGEXES) {
    if (pattern.test(upper)) {
      hits += 1
    }
  }
  return hits
}
