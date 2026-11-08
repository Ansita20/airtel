package com.udyam.digital.order;

import com.udyam.digital.engine.MiniJson;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Order <-> JSON (pure Java, so save/load is unit-tested). */
public final class OrderJson {
    private OrderJson() {}

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private static String d(LocalDate x) { return x == null ? "null" : q(x.toString()); }

    public static String write(Order o) {
        StringBuilder b = new StringBuilder("{");
        b.append("\"id\":").append(q(o.id))
                .append(",\"monthly\":").append(o.monthlyTotal)
                .append(",\"setup\":").append(o.setupTotal)
                .append(",\"contract\":").append(o.contractMonths)
                .append(",\"tier\":").append(o.cityTier)
                .append(",\"created\":").append(d(o.created))
                .append(",\"review\":").append(o.kycUnderReview)
                .append(",\"reviewReason\":").append(q(o.reviewReason))
                .append(",\"payFails\":").append(o.paymentFailures)
                .append(",\"payMethod\":").append(q(o.paymentMethod))
                .append(",\"visitDate\":").append(d(o.visitDate))
                .append(",\"visitSlot\":").append(o.visitSlot == null ? "null" : q(o.visitSlot.name()))
                .append(",\"reschedules\":").append(o.reschedules)
                .append(",\"promised\":").append(d(o.promisedActive));
        b.append(",\"done\":{");
        boolean first = true;
        for (Map.Entry<OrderStep, LocalDate> e : o.done.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(q(e.getKey().name())).append(':').append(d(e.getValue()));
        }
        b.append("},\"notified\":[");
        first = true;
        for (String n : o.notified) {
            if (!first) b.append(',');
            first = false;
            b.append(q(n));
        }
        b.append("],\"lines\":[");
        for (int i = 0; i < o.lines.size(); i++) {
            OrderLine l = o.lines.get(i);
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(q(l.productId)).append(",\"name\":").append(q(l.name))
                    .append(",\"qty\":").append(l.quantity).append(",\"monthly\":").append(l.monthlyInr)
                    .append(",\"setup\":").append(l.setupInr).append(",\"days\":").append(l.installDays).append('}');
        }
        return b.append("]}").toString();
    }

    @SuppressWarnings("unchecked")
    public static Order read(String json) {
        Map<String, Object> m = (Map<String, Object>) MiniJson.parse(json);
        Order o = new Order((String) m.get("id"));
        o.monthlyTotal = i(m.get("monthly"));
        o.setupTotal = i(m.get("setup"));
        o.contractMonths = i(m.get("contract"));
        o.cityTier = i(m.get("tier"));
        o.created = date(m.get("created"));
        o.kycUnderReview = Boolean.TRUE.equals(m.get("review"));
        o.reviewReason = s(m.get("reviewReason"));
        o.paymentFailures = i(m.get("payFails"));
        o.paymentMethod = s(m.get("payMethod"));
        o.visitDate = date(m.get("visitDate"));
        o.visitSlot = m.get("visitSlot") == null ? null : VisitSlot.valueOf((String) m.get("visitSlot"));
        o.reschedules = i(m.get("reschedules"));
        o.promisedActive = date(m.get("promised"));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("done")).entrySet()) {
            o.done.put(OrderStep.valueOf(e.getKey()), date(e.getValue()));
        }
        for (Object n : (List<Object>) m.get("notified")) o.notified.add((String) n);
        for (Object lo : (List<Object>) m.get("lines")) {
            Map<String, Object> l = (Map<String, Object>) lo;
            o.lines.add(new OrderLine(s(l.get("id")), s(l.get("name")), i(l.get("qty")), i(l.get("monthly")),
                    i(l.get("setup")), i(l.get("days"))));
        }
        return o;
    }

    private static int i(Object o) { return o instanceof Double ? ((Double) o).intValue() : 0; }
    private static String s(Object o) { return o == null ? "" : (String) o; }
    private static LocalDate date(Object o) { return o == null ? null : LocalDate.parse((String) o); }
}
