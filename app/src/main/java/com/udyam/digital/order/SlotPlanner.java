package com.udyam.digital.order;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Technician slots: the next 7 days (from tomorrow), 3 a day, none on Sundays. Pure Java. */
public final class SlotPlanner {
    private SlotPlanner() {}

    public static final int DAYS_AHEAD = 7;

    public static final class Slot {
        public final LocalDate date;
        public final VisitSlot time;
        Slot(LocalDate date, VisitSlot time) { this.date = date; this.time = time; }
        @Override public String toString() { return date + " " + time; }
    }

    public static List<Slot> available(LocalDate today) {
        List<Slot> out = new ArrayList<>();
        for (int i = 1; i <= DAYS_AHEAD; i++) {
            LocalDate d = today.plusDays(i);
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            for (VisitSlot s : VisitSlot.values()) out.add(new Slot(d, s));
        }
        return out;
    }
}
