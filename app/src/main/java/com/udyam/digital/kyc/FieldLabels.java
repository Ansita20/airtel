package com.udyam.digital.kyc;

import com.udyam.digital.i18n.I18n;
import com.udyam.digital.model.Language;

/** Plain names for extracted fields ("field_bill_date" in res/values-<lang>/strings_kyc.xml). */
public final class FieldLabels {
    private FieldLabels() {}

    public static I18n.Msg text(String field) {
        return I18n.has("field_" + field) ? I18n.msg("field_" + field) : I18n.msg("field_name");
    }

    public static String of(String field, Language l) { return text(field).in(l); }
}
