package com.attendance.recognition;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 各国表头别名，交给 vision_ground，禁止写具体时刻或人名。
 */
public final class HarnessColumnCatalog {

    private static final Set<String> CANONICAL = new HashSet<String>(Arrays.asList(
            "NO", "NAME", "AGENCY", "SHIFT", "ARRIVEE", "DEPAR", "PAUSE", "SIGNATURE", "OBSERVATIONS"));
    private static final Map<String, String> FIELD_ALIAS = buildAliasMap();

    private HarnessColumnCatalog() {
    }

    public static List<String> orderedFields() {
        return new ArrayList<String>(Arrays.asList(
                "NO", "NAME", "AGENCY", "SHIFT", "ARRIVEE", "DEPAR", "PAUSE", "SIGNATURE", "OBSERVATIONS"));
    }

    public static String canonicalField(String raw) {
        if (raw == null) {
            return "";
        }
        String key = aliasKey(raw);
        if (key.isEmpty()) {
            return "";
        }
        if (CANONICAL.contains(key)) {
            return key;
        }
        String mapped = FIELD_ALIAS.get(key);
        return mapped != null ? mapped : key;
    }

    public static String promptBlock(String countryCode) {
        String code = countryCode == null ? "FR" : countryCode.trim().toUpperCase(Locale.ROOT);
        Map<String, List<String>> cols = columnsFor(code);
        StringBuilder sb = new StringBuilder();
        sb.append("Country code: ").append(code).append('\n');
        sb.append("Locate these table columns by header aliases (case-insensitive):\n");
        for (Map.Entry<String, List<String>> e : cols.entrySet()) {
            sb.append("- field ").append(e.getKey()).append(": ");
            List<String> names = e.getValue();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    sb.append(" / ");
                }
                sb.append(names.get(i));
            }
            sb.append('\n');
        }
        sb.append("Also locate page header warehouse and printed date if present.\n");
        return sb.toString();
    }

    static Map<String, List<String>> columnsFor(String code) {
        Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
        map.put("NO", aliases("NO", "N°", "Nr", "#"));
        map.put("NAME", aliases("NOM_PRENOM", "Nome", "Nom", "Name", "Apellidos", "Cognome"));
        map.put("AGENCY", aliases("AGENCE", "Agenzia", "Agencia", "Agentur", "Agency", "Uitzend"));
        map.put("SHIFT", aliases("HORAIRES", "Orario", "Horario", "Shift", "Dienst", "班次"));
        if ("IT".equals(code)) {
            map.put("ARRIVEE", aliases("Arrivo", "Entrata", "In"));
            map.put("DEPAR", aliases("Uscita", "Usc.", "Out"));
        } else if ("ES".equals(code)) {
            map.put("ARRIVEE", aliases("Entrada", "Llegada", "In"));
            map.put("DEPAR", aliases("Salida", "Out"));
        } else if ("DE".equals(code)) {
            map.put("ARRIVEE", aliases("Kommen", "Beginn", "Ankunft"));
            map.put("DEPAR", aliases("Gehen", "Ende", "Ausgang"));
        } else if ("NL".equals(code)) {
            map.put("ARRIVEE", aliases("Aankomst", "In", "Begin"));
            map.put("DEPAR", aliases("Vertrek", "Uit", "Einde"));
        } else if ("PL".equals(code)) {
            map.put("ARRIVEE", aliases("Przyjście", "Wejście", "In"));
            map.put("DEPAR", aliases("Wyjście", "Out"));
        } else if ("CZ".equals(code)) {
            map.put("ARRIVEE", aliases("Příchod", "In"));
            map.put("DEPAR", aliases("Odchod", "Out"));
        } else {
            map.put("ARRIVEE", aliases("Arrivée", "Arrivee", "Arrival", "In", "Entrée"));
            map.put("DEPAR", aliases("Départ", "Depart", "Sortie", "Out", "Leave"));
        }
        map.put("PAUSE", aliases("Pause", "Pausa", "Break", "Pauza", "休息"));
        map.put("SIGNATURE", aliases("Signature", "Firma", "Signatura", "Handtekening", "Unterschrift", "签字"));
        map.put("OBSERVATIONS", aliases("Observations", "Note", "Remarques", "Osservazioni", "Observaciones", "备注"));
        return map;
    }

    static String aliasKey(String raw) {
        if (raw == null) {
            return "";
        }
        String n = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
        return n;
    }

    private static Map<String, String> buildAliasMap() {
        Map<String, String> map = new HashMap<String, String>();
        String[] codes = new String[] {"FR", "IT", "ES", "DE", "NL", "PL", "CZ"};
        for (int i = 0; i < codes.length; i++) {
            Map<String, List<String>> cols = columnsFor(codes[i]);
            for (Map.Entry<String, List<String>> e : cols.entrySet()) {
                map.put(aliasKey(e.getKey()), e.getKey());
                List<String> names = e.getValue();
                for (int j = 0; j < names.size(); j++) {
                    String k = aliasKey(names.get(j));
                    if (k.length() > 1) {
                        map.put(k, e.getKey());
                    }
                }
            }
        }
        map.put("ARRIVO", "ARRIVEE");
        map.put("USCITA", "DEPAR");
        map.put("USC", "DEPAR");
        map.put("NOMPRENOM", "NAME");
        map.put("NR", "NO");
        return map;
    }

    private static List<String> aliases(String... names) {
        List<String> list = new ArrayList<String>();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                list.add(names[i]);
            }
        }
        return list;
    }
}
