package com.example.smart_dosage;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.Medicine;
import com.example.smart_dosage.data.Supply;

import java.util.List;

public class SupplyActivity extends AppCompatActivity {
    private List<Medicine> medicines;
    private long selectedId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supply);

        Spinner sp = findViewById(R.id.sp_medicine);
        TextView tvName = findViewById(R.id.tv_med_name);
        TextView tvDaysLeft = findViewById(R.id.tv_days_left);
        TextView tvProgress = findViewById(R.id.tv_progress);
        ProgressBar pb = findViewById(R.id.pb_supply);
        Switch swEnable = findViewById(R.id.sw_enable_refill);
        new Thread(() -> {
            medicines = AppDatabase.get(SupplyActivity.this).medicineDao().getAllSync();
            runOnUiThread(() -> {
                ArrayAdapter<String> ad = new ArrayAdapter<>(SupplyActivity.this, android.R.layout.simple_spinner_item);
                for (Medicine m : medicines) ad.add(m.name);
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sp.setAdapter(ad);
            });
        }).start();

        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Medicine m = medicines.get(position);
                selectedId = m.id;
                new Thread(() -> {
                    Supply s = AppDatabase.get(SupplyActivity.this).supplyDao().getByMedicine(selectedId);
                    runOnUiThread(() -> {
                        tvName.setText(m.name);
                        int remaining = s != null ? s.remaining : m.initialSupply;
                        int total = m.initialSupply;
                        int dosesPerDay = Math.max(1, m.dosesPerDay);
                        int daysLeft = total==0?0: (remaining / dosesPerDay);
                        tvDaysLeft.setText("Estimated days left: " + daysLeft);
                        int pct = total==0?0: (int)Math.round(remaining * 100.0 / total);
                        pb.setProgress(pct);
                        tvProgress.setText(remaining + " left out of " + total);
                        ((EditText)findViewById(R.id.et_remaining)).setText(String.valueOf(remaining));
                        ((EditText)findViewById(R.id.et_lead_days)).setText(s != null ? String.valueOf(s.refillLeadDays) : "5");
                        swEnable.setChecked(s == null || s.refillLeadDays > 0);
                        ((EditText)findViewById(R.id.et_lead_days)).setEnabled(swEnable.isChecked());
                    });
                }).start();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        swEnable.setOnCheckedChangeListener((buttonView, isChecked) -> ((EditText)findViewById(R.id.et_lead_days)).setEnabled(isChecked));

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            final int remainingVal = Integer.parseInt(((EditText)findViewById(R.id.et_remaining)).getText().toString());
            int parsedLead = Integer.parseInt(((EditText)findViewById(R.id.et_lead_days)).getText().toString());
            final int leadVal = swEnable.isChecked() ? parsedLead : 0; // disable reminders when off
            new Thread(() -> {
                Supply s = AppDatabase.get(SupplyActivity.this).supplyDao().getByMedicine(selectedId);
                if (s == null) { s = new Supply(); s.medicineId = selectedId; }
                s.remaining = remainingVal;
                s.refillLeadDays = leadVal;
                s.lastUpdated = new java.util.Date();
                if (s.id == 0) AppDatabase.get(SupplyActivity.this).supplyDao().insert(s); else AppDatabase.get(SupplyActivity.this).supplyDao().update(s);
                Medicine m = AppDatabase.get(SupplyActivity.this).medicineDao().getById(selectedId);
                if (m != null) com.example.smart_dosage.supply.SupplyManager.ensureInitial(SupplyActivity.this, m);
                finish();
            }).start();
        });
    }
}
