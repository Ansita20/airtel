package com.udyam.digital.help;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional AI layer over the Anthropic Messages API. Call from a background thread.
 * The key comes from local.properties via BuildConfig.ANTHROPIC_API_KEY and is never
 * hard-coded. Any failure returns null so the caller falls back to the KB answer.
 *
 * Note for production: shipping an API key inside an APK exposes it. For anything beyond
 * a demo, route this call through your own backend.
 */
public final class LlmClient {

    /** Change here to switch models. */
    public static final String MODEL = "claude-haiku-5-5";
    static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    static final String API_VERSION = "2023-06-01";
    static final int MAX_TOKENS = 300;
    static final int TIMEOUT_MS = 8000;

    private final String apiKey;

    public LlmClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /** Builds the request body; package-private so tests can check nothing unmasked is sent. */
    static String requestBody(String systemPrompt, String kbEntry, String maskedContextJson, String maskedUserMessage, String draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        List<Object> messages = new ArrayList<>();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content",
                "KNOWLEDGE-BASE ENTRY:\n" + kbEntry
                        + "\n\nCUSTOMER CONTEXT (masked):\n" + maskedContextJson
                        + "\n\nDRAFT ANSWER (facts are correct, keep every number):\n" + draft
                        + "\n\nCUSTOMER MESSAGE:\n" + maskedUserMessage
                        + "\n\nRewrite the draft answer for this customer.");
        messages.add(m);
        body.put("messages", messages);
        // Last line of defence: whatever a caller forgot, no full GSTIN, PAN, Udyam, Aadhaar or
        // phone number leaves the phone. Document images have no path to the network at all.
        return com.udyam.digital.security.OutboundGuard.redact(MiniJson.write(body));
    }

    /** Returns the rewritten answer, or null on any problem (no key, no network, timeout, bad JSON). */
    public String rewrite(String systemPrompt, String kbEntry, String maskedContextJson, String maskedUserMessage, String draft) {
        if (!isConfigured()) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("anthropic-version", API_VERSION);
            byte[] payload = requestBody(systemPrompt, kbEntry, maskedContextJson, maskedUserMessage, draft)
                    .getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = c.getOutputStream()) {
                os.write(payload);
            }
            if (c.getResponseCode() / 100 != 2) return null;
            try (InputStream in = c.getInputStream()) {
                return parseText(KnowledgeBase.readAll(in));
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String parseText(String responseJson) {
        Map<String, Object> r = MiniJson.obj(MiniJson.parse(responseJson));
        StringBuilder sb = new StringBuilder();
        for (Object block : MiniJson.arr(r.get("content"))) {
            Map<String, Object> b = MiniJson.obj(block);
            if ("text".equals(b.get("type"))) sb.append(MiniJson.str(b.get("text"), ""));
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
