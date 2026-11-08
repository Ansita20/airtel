package com.udyam.digital.kyc;

import com.udyam.digital.i18n.I18n;
import com.udyam.digital.model.Language;

/**
 * One cross-check: code (C01-C20), result, how sure we are, whether the owner can fix it, and
 * plain-language text.
 */
public final class CheckResult {

    public enum Status { PASS, WARN, FAIL }

    /** Where "Fix this" should take the owner. */
    public enum FixAt { NONE, LOOKUP, UPLOAD, CONFIRM }

    public final String code;
    public final Status status;
    /** Can the owner resolve it themselves (rescan, re-enter, confirm)? If not: manual review. */
    public final boolean fixable;
    public final FixAt fixAt;
    /** Slot to re-upload, for FixAt.UPLOAD. May be null. */
    public final Slot slot;
    /** The message, translated when shown (res/values-<lang>/strings_kyc.xml). */
    public final I18n.Msg msg;
    /** English and Hindi text of {@link #msg}, for reports, logs and tests. */
    public final String en, hi;
    /** How sure the check is: the lowest OCR confidence of the document fields it used (1 = registry only). */
    public final float confidence;
    /**
     * True when this would have been a FAIL, but the field it rests on was read with low
     * confidence and nobody confirmed it: we ask for a rescan instead of rejecting.
     */
    public final boolean rescan;

    public CheckResult(String code, Status status, boolean fixable, FixAt fixAt, Slot slot, I18n.Msg msg) {
        this(code, status, fixable, fixAt, slot, msg, 1f, false);
    }

    public CheckResult(String code, Status status, boolean fixable, FixAt fixAt, Slot slot, I18n.Msg msg,
                       float confidence, boolean rescan) {
        this.code = code;
        this.status = status;
        this.fixable = fixable;
        this.fixAt = fixAt;
        this.slot = slot;
        this.msg = msg;
        this.en = msg.in(Language.EN);
        this.hi = msg.in(Language.HI);
        this.confidence = confidence;
        this.rescan = rescan;
    }

    public String message(Language l) { return msg.in(l); }

    /** Key used to remember a confirmed warning. */
    public String key() { return code + (slot == null ? "" : ":" + slot.name()); }

    @Override public String toString() {
        return code + " " + status + (rescan ? " (rescan)" : "") + (fixable ? "" : " (not fixable)") + ": " + en;
    }
}
