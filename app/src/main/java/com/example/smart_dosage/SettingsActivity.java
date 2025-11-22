package com.example.smart_dosage;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ((TextView)findViewById(R.id.desc_notifications)).setText("Notifications are used to remind you of doses and refills.");
        ((TextView)findViewById(R.id.desc_location)).setText("Location is used to suggest nearby pharmacies when you move significantly.");
        ((TextView)findViewById(R.id.desc_camera)).setText("Camera/Photos are used to scan prescriptions and attach medicine photos.");
        ((TextView)findViewById(R.id.desc_caretaker)).setText("Caretaker sharing lets you notify trusted contacts about missed doses.");

        findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 201);
            }
        });
        findViewById(R.id.btn_location).setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 202);
            }
        });
        findViewById(R.id.btn_photos).setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, 203);
                }
            }
        });

        android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        ((android.widget.EditText)findViewById(R.id.et_ai_base_url)).setText(sp.getString("ai_base_url", ""));
        ((android.widget.EditText)findViewById(R.id.et_ai_api_key)).setText(sp.getString("ai_api_key", ""));
        findViewById(R.id.btn_save_ai).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String base = ((android.widget.EditText)findViewById(R.id.et_ai_base_url)).getText().toString();
                String key = ((android.widget.EditText)findViewById(R.id.et_ai_api_key)).getText().toString();
                sp.edit().putString("ai_base_url", base).putString("ai_api_key", key).apply();
                android.widget.Toast.makeText(SettingsActivity.this, "AI settings saved", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
}
