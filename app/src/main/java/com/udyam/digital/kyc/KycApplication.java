package com.udyam.digital.kyc;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Everything the KYC flow collects for one application. Persisted encrypted by KycStore. */
public final class KycApplication {

    public enum Outcome { IN_PROGRESS, APPROVED, PLEASE_FIX, MANUAL_REVIEW }

    public final String id;
    public int step = 1;
    public boolean kycConsent;
    public boolean marketingConsent;
    public String consentTimestamp;
    public String noticeVersion;

    public String gstin = "";
    public boolean noGstin;
    public String udyam = "";
    /** Optional: where the connection will be installed, if different from the GST address. */
    public String installationAddress = "";

    public final Map<Slot, KycDocument> documents = new EnumMap<>(Slot.class);
    /** WARN checks the owner confirmed ("Yes, this is correct"). */
    public final Set<String> confirmedWarnings = new HashSet<>();

    public Outcome outcome = Outcome.IN_PROGRESS;
    public String outcomeReason = "";
    /** Date (KycClock) the application was decided; drives retention. */
    public String decidedOn;
    /** Date of the last change; never-submitted applications are deleted 90 days after it. */
    public String updatedOn;
    public String ticketId;

    public KycApplication(String id) { this.id = id; }

    public KycDocument doc(Slot s) { return documents.get(s); }

    /** Slots this application needs. Without a GSTIN there is no GST certificate. */
    public Slot[] requiredSlots() {
        return noGstin ? new Slot[] {Slot.UDYAM_CERT, Slot.SIGNATORY_ID, Slot.ADDRESS_PROOF} : Slot.values();
    }
}
