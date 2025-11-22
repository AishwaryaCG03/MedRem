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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        RecyclerView rv = findViewById(R.id.rv_messages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        rv.setAdapter(adapter);

        new Thread(() -> meds = AppDatabase.get(ChatActivity.this).medicineDao().getAllSync()).start();

        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = ((EditText)findViewById(R.id.et_input)).getText().toString();
                if (q == null || q.trim().isEmpty()) return;
                ((EditText)findViewById(R.id.et_input)).setText("");
                addMessage(q, true);
                new Thread(() -> {
                    String a = remoteAnswer(q);
                    if (a == null || a.trim().isEmpty()) a = answer(q);
                    final String fa = a;
                    runOnUiThread(() -> addMessage(fa, false));
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
            if (base == null || key == null) return null;
            boolean isGemini = base.contains("generativelanguage.googleapis.com");
            String fullUrl = isGemini ? (base + (base.contains("?")?"&":"?") + "key=" + key) : base;
            java.net.URL url = new java.net.URL(fullUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!isGemini) conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);
            String body;
            if (isGemini) {
                org.json.JSONObject part = new org.json.JSONObject(); part.put("text", q);
                org.json.JSONObject content = new org.json.JSONObject(); content.put("parts", new org.json.JSONArray().put(part));
                org.json.JSONObject root = new org.json.JSONObject(); root.put("contents", new org.json.JSONArray().put(content));
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

    private void addMessage(String text, boolean fromUser) {
        messages.add(new ChatMessage(text, fromUser));
        adapter.notifyItemInserted(messages.size()-1);
    }

    static class ChatMessage { final String text; final boolean fromUser; ChatMessage(String t, boolean u){ text=t; fromUser=u; } }

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final java.util.List<ChatMessage> items;
        ChatAdapter(java.util.List<ChatMessage> items){ this.items = items; }
        static class VH extends RecyclerView.ViewHolder { android.widget.TextView tv; VH(android.widget.TextView v){ super(v); tv=v; } }
        @Override public int getItemViewType(int position){ return items.get(position).fromUser?1:0; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.widget.TextView tv = new android.widget.TextView(parent.getContext());
            tv.setTextSize(16f);
            tv.setPadding(24,16,24,16);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(12,12,12,12);
            lp.gravity = viewType==1 ? android.view.Gravity.END : android.view.Gravity.START;
            tv.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(24f);
            bg.setColor(viewType==1?0xFFBB86FC:0xFF3E3E3E);
            tv.setBackground(bg);
            tv.setTextColor(0xFFFFFFFF);
            return new VH(tv);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) { holder.tv.setText(items.get(position).text); }
        @Override public int getItemCount(){ return items.size(); }
    }
}
