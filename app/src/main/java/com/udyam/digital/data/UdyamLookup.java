package com.udyam.digital.data;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Lookup against mock_udyam_registry.csv. Loaded now; used by the KYC screen later. */
public final class UdyamLookup {

    public static final class Record {
        public final String udyamNumber;
        public final String enterpriseName;
        public final String enterpriseType;
        public final String majorActivity;
        public final String nicDescription;
        public final String state;
        public final String city;
        public final String registrationDate;

        Record(Map<String, String> r) {
            udyamNumber = r.get("udyam_number");
            enterpriseName = r.get("enterprise_name");
            enterpriseType = r.get("enterprise_type");
            majorActivity = r.get("major_activity");
            nicDescription = r.get("nic_description");
            state = r.get("state");
            city = r.get("city");
            registrationDate = r.get("date_of_registration");
        }
    }

    private final Map<String, Record> byNumber;

    private UdyamLookup(Map<String, Record> byNumber) { this.byNumber = byNumber; }

    public static UdyamLookup load(Reader in) throws IOException {
        Map<String, Record> map = new HashMap<>();
        for (Map<String, String> r : CsvParser.readRecords(in)) {
            map.put(r.get("udyam_number").toUpperCase(Locale.ROOT), new Record(r));
        }
        return new UdyamLookup(map);
    }

    public int size() { return byNumber.size(); }

    /** Returns null when not found. */
    public Record find(String udyamNumber) {
        if (udyamNumber == null) return null;
        return byNumber.get(udyamNumber.trim().toUpperCase(Locale.ROOT));
    }
}
