package com.udyam.digital.order;

import com.udyam.digital.i18n.I18n;
import com.udyam.digital.model.Language;

import java.util.Locale;

/** Technician visit windows: three a day, Monday to Saturday. Labels: "visit_morning" etc. in strings_order.xml. */
public enum VisitSlot {
    MORNING, AFTERNOON, EVENING;

    public String label(Language l) { return I18n.get(l, "visit_" + name().toLowerCase(Locale.ROOT)); }
}
