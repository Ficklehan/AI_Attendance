package com.attendance.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfirmValidationConfigDTO {

    public static final String SCOPE_EXCEPT_DELETED_ABSENT = "except_deleted_absent";

    /** @deprecated 保留兼容；校验已不再限制「正常」标记 */
    public static final String SCOPE_MARK_CONTAINS_NORMAL = "mark_contains_normal";

    public static final List<String> ALL_FIELD_KEYS = Collections.unmodifiableList(Arrays.asList(
            "Pays",
            "Entrepot",
            "Date",
            "NOM_PRENOM",
            "AGENCE_INTERIMAIRE",
            "HORAIRES_DU_TRAVAIL",
            "ARRIVEE",
            "DEPAR",
            "PAUSE"
    ));

    private String scope = SCOPE_EXCEPT_DELETED_ABSENT;
    private List<String> requiredFields = new ArrayList<>(defaultRequiredFields());

    public static List<String> defaultRequiredFields() {
        return Arrays.asList("NOM_PRENOM", "Date", "ARRIVEE", "DEPAR", "PAUSE");
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }
}
