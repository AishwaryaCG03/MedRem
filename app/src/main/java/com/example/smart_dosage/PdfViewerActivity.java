package com.example.smart_dosage;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

public class PdfViewerActivity extends AppCompatActivity {
    private PdfRenderer renderer;
    private PdfRenderer.Page page;
    private ParcelFileDescriptor pfd;
    private ImageView image;
    private int index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        image = findViewById(R.id.pdf_image);
        findViewById(R.id.btn_prev).setOnClickListener(v -> showPage(index - 1));
        findViewById(R.id.btn_next).setOnClickListener(v -> showPage(index + 1));
        String path = getIntent().getStringExtra("path");
        if (path == null || path.isEmpty()) {
            path = getSharedPreferences("doctor_pack", MODE_PRIVATE).getString("last_path", null);
        }
        if (path == null) { Toast.makeText(this, "No PDF found", Toast.LENGTH_SHORT).show(); finish(); return; }
        try {
            File f = new File(path);
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            showPage(0);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open PDF", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showPage(int i){
        if (renderer == null) return;
        if (i < 0 || i >= renderer.getPageCount()) return;
        try {
            if (page != null) page.close();
            page = renderer.openPage(i);
            index = i;
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = (int)(w * (page.getHeight() / (float) page.getWidth()));
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            image.setImageBitmap(bmp);
            ((android.widget.TextView)findViewById(R.id.tv_page)).setText("Page " + (index+1) + " / " + renderer.getPageCount());
        } catch (Exception ignore) {}
    }

    @Override
    protected void onDestroy() {
        if (page != null) page.close();
        if (renderer != null) renderer.close();
        if (pfd != null) try { pfd.close(); } catch (IOException ignore) {}
        super.onDestroy();
    }
}
