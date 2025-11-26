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
        android.content.SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
        boolean logged = sp.getBoolean("logged_in", false);
        if (!logged) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(i);
                    android.widget.Toast.makeText(this, "Enable exact alarms for reliable reminders", android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception ignore) {}
            }
        }

        com.example.smart_dosage.notifications.ReminderScheduler.scheduleAll(this);

        db = AppDatabase.get(this);
        list = findViewById(R.id.medicine_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MedicineAdapter(medicine -> {
            ReminderScheduler.scheduleForMedicine(this, medicine);
        }, medicine -> {
            android.widget.EditText etName = new android.widget.EditText(MainActivity.this);
            etName.setHint("Name");
            etName.setText(medicine.name);
            android.widget.EditText etStrength = new android.widget.EditText(MainActivity.this);
            etStrength.setHint("Strength");
            etStrength.setText(medicine.strength);
            android.widget.EditText etInstructions = new android.widget.EditText(MainActivity.this);
            etInstructions.setHint("Instructions");
            etInstructions.setText(medicine.instructions);
            android.widget.LinearLayout cont = new android.widget.LinearLayout(MainActivity.this);
            cont.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            cont.setPadding(pad, pad/2, pad, 0);
            cont.addView(etName);
            cont.addView(etStrength);
            cont.addView(etInstructions);
            new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Edit medicine")
                    .setView(cont)
                    .setPositiveButton("Save", (d,w) -> {
                        medicine.name = etName.getText().toString();
                        medicine.strength = etStrength.getText().toString();
                        medicine.instructions = etInstructions.getText().toString();
                        new Thread(() -> db.medicineDao().update(medicine)).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }, m -> showDetails(m));
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
        // scan feature removed
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
        View dp = findViewById(R.id.btn_doctor_pack_home);
        if (dp != null) dp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, DoctorPackActivity.class)); }
        });
        View bg = findViewById(R.id.btn_budget_home);
        if (bg != null) bg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, BudgetActivity.class)); }
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

    private void showDetails(com.example.smart_dosage.data.Medicine m){
        android.widget.LinearLayout cont = new android.widget.LinearLayout(this);
        cont.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        cont.setPadding(pad, pad/2, pad, pad/2);
        android.widget.TextView t1 = new android.widget.TextView(this);
        android.widget.TextView t2 = new android.widget.TextView(this);
        android.widget.TextView t3 = new android.widget.TextView(this);
        android.widget.TextView t4 = new android.widget.TextView(this);
        t1.setText("Name: " + (m.name==null?"":m.name));
        t2.setText("Strength: " + (m.strength==null?"":m.strength));
        t3.setText("Dose: " + m.dosageAmount + (m.times!=null && !m.times.isEmpty()?"  •  " + String.join(", ", m.times):""));
        t4.setText("Instructions: " + (m.instructions==null?"":m.instructions));
        int black = android.graphics.Color.BLACK;
        t1.setTextColor(black); t2.setTextColor(black); t3.setTextColor(black); t4.setTextColor(black);
        cont.addView(t1); cont.addView(t2); cont.addView(t3); cont.addView(t4);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Medicine")
                .setView(cont)
                .setPositiveButton("Edit", (d,w) -> {
                    android.widget.EditText etName = new android.widget.EditText(MainActivity.this);
                    android.widget.EditText etStrength = new android.widget.EditText(MainActivity.this);
                    android.widget.EditText etInstructions = new android.widget.EditText(MainActivity.this);
                    etName.setHint("Name"); etName.setText(m.name);
                    etStrength.setHint("Strength"); etStrength.setText(m.strength);
                    etInstructions.setHint("Instructions"); etInstructions.setText(m.instructions);
                    android.widget.LinearLayout c2 = new android.widget.LinearLayout(MainActivity.this);
                    c2.setOrientation(android.widget.LinearLayout.VERTICAL);
                    c2.setPadding(pad, pad/2, pad, 0);
                    c2.addView(etName); c2.addView(etStrength); c2.addView(etInstructions);
                    new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Edit medicine")
                            .setView(c2)
                            .setPositiveButton("Save", (dd,ww) -> {
                                m.name = etName.getText().toString();
                                m.strength = etStrength.getText().toString();
                                m.instructions = etInstructions.getText().toString();
                                new Thread(() -> db.medicineDao().update(m)).start();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Delete", (d,w) -> {
                    new Thread(() -> db.medicineDao().delete(m)).start();
                })
                .setNeutralButton("Close", null)
                .show();
    }
    
}
