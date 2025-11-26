package com.example.smart_dosage;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.Medicine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {
    private List<Medicine> meds;
    private final java.util.List<ChatMessage> messages = new java.util.ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rv;
    private android.view.View typing;
    private android.animation.ObjectAnimator typingAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        rv = findViewById(R.id.rv_messages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        rv.setAdapter(adapter);
        rv.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        typing = findViewById(R.id.typing_indicator);
        if (typing != null) {
            typing.setAlpha(1f);
            typingAnim = android.animation.ObjectAnimator.ofFloat(typing, "alpha", 0.6f, 1f);
            typingAnim.setDuration(600);
            typingAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            typingAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        }

        new Thread(() -> meds = AppDatabase.get(ChatActivity.this).medicineDao().getAllSync()).start();

        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new android.content.Intent(ChatActivity.this, SettingsActivity.class)));

        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = ((EditText)findViewById(R.id.et_input)).getText().toString();
                if (q == null || q.trim().isEmpty()) return;
                ((EditText)findViewById(R.id.et_input)).setText("");
                addMessage(q, true);
                final int thinkingIndex;
                addMessage("Thinking…", false);
                thinkingIndex = messages.size()-1;
                if (typing != null) typing.setVisibility(android.view.View.VISIBLE);
                if (typingAnim != null) typingAnim.start();
                new Thread(() -> {
                    String a = remoteAnswer(q);
                    if (a == null || a.trim().isEmpty()) a = answer(q);
                    final String fa = beautify(a);
                    runOnUiThread(() -> { messages.set(thinkingIndex, new ChatMessage(fa, false)); adapter.notifyItemChanged(thinkingIndex); rv.scrollToPosition(adapter.getItemCount()-1); if (typing != null) typing.setVisibility(android.view.View.GONE); if (typingAnim != null) typingAnim.cancel(); typing.setAlpha(1f); });
                }).start();
            }
        });
    }

    private String answer(String q) {
        String lq = q.toLowerCase(Locale.ROOT);
        if (meds == null || meds.isEmpty()) return "No medicines found.";
        for (Medicine m : meds) {
            if (m.name != null && lq.contains(m.name.toLowerCase(Locale.ROOT))) {
                StringBuilder sb = new StringBuilder();
                sb.append("💊 ").append(m.name).append(" ").append(m.strength==null?"":m.strength).append("\n");
                if (m.times != null && !m.times.isEmpty()) sb.append("⏱️ ").append(String.join(", ", m.times)).append("\n");
                if (m.instructions != null && !m.instructions.isEmpty()) sb.append("📋 ").append(m.instructions).append("\n");
                sb.append("If in doubt, consult your doctor.");
                return sb.toString();
            }
        }
        return "Please mention a medicine name.";
    }

    private String remoteAnswer(String q) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
            String base = sp.getString("ai_base_url", null);
            String key = sp.getString("ai_api_key", null);
            if (base == null) return null;
            boolean isGemini = base.contains("generativelanguage.googleapis.com");
            boolean isOpenAI = base.contains("openai.com");
            boolean isOpenRouter = base.contains("openrouter.ai");
            boolean isLocal = false;
            try { java.net.URI u = new java.net.URI(base); String h = u.getHost(); if (h != null) { String lh = h.toLowerCase(); isLocal = lh.equals("localhost") || lh.equals("127.0.0.1") || lh.startsWith("10.") || lh.startsWith("192.168."); } } catch (Exception ignore) {}
            if (isGemini && (key == null || key.isEmpty())) return null;
            if (!isGemini && !isLocal && (key == null || key.isEmpty())) return null;
            String correctedBase = base;
            if (isGemini) {
                correctedBase = correctedBase.replace("/v1beta/", "/v1/");
                correctedBase = correctedBase.replace("gemini-1.5-flash:", "gemini-1.5-flash-latest:");
            }
            String fullUrl = isGemini ? (correctedBase + (correctedBase.contains("?")?"&":"?" ) + "key=" + key) : correctedBase;
            java.net.URL url = new java.net.URL(fullUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!isGemini && key != null && !key.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);
            String body;
            if (isGemini) {
                org.json.JSONObject part = new org.json.JSONObject(); part.put("text", q);
                org.json.JSONObject content = new org.json.JSONObject(); content.put("parts", new org.json.JSONArray().put(part));
                org.json.JSONObject root = new org.json.JSONObject(); root.put("contents", new org.json.JSONArray().put(content));
                body = root.toString();
            } else if (isOpenAI || isOpenRouter) {
                String model = getSharedPreferences("settings", MODE_PRIVATE).getString("ai_model_id", isOpenRouter?"openai/gpt-4o-mini":"gpt-4o-mini");
                org.json.JSONObject root = new org.json.JSONObject();
                root.put("model", model);
                org.json.JSONArray msgs = new org.json.JSONArray();
                org.json.JSONObject m = new org.json.JSONObject(); m.put("role","user"); m.put("content", q); msgs.put(m);
                root.put("messages", msgs);
                body = root.toString();
            } else {
                body = "{\"inputs\": " + JSONObject.quote(q) + "}";
            }
            try (java.io.OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            int code = conn.getResponseCode();
            java.io.InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String contentType = conn.getHeaderField("Content-Type");
            String resp = readStream(is);
            if (contentType != null && contentType.contains("text/html")) return null; // avoid dumping HTML pages
            // HuggingFace style
            try {
                JSONArray arr = new JSONArray(resp);
                if (arr.length() > 0) {
                    JSONObject o = arr.getJSONObject(0);
                    if (o.has("generated_text")) return o.getString("generated_text");
                }
            } catch (Exception ignore) {}
            // OpenAI-style
            try {
                JSONObject o = new JSONObject(resp);
                if (o.has("choices")) {
                    JSONArray choices = o.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject c = choices.getJSONObject(0);
                        if (c.has("text")) return c.getString("text");
                        if (c.has("message")) {
                            JSONObject m = c.getJSONObject("message");
                            if (m.has("content")) return m.getString("content");
                        }
                    }
                }
                if (o.has("candidates")) {
                    org.json.JSONArray candidates = o.getJSONArray("candidates");
                    if (candidates.length() > 0) {
                        org.json.JSONObject cand = candidates.getJSONObject(0);
                        if (cand.has("content")) {
                            org.json.JSONObject cObj = cand.getJSONObject("content");
                            if (cObj.has("parts")) {
                                org.json.JSONArray parts = cObj.getJSONArray("parts");
                                if (parts.length() > 0) {
                                    org.json.JSONObject p0 = parts.getJSONObject(0);
                                    if (p0.has("text")) return p0.getString("text");
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
            // Try to surface structured error from providers
            try {
                JSONObject o = new JSONObject(resp);
                if (o.has("error")) {
                    JSONObject err = o.getJSONObject("error");
                    String msg = err.optString("message");
                    if (msg != null && !msg.isEmpty()) {
                        String hint = isGemini ? "\nHint: Use v1/models/gemini-1.5-flash-latest:generateContent and a Google AI Studio key." : "";
                        return "AI error: " + msg + hint;
                    }
                }
            } catch (Exception ignore) {}
            // If plain text, strip any tags and return
            String clean = resp.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (!clean.isEmpty()) return clean;
            return null;
        } catch (Exception e) { return null; }
    }

    private String readStream(java.io.InputStream is) throws java.io.IOException {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line; int count = 0;
        while ((line = br.readLine()) != null && count < 1000) { sb.append(line).append('\n'); count += line.length(); }
        return sb.toString();
    }

    private String beautify(String s) {
        if (s == null) return "";
        String t = s.trim();
        try {
            if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                org.json.JSONObject o = new org.json.JSONObject(t);
                if (o.has("choices")) {
                    org.json.JSONArray choices = o.getJSONArray("choices");
                    if (choices.length() > 0) {
                        org.json.JSONObject c = choices.getJSONObject(0);
                        if (c.has("message")) {
                            org.json.JSONObject m = c.getJSONObject("message");
                            if (m.has("content")) t = m.getString("content");
                        } else if (c.has("text")) { t = c.getString("text"); }
                    }
                } else if (o.has("candidates")) {
                    org.json.JSONArray candidates = o.getJSONArray("candidates");
                    if (candidates.length() > 0) {
                        org.json.JSONObject cand = candidates.getJSONObject(0);
                        if (cand.has("content")) {
                            org.json.JSONObject cObj = cand.getJSONObject("content");
                            if (cObj.has("parts")) {
                                org.json.JSONArray parts = cObj.getJSONArray("parts");
                                if (parts.length() > 0) {
                                    org.json.JSONObject p0 = parts.getJSONObject(0);
                                    if (p0.has("text")) t = p0.getString("text");
                                }
                            }
                        }
                    }
                } else if (o.has("generated_text")) { t = o.getString("generated_text"); }
            }
        } catch (Exception ignore) { }
        if (t.contains("\"text\"")) {
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"(.*?)\\\"", java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher m = p.matcher(t);
                if (m.find()) {
                    String extracted = m.group(1);
                    t = extracted;
                }
            } catch (Exception ignore) {}
        }
        t = t.replace("\\n", "\n").replace("\\t", " ").replace("\\r", "");
        t = t.replace("**", "");
        t = t.replaceAll("^[\\s\\S]*?candidates[\\s\\S]*?parts[\\s\\S]*?text\\s*:\\s*", "");
        return t.trim();
    }

    private void addMessage(String text, boolean fromUser) {
        messages.add(new ChatMessage(text, fromUser));
        adapter.notifyItemInserted(messages.size()-1);
        if (rv != null) rv.scrollToPosition(adapter.getItemCount()-1);
    }

    static class ChatMessage { final String text; final boolean fromUser; ChatMessage(String t, boolean u){ text=t; fromUser=u; } }

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final java.util.List<ChatMessage> items;
        ChatAdapter(java.util.List<ChatMessage> items){ this.items = items; }
        static class VH extends RecyclerView.ViewHolder {
            android.widget.LinearLayout root;
            android.widget.TextView avatar;
            android.widget.TextView bubble;
            VH(android.widget.LinearLayout v){ super(v); root=v; avatar=(android.widget.TextView)v.getChildAt(0); bubble=(android.widget.TextView)v.getChildAt(1); }
        }
        @Override public int getItemViewType(int position){ return items.get(position).fromUser?1:0; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.widget.LinearLayout root = new android.widget.LinearLayout(parent.getContext());
            root.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            root.setPadding(12,12,12,12);
            android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0,6,0,6);
            root.setLayoutParams(rp);

            android.widget.TextView avatar = new android.widget.TextView(parent.getContext());
            android.widget.LinearLayout.LayoutParams ap = new android.widget.LinearLayout.LayoutParams(dp(parent,36), dp(parent,36));
            ap.setMargins(8,8,8,8);
            avatar.setLayoutParams(ap);
            avatar.setGravity(android.view.Gravity.CENTER);
            avatar.setTextColor(0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable abg = new android.graphics.drawable.GradientDrawable();
            abg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            abg.setColor(viewType==1?0xFF10A37F:0xFF5865F2);
            avatar.setBackground(abg);
            avatar.setText(viewType==1?"You":"AI");

            android.widget.TextView bubble = new android.widget.TextView(parent.getContext());
            bubble.setTextSize(16f);
            bubble.setPadding(26,20,26,20);
            android.widget.LinearLayout.LayoutParams bp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(10,10,10,10);
            bp.gravity = viewType==1 ? android.view.Gravity.END : android.view.Gravity.START;
            bubble.setLayoutParams(bp);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(24f);
            int aiBg = 0xFFFFFFFF;
            int userBg = 0xFF10A37F;
            bg.setColor(viewType==1?userBg:aiBg);
            bg.setStroke(2, viewType==1?0x3310A37F:0x33B0BEC5);
            bubble.setBackground(bg);
            bubble.setTextColor(viewType==1?0xFFFFFFFF:0xFF2C3E50);
            bubble.setLineSpacing(0f, 1.12f);
            bubble.setElevation(4f);
            int max = (int)(parent.getResources().getDisplayMetrics().widthPixels * 0.85f);
            bubble.setMaxWidth(max);
            bubble.setMaxLines(Integer.MAX_VALUE);
            bubble.setEllipsize(null);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                bubble.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
                bubble.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
            }

            if (viewType==1) { root.setGravity(android.view.Gravity.END); root.addView(avatar); root.addView(bubble); }
            else { root.setGravity(android.view.Gravity.START); root.addView(avatar); root.addView(bubble); }

            return new VH(root);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bubble.setText(items.get(position).text);
            boolean fromUser = items.get(position).fromUser;
            holder.root.setAlpha(0f);
            holder.root.setTranslationX(fromUser ? dp(holder.root, 24) : -dp(holder.root, 24));
            holder.root.animate().alpha(1f).translationX(0f).setDuration(160).start();
        }
        @Override public int getItemCount(){ return items.size(); }

        private int dp(android.view.View v, int d){ return (int) (d * v.getResources().getDisplayMetrics().density); }
    }
}
