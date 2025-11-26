package com.example.smart_dosage;

import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.DoseEvent;
import com.example.smart_dosage.data.Medicine;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DoctorPackActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_pack);

        android.widget.TextView tvLast = findViewById(R.id.tv_last_path);
        android.view.View btnOpen = findViewById(R.id.btn_open_pdf);
        android.view.View btnShare = findViewById(R.id.btn_share_pdf);
        android.widget.TextView tvStats = findViewById(R.id.tv_stats);

        updateLast(tvLast);

        try {
            java.util.Calendar cal0 = java.util.Calendar.getInstance();
            java.util.Date end0 = cal0.getTime();
            cal0.add(java.util.Calendar.DAY_OF_YEAR, -30);
            java.util.Date start0 = cal0.getTime();
            java.util.List<com.example.smart_dosage.data.DoseEvent> ev0 = com.example.smart_dosage.data.AppDatabase.get(this).doseEventDao().historyAllList(start0, end0);
            int taken0 = 0, missed0 = 0;
            if (ev0 != null) {
                for (com.example.smart_dosage.data.DoseEvent e : ev0) {
                    if ("TAKEN".equals(e.action)) taken0++;
                    else if ("MISSED".equals(e.action)) missed0++;
                }
            }
            if (tvStats != null) tvStats.setText("Last 30 days • Taken: " + taken0 + "  Missed: " + missed0);
        } catch (Exception ignored) {}

        btnOpen.setOnClickListener(v -> {
            java.io.File f = getLastFile();
            if (f == null || !f.exists()) { Toast.makeText(this, "No PDF yet", Toast.LENGTH_SHORT).show(); return; }
            try {
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName()+".fileprovider", f);
                Intent open = new Intent(Intent.ACTION_VIEW);
                open.setDataAndType(uri, "application/pdf");
                open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (getPackageManager().queryIntentActivities(open, 0).isEmpty()) {
                    Intent internal = new Intent(DoctorPackActivity.this, PdfViewerActivity.class);
                    internal.putExtra("path", f.getAbsolutePath());
                    startActivity(internal);
                } else {
                    startActivity(open);
                }
            } catch (Exception e) {
                Intent internal = new Intent(DoctorPackActivity.this, PdfViewerActivity.class);
                internal.putExtra("path", f.getAbsolutePath());
                startActivity(internal);
            }
        });

        btnShare.setOnClickListener(v -> {
            java.io.File f = getLastFile();
            if (f == null || !f.exists()) { Toast.makeText(this, "No PDF yet", Toast.LENGTH_SHORT).show(); return; }
            String pub = getSharedPreferences("doctor_pack", MODE_PRIVATE).getString("last_public_uri", null);
            android.net.Uri uri = null;
            if (pub != null) {
                try { uri = android.net.Uri.parse(pub); } catch (Exception ignore) {}
            }
            if (uri == null) {
                uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName()+".fileprovider", f);
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/pdf");
            send.putExtra(Intent.EXTRA_SUBJECT, "Smart Dosage — Doctor Pack");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share PDF"));
        });

        findViewById(R.id.btn_generate_pdf).setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    Calendar cal = Calendar.getInstance();
                    Date end = cal.getTime();
                    cal.add(Calendar.DAY_OF_YEAR, -30);
                    Date start = cal.getTime();
                    List<Medicine> meds = AppDatabase.get(DoctorPackActivity.this).medicineDao().getAllSync();
                    List<DoseEvent> events = AppDatabase.get(DoctorPackActivity.this).doseEventDao().historyAllList(start, end);

                    PdfDocument doc = new PdfDocument();
                    int pageNum = 1;
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, pageNum).create();
                    PdfDocument.Page page = doc.startPage(info);
                    android.graphics.Canvas c = page.getCanvas();
                    android.graphics.Paint p = new android.graphics.Paint();
                    p.setTextSize(14f);
                    int x = 40; int y = 40; int right = 555; int bottom = 800;

                    y = drawLine(c, p, x, y, right, "Smart Dosage — Doctor Pack"); y += 10;
                    y = drawLine(c, p, x, y, right, "Generated: " + new Date().toString()); y += 10;
                    y = drawLine(c, p, x, y, right, "Current Regimen:"); y += 6;
                    if (meds != null) {
                        for (Medicine m : meds) {
                            y = drawLine(c, p, x+20, y, right, "• " + safe(m.name) + (m.strength!=null?" " + m.strength:"") + (m.dosageAmount>0?" × " + m.dosageAmount:"") );
                            if (m.times != null && !m.times.isEmpty()) y = drawLine(c, p, x+40, y, right, "Times: " + String.join(", ", m.times));
                            if (m.scheduleType != null) y = drawLine(c, p, x+40, y, right, "Schedule: " + m.scheduleType + (m.intervalHours!=null?" ("+m.intervalHours+"h)":"") + (m.weekdays!=null && !m.weekdays.isEmpty()?" " + String.join(", ", m.weekdays):""));
                            if (m.instructions != null && !m.instructions.isEmpty()) y = drawLine(c, p, x+40, y, right, "Instructions: " + m.instructions);
                            if (m.startDate != null) y = drawLine(c, p, x+40, y, right, "Start: " + m.startDate);
                            if (m.endDate != null) y = drawLine(c, p, x+40, y, right, "End: " + m.endDate);
                            if (m.refills > 0) y = drawLine(c, p, x+40, y, right, "Refills: " + m.refills);
                            if (m.initialSupply > 0) y = drawLine(c, p, x+40, y, right, "Initial supply: " + m.initialSupply + "; Doses/day: " + Math.max(1, m.dosesPerDay));
                            y += 6;
                            if (y > bottom) { doc.finishPage(page); info = new PdfDocument.PageInfo.Builder(595, 842, ++pageNum).create(); page = doc.startPage(info); c = page.getCanvas(); y = 40; }
                        }
                    }
                    y += 6;
                    y = drawLine(c, p, x, y, right, "Adherence (last 30 days):");
                    int taken = 0, missed = 0;
                    if (events != null) {
                        for (DoseEvent e : events) {
                            if ("TAKEN".equals(e.action)) taken++;
                            else if ("MISSED".equals(e.action)) missed++;
                        }
                    }
                    y = drawLine(c, p, x+20, y, right, "Taken: " + taken + "    Missed: " + missed);
                    doc.finishPage(page);

                    java.io.File out = savePdf(doc);
                    doc.close();

                    getSharedPreferences("doctor_pack", MODE_PRIVATE).edit().putString("last_path", out.getAbsolutePath()).apply();
                    runOnUiThread(() -> { updateLast(tvLast); Toast.makeText(DoctorPackActivity.this, "Saved: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show(); });
                } catch (Exception ex) {
                    runOnUiThread(() -> Toast.makeText(DoctorPackActivity.this, "Failed to generate", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
    }

    private void updateLast(android.widget.TextView tv) {
        if (tv == null) return;
        String p = getSharedPreferences("doctor_pack", MODE_PRIVATE).getString("last_path", null);
        tv.setText(p == null ? "No PDF generated yet" : ("Last: " + p));
    }

    private java.io.File getLastFile() {
        String p = getSharedPreferences("doctor_pack", MODE_PRIVATE).getString("last_path", null);
        return p == null ? null : new java.io.File(p);
    }

    private int drawLine(android.graphics.Canvas c, android.graphics.Paint p, int x, int y, int right, String text){
        if (text == null) return y;
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length()==0 ? w : (line + " " + w);
            float width = p.measureText(test);
            if (x + width > right) {
                c.drawText(line.toString(), x, y, p);
                y += 20;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) { c.drawText(line.toString(), x, y, p); y += 20; }
        return y;
    }

    private String safe(String s){ return s==null?"":s; }

    private java.io.File savePdf(PdfDocument doc) throws java.io.IOException {
        java.io.File outDir = getExternalFilesDir("docs"); if (outDir == null) outDir = getFilesDir(); if (!outDir.exists()) outDir.mkdirs();
        java.io.File out = new java.io.File(outDir, "doctor_pack.pdf"); try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) { doc.writeTo(fos); }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "doctor_pack.pdf");
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/SmartDosage");
                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                try (java.io.InputStream is = new java.io.FileInputStream(out); java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                }
                getSharedPreferences("doctor_pack", MODE_PRIVATE).edit().putString("last_public_uri", uri.toString()).apply();
            } catch (Exception ignore) {}
        }
        return out;
    }
}
