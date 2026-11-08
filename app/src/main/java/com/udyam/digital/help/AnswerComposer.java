package com.udyam.digital.help;

import com.udyam.digital.help.core.BusinessProfile;
import com.udyam.digital.help.core.GstinLookup;
import com.udyam.digital.help.core.Product;
import com.udyam.digital.help.core.Recommendation;
import com.udyam.digital.help.core.RulesEngine;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1) the matched KB entry, 2) filled with this user's real data (products, prices, install
 * days, KYC failure reason) and with RulesEngine results for "why can't I get X" and "make
 * it cheaper", 3) optionally rewritten by the LLM (see {@link #polish}).
 * Prices and eligibility always come from RulesEngine, never from the bot.
 */
public final class AnswerComposer {

    public static final class Composed {
        public final String text;
        public final List<String> quickReplies;
        public final String action;
        /** A changed bundle the user can accept (cheaper plan, new quantity). */
        public final Recommendation proposal;
        /** What the bot did, for the agent summary ("Re-ran plan within Rs 4,000"). */
        public final String botAction;

        Composed(String text, List<String> quickReplies, String action, Recommendation proposal, String botAction) {
            this.text = text;
            this.quickReplies = quickReplies;
            this.action = action;
            this.proposal = proposal;
            this.botAction = botAction;
        }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)\\}");
    private static final DateTimeFormatter DATE_EN = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final KnowledgeBase kb;
    private final RulesEngine engine;
    private final IntentMatcher matcher;
    private final GstinLookup gstinLookup; // may be null

    public AnswerComposer(KnowledgeBase kb, RulesEngine engine, IntentMatcher matcher, GstinLookup gstinLookup) {
        this.kb = kb;
        this.engine = engine;
        this.matcher = matcher;
        this.gstinLookup = gstinLookup;
    }

    public static String rs(int amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(amount);
    }

    public Composed compose(KbEntry e, HelpContext ctx, String message, Lang lang) {
        return compose(e, ctx, message, lang, false);
    }

    /** followUp = the user said the previous answer for this same problem didn't work. */
    public Composed compose(KbEntry e, HelpContext ctx, String message, Lang lang, boolean followUp) {
        Map<String, String> v = new LinkedHashMap<>();
        Composed c = composeFirst(e, ctx, message, lang, v);
        if (!followUp) return c;
        String fu = e.followupAnswer(lang);
        String text = fu != null ? fill(fu, v) : kb.phrase("followup_sorry", lang) + " " + c.text;
        return new Composed(text, c.quickReplies, c.action, c.proposal, c.botAction + " (again)");
    }

    private Composed composeFirst(KbEntry e, HelpContext ctx, String message, Lang lang, Map<String, String> v) {
        common(v, ctx, lang);
        Recommendation proposal = null;
        String botAction = e.title;
        BusinessProfile b = ctx.profile;
        Recommendation rec = ctx.recommendation;

        switch (e.id) {
            case "product_not_shown":
            case "want_1gbps":
            case "why_no_leased_line":
            case "addon_without_broadband": {
                String mentioned = ProductMentions.find(message);
                String pid;
                switch (e.id) {
                    case "want_1gbps":
                        pid = "P03";
                        break;
                    case "why_no_leased_line":
                        pid = "P06".equals(mentioned) ? "P06" : "P05";
                        break;
                    case "addon_without_broadband":
                        pid = mentioned != null && (mentioned.equals("P22") || mentioned.equals("P09") || mentioned.equals("P11"))
                                ? mentioned : "P23";
                        break;
                    default:
                        pid = mentioned != null ? mentioned : "P15";
                }
                Product p = engine.catalogue().get(pid);
                v.put("product_name", p.name);
                v.put("product_price", rs(p.monthlyPrice()));
                List<RulesEngine.Reason> why = b == null ? new ArrayList<RulesEngine.Reason>() : engine.eligibility(p, b, rec);
                if (why.isEmpty()) {
                    // Eligible: the bot can simply offer it.
                    return finish(kb.phrase("product_is_available", lang), v, e, lang, null, "Confirmed " + p.name + " is available");
                }
                v.put("reasons", reasons(why, p, b, lang));
                v.put("alternative", alternative(p, b, rec, lang));
                botAction = "Explained why " + p.name + " is not available (" + why + ")";
                break;
            }
            case "fibre_not_available": {
                Product af = engine.catalogue().get("P04");
                v.put("airfiber_price", rs(af.monthlyPrice()));
                v.put("airfiber_days", String.valueOf(af.installDays));
                if (b != null && rec != null) {
                    BusinessProfile nb = b.copy();
                    nb.fibreAvailable = false;
                    proposal = engine.recommend(nb);
                    v.put("new_total", rs(proposal.monthlyTotal()));
                }
                botAction = "Offered 5G AirFiber (P04)";
                break;
            }
            case "too_expensive": {
                if (rec != null && b != null) {
                    int old = rec.monthlyTotal();
                    int target = (int) Math.floor(old * 0.8);
                    if (b.monthlyBudget > 0) target = Math.min(target, b.monthlyBudget);
                    proposal = engine.rerunWithBudget(b, rec, target);
                    v.put("old_total", rs(old));
                    v.put("new_total", rs(proposal.monthlyTotal()));
                    v.put("target", rs(target));
                    String diff = describeChanges(rec, proposal, lang);
                    v.put("changes", diff.isEmpty() ? kb.phrase("no_changes_possible", lang) : diff);
                    botAction = "Re-ran plan within Rs " + rs(target) + ": Rs " + rs(old) + " -> Rs " + rs(proposal.monthlyTotal());
                }
                break;
            }
            case "mobile_only": {
                Product p7 = engine.catalogue().get("P07"), p8 = engine.catalogue().get("P08");
                v.put("sim_options", p7.name + " (Rs " + rs(p7.monthlyPrice()) + "), " + p8.name + " (Rs " + rs(p8.monthlyPrice()) + ")");
                break;
            }
            case "explain_terms": {
                String term = matcher.findTerm(message);
                if (term == null) term = "mbps";
                Map<String, Object> g = kb.glossary().get(term);
                v.put("term", MiniJson.str(g.get("label"), term));
                v.put("term_explanation", MiniJson.str(g.get(lang.code), MiniJson.str(g.get("en"), "")));
                botAction = "Explained term: " + term;
                break;
            }
            case "multi_site_states": {
                int sites = b == null ? 1 : b.sites;
                v.put("sites", String.valueOf(sites));
                v.put("states", b == null || b.siteStates.isEmpty() ? "-" : String.join(", ", b.siteStates));
                v.put("sdwan_line", b != null && engine.eligibility("P10", b, rec).isEmpty()
                        ? kb.phrase("sdwan_eligible", lang) : kb.phrase("sdwan_not_eligible", lang));
                v.put("gst_note", b != null && b.siteStates.size() > 1 ? kb.phrase("note_gst_per_state", lang) : "");
                break;
            }
            case "quote_confusing": {
                v.put("line_items", lineItems(rec, lang));
                break;
            }
            case "contract_cancel_terms": {
                List<String> parts = new ArrayList<>();
                if (rec != null) for (Recommendation.Line l : rec.lines()) parts.add(l.product.name + ": " + l.product.minContractMonths);
                v.put("contract_list", parts.isEmpty() ? "-" : String.join("; ", parts));
                break;
            }
            case "no_gstin": {
                List<String> names = new ArrayList<>();
                for (Product p : engine.catalogue().gstinOnly()) names.add(p.name);
                v.put("gstin_products", String.join(", ", names));
                if (rec != null) {
                    proposal = engine.withoutGstinProducts(rec);
                    v.put("total_without_gstin", rs(proposal.monthlyTotal()));
                } else {
                    v.put("total_without_gstin", "-");
                }
                break;
            }
            case "gstin_cancelled": {
                v.put("gstin_status", b == null ? "" : statusWord(b.gstinStatus, lang));
                if (rec != null) {
                    proposal = engine.withoutGstinProducts(rec);
                    v.put("total_without_gstin", rs(proposal.monthlyTotal()));
                    String diff = describeChanges(rec, proposal, lang);
                    v.put("changes", diff.isEmpty() ? kb.phrase("no_changes_needed", lang) : diff);
                }
                break;
            }
            case "gstin_invalid": {
                String candidate = findGstinLike(message);
                GstinLookup.Format f = GstinLookup.checkFormat(candidate);
                String problem;
                if (f == GstinLookup.Format.VALID) {
                    GstinLookup.Record r = gstinLookup == null ? null : gstinLookup.lookup(candidate);
                    problem = r == null ? kb.phrase("fmt_NOT_IN_REGISTRY", lang) : kb.phrase("fmt_FOUND", lang)
                            .replace("{status}", statusWord(r.status, lang));
                } else {
                    problem = kb.phrase("fmt_" + f.name(), lang).replace("{len}", String.valueOf(GstinLookup.normalise(candidate).length()));
                }
                v.put("format_problem", problem);
                botAction = "Checked GSTIN format: " + f;
                break;
            }
            case "upgrade_speed": {
                List<String> opts = new ArrayList<>();
                if (b != null && rec != null) for (Product p : engine.upgrades(b, rec)) opts.add(p.name + " (Rs " + rs(p.monthlyPrice()) + ")");
                v.put("upgrade_options", opts.isEmpty() ? kb.phrase("no_upgrade", lang) : String.join(", ", opts));
                break;
            }
            case "change_quantity": {
                String pid = ProductMentions.find(message);
                // "SIM" means whichever SIM plan the bundle already has.
                if ("P07".equals(pid) && rec != null && !rec.contains("P07") && rec.contains("P08")) pid = "P08";
                int qty = ProductMentions.quantity(message);
                boolean hasQty = qty > 0;
                // "add 2 SIMs" = current + 2; "remove 1 camera" = current - 1.
                String low = message == null ? "" : message.toLowerCase(Locale.ROOT);
                if (pid != null && qty > 0 && rec != null && rec.line(pid) != null) {
                    int cur = rec.line(pid).quantity;
                    if (low.contains("add") || low.contains("more") || low.contains("extra") || low.contains("aur")) qty = cur + qty;
                    else if (low.contains("remove") || low.contains("less") || low.contains("hatao") || low.contains("kam")) qty = cur - qty;
                }
                if (pid != null && hasQty && rec != null && b != null) {
                    qty = Math.max(0, qty);
                    proposal = engine.withQuantity(b, rec, pid, qty);
                    v.put("product_name", engine.catalogue().get(pid).name);
                    v.put("qty", String.valueOf(qty));
                    v.put("new_total", rs(proposal.monthlyTotal()));
                    return finish(kb.phrase("quantity_changed", lang), v, e, lang, proposal, "Changed " + pid + " to " + qty);
                }
                break;
            }
            case "internet_down": {
                Product fo = engine.catalogue().get("P09");
                boolean hasBase = rec != null && rec.base() != null;
                v.put("failover_tip", hasBase && !rec.contains("P09")
                        ? kb.phrase("failover_tip", lang).replace("{price}", rs(fo.monthlyPrice())) : "");
                botAction = "Gave router troubleshooting steps";
                break;
            }
            case "competitor_cheaper": {
                v.put("line_items", lineItems(rec, lang));
                break;
            }
            default:
                break;
        }
        return finish(e.answer(lang), v, e, lang, proposal, botAction);
    }

    private Composed finish(String template, Map<String, String> v, KbEntry e, Lang lang, Recommendation proposal, String botAction) {
        return new Composed(fill(template, v), e.quickReplies(lang), e.action, proposal, botAction);
    }

    private void common(Map<String, String> v, HelpContext ctx, Lang lang) {
        Recommendation rec = ctx.recommendation;
        BusinessProfile b = ctx.profile;
        v.put("monthly_total", rec == null ? "-" : rs(rec.monthlyTotal()));
        v.put("setup_total", rec == null ? "-" : rs(rec.setupTotal()));
        v.put("contract_months", rec == null ? "-" : String.valueOf(rec.contractMonths()));
        v.put("install_days", rec == null ? "-" : String.valueOf(rec.maxInstallDays()));
        Recommendation.Line slow = rec == null ? null : rec.slowestLine();
        v.put("slowest_product", slow == null ? "-" : slow.product.name);
        v.put("expected_date", ctx.expectedDate == null ? "-" : date(ctx.expectedDate, lang));
        v.put("days_late", String.valueOf(ctx.daysLate()));
        v.put("days_since_order", String.valueOf(ctx.daysSinceOrder()));
        v.put("order_id", ctx.orderId == null || ctx.orderId.isEmpty() ? "-" : ctx.orderId);
        v.put("current_step", ctx.currentStepLabel == null || ctx.currentStepLabel.isEmpty() ? "-" : ctx.currentStepLabel);
        v.put("fibre_date", ctx.connectionReady == null ? (ctx.expectedDate == null ? "-" : date(ctx.expectedDate, lang)) : date(ctx.connectionReady, lang));
        v.put("visit_slot", ctx.visitSlot == null || ctx.visitSlot.isEmpty() ? "-" : ctx.visitSlot);
        v.put("gstin_masked", ctx.maskedGstin() == null ? "-" : ctx.maskedGstin());
        v.put("docs_done", String.valueOf(ctx.docsDone));
        v.put("docs_total", String.valueOf(ctx.docsTotal));
        v.put("docs_left", String.valueOf(Math.max(0, ctx.docsTotal - ctx.docsDone)));
        v.put("mismatch_field", ctx.mismatchField == null || ctx.mismatchField.isEmpty() ? kb.phrase("mismatch_field_default", lang) : ctx.mismatchField);
        v.put("address_cutoff", date(ctx.today.minusDays(90), lang));
        v.put("city", b == null ? "" : b.city);
        v.put("product_name", "");
        v.put("reasons", "");
        v.put("alternative", "");
    }

    private String date(java.time.LocalDate d, Lang lang) {
        return d.format(DATE_EN);
    }

    private String lineItems(Recommendation rec, Lang lang) {
        if (rec == null || rec.lines().isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (Recommendation.Line l : rec.lines()) {
            sb.append("\n• ").append(l.quantity).append(" × ").append(l.product.name)
                    .append(": Rs ").append(rs(l.monthly())).append(kb.phrase("per_month", lang));
            if (l.setup() > 0) sb.append(" + Rs ").append(rs(l.setup())).append(kb.phrase("one_time", lang));
            sb.append(" (").append(l.product.minContractMonths).append(kb.phrase("months_contract", lang)).append(")");
        }
        return sb.toString();
    }

    /** Localised list of what differs between two bundles: switched, removed, fewer. */
    String describeChanges(Recommendation before, Recommendation after, Lang lang) {
        List<String> parts = new ArrayList<>();
        Recommendation.Line b0 = before.base(), b1 = after.base();
        if (b0 != null && b1 != null && !b0.product.id.equals(b1.product.id)) {
            parts.add(kb.phrase("change_switched", lang).replace("{from}", b0.product.name).replace("{to}", b1.product.name));
        }
        for (Recommendation.Line l : before.lines()) {
            if (l.product.isConnectivityBase()) continue;
            Recommendation.Line n = after.line(l.product.id);
            if (n == null) {
                parts.add(kb.phrase("change_removed", lang).replace("{qty}", String.valueOf(l.quantity)).replace("{name}", l.product.name));
            } else if (n.quantity < l.quantity) {
                parts.add(kb.phrase("change_fewer", lang).replace("{name}", l.product.name)
                        .replace("{from}", String.valueOf(l.quantity)).replace("{to}", String.valueOf(n.quantity)));
            }
        }
        return join(parts, lang);
    }

    String reasons(List<RulesEngine.Reason> why, Product p, BusinessProfile b, Lang lang) {
        List<String> parts = new ArrayList<>();
        for (RulesEngine.Reason r : why) {
            String t = kb.phrase("reason_" + r.code.name(), lang);
            t = t.replace("{n}", String.valueOf(r.value))
                    .replace("{tiers}", joinInts(p.cityTiers))
                    .replace("{tier}", String.valueOf(b.cityTier()))
                    .replace("{city}", b.city)
                    .replace("{have_employees}", String.valueOf(b.employees))
                    .replace("{have_sites}", String.valueOf(b.sites))
                    .replace("{status}", r.status == null ? "" : statusWord(BusinessProfile.GstinStatus.valueOf(r.status), lang))
                    .replace("{products}", productNames(r.productIds));
            parts.add(t);
        }
        return join(parts, lang);
    }

    /** The nearest product this profile CAN get instead. */
    String alternative(Product wanted, BusinessProfile b, Recommendation rec, Lang lang) {
        if (b == null) return "";
        List<String> candidates = new ArrayList<>();
        switch (wanted.id) {
            case "P03": candidates.add("P02"); if (b.employees >= 10) candidates.add("P05"); break;
            case "P06": candidates.add("P05"); candidates.add("P03"); candidates.add("P02"); break;
            case "P05": candidates.add("P03"); candidates.add("P02"); break;
            case "P10": candidates.add("P09"); break;
            case "P15": return kb.phrase("alt_whatsapp_no_gstin", lang);
            case "P14": return kb.phrase("alt_gstin_later", lang);
            case "P12": candidates.add("P08"); break;
            case "P21": candidates.add("P20"); break;
            case "P22": case "P23": case "P09": case "P11":
                // Needs broadband: the cheapest eligible base makes it possible.
                for (String base : new String[]{"P04", "P01", "P02"}) {
                    if (engine.isEligible(base, b, rec)) {
                        Product bp = engine.catalogue().get(base);
                        return kb.phrase("alt_add_base", lang).replace("{alt}", bp.name)
                                .replace("{price}", rs(bp.monthlyPrice())).replace("{product_name}", wanted.name);
                    }
                }
                break;
            default: break;
        }
        for (String id : candidates) {
            if (engine.isEligible(id, b, rec)) {
                Product a = engine.catalogue().get(id);
                return kb.phrase("alt_offer", lang).replace("{alt}", a.name).replace("{price}", rs(a.monthlyPrice()))
                        .replace("{days}", String.valueOf(a.installDays));
            }
        }
        return kb.phrase("alt_none", lang);
    }

    private String productNames(List<String> ids) {
        List<String> n = new ArrayList<>();
        for (String id : ids) if (engine.catalogue().has(id)) n.add(engine.catalogue().get(id).name);
        return String.join(" / ", n);
    }

    private String statusWord(BusinessProfile.GstinStatus s, Lang lang) {
        return kb.phrase("status_" + s.name(), lang);
    }

    private String join(List<String> parts, Lang lang) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        String and = kb.phrase("and", lang);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + and + parts.get(parts.size() - 1);
    }

    private static String joinInts(java.util.Set<Integer> s) {
        List<String> out = new ArrayList<>();
        for (Integer i : s) out.add(String.valueOf(i));
        return String.join("/", out);
    }

    static String findGstinLike(String message) {
        if (message == null) return "";
        Matcher m = Pattern.compile("[0-9]{2}[A-Za-z0-9]{10,14}").matcher(message.replaceAll("[\\s-]", ""));
        return m.find() ? m.group() : "";
    }

    /** Replaces {placeholders}; unknown ones become "-" so a user never sees raw braces. */
    public static String fill(String template, Map<String, String> v) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String val = v.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "-" : val));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("[ \\t]+\\n", "\n").trim();
    }

    // ---------------------------------------------------------------- LLM layer

    /**
     * Step 3: let the LLM rewrite the filled KB answer in the user's language. Returns the
     * original when the LLM is missing, fails, or invents a number that is not in the draft
     * or the context (guard against made-up prices and dates).
     */
    public String polish(LlmClient llm, String systemPrompt, KbEntry e, HelpContext ctx, String userMessage,
                         String draft, Lang lang) {
        if (llm == null || !llm.isConfigured() || !ctx.llmAvailable) return draft;
        String contextJson = MiniJson.write(ctx.toMaskedMap());
        String sys = systemPrompt.replace("{language}", lang.displayName);
        String out = llm.rewrite(sys, e.id + ": " + e.answer(Lang.EN), contextJson, Masking.freeText(userMessage), draft);
        if (out == null || out.trim().isEmpty()) return draft;
        if (!numbersGrounded(out, draft + " " + contextJson)) return draft;
        return out.trim();
    }

    static boolean numbersGrounded(String out, String allowed) {
        Matcher m = Pattern.compile("\\d[\\d,]*").matcher(out);
        String allowedDigits = allowed.replace(",", "");
        while (m.find()) {
            String n = m.group().replace(",", "");
            if (n.length() >= 2 && !allowedDigits.contains(n)) return false;
        }
        return true;
    }
}
