package com.example.smart_dosage;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.Manifest;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.Medicine;
import com.example.smart_dosage.notifications.ReminderScheduler;
import com.example.smart_dosage.MedicineAdapter;
import com.example.smart_dosage.location.LocationWatcher;
import androidx.annotation.NonNull;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private AppDatabase db;
    private RecyclerView list;
    private MedicineAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        

        db = AppDatabase.get(this);
        list = findViewById(R.id.medicine_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MedicineAdapter(medicine -> {
            ReminderScheduler.scheduleForMedicine(this, medicine);
        });
        list.setAdapter(adapter);

        db.medicineDao().getAll().observe(this, new Observer<List<Medicine>>() {
            @Override
            public void onChanged(List<Medicine> medicines) {
                adapter.submit(medicines);
                View empty = findViewById(R.id.empty_view);
                if (empty != null) empty.setVisibility(medicines == null || medicines.isEmpty() ? View.VISIBLE : View.GONE);
                list.setVisibility(medicines == null || medicines.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AddMedicineActivity.class));
            }
        });
        findViewById(R.id.btn_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PrescriptionScanActivity.class));
            }
        });
        findViewById(R.id.btn_history).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        findViewById(R.id.btn_chat).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ChatActivity.class));
            }
        });
        findViewById(R.id.btn_supply).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SupplyActivity.class));
            }
        });
        View ct = findViewById(R.id.btn_caretakers);
        if (ct != null) ct.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, CaretakerActivity.class)); }
        });
        View st = findViewById(R.id.btn_settings);
        if (st != null) st.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationWatcher.start(this);
        }
    }

    
}
