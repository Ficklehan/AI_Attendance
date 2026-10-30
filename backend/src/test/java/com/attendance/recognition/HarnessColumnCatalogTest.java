package com.attendance.recognition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarnessColumnCatalogTest {

    @Test
    void canonicalFieldMapsCountryAliases() {
        assertEquals("ARRIVEE", HarnessColumnCatalog.canonicalField("Arrivo"));
        assertEquals("DEPAR", HarnessColumnCatalog.canonicalField("Uscita"));
        assertEquals("ARRIVEE", HarnessColumnCatalog.canonicalField("Entrada"));
        assertEquals("DEPAR", HarnessColumnCatalog.canonicalField("Départ"));
        assertEquals("NAME", HarnessColumnCatalog.canonicalField("NOM_PRENOM"));
        assertEquals("NO", HarnessColumnCatalog.canonicalField("Nr"));
    }

    @Test
    void orderedFieldsAreNineLeftToRight() {
        assertEquals(9, HarnessColumnCatalog.orderedFields().size());
        assertEquals("NO", HarnessColumnCatalog.orderedFields().get(0));
        assertEquals("ARRIVEE", HarnessColumnCatalog.orderedFields().get(4));
        assertEquals("OBSERVATIONS", HarnessColumnCatalog.orderedFields().get(8));
    }
}
