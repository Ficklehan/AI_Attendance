package com.attendance.recognition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessColumnCatalogTest {

    @Test
    void columnsForMapsFrenchAliases() {
        Map<String, List<String>> cols = HarnessColumnCatalog.columnsFor("FR");
        assertEquals(9, cols.size());
        assertTrue(cols.get("ARRIVEE").contains("Arrivée"));
        assertTrue(cols.get("DEPAR").contains("Départ"));
        assertTrue(cols.get("NAME").contains("NOM_PRENOM"));
        assertTrue(cols.get("NO").contains("N°"));
    }

    @Test
    void columnsForMapsItalianClockAliases() {
        Map<String, List<String>> cols = HarnessColumnCatalog.columnsFor("IT");
        assertTrue(cols.get("ARRIVEE").contains("Arrivo"));
        assertTrue(cols.get("DEPAR").contains("Uscita"));
    }

    @Test
    void promptBlockIncludesCountryAndFields() {
        String block = HarnessColumnCatalog.promptBlock("IT");
        assertTrue(block.contains("Country code: IT"));
        assertTrue(block.contains("field ARRIVEE"));
        assertTrue(block.contains("Arrivo"));
    }
}
