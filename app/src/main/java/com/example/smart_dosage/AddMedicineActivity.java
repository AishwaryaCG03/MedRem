package com.example.smart_dosage;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.Medicine;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AddMedicineActivity extends AppCompatActivity {
    private static final int REQ_PHOTO = 100;
    private final List<String> times = new ArrayList<>();
    private String photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_medicine);

        // no auto-complete in simple layout

        Spinner spType = findViewById(R.id.sp_schedule_type);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"TIMES","EVERY_X_HOURS","WEEKDAYS"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(ad);

        findViewById(R.id.btn_add_time).setOnClickListener(v -> {
            TimePickerDialog dlg = new TimePickerDialog(AddMedicineActivity.this, (view, hourOfDay, minute) -> {
                String t = String.format("%02d:%02d", hourOfDay, minute);
                times.add(t);
                TextView tv = findViewById(R.id.tv_times);
                if (tv != null) tv.setText(String.join(", ", times));
            }, 8, 0, true);
            dlg.show();
        });

        // No +/- steppers in simple layout

        EditText etStart = findViewById(R.id.et_start_date);
        EditText etEnd = findViewById(R.id.et_end_date);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (etStart != null) {
            etStart.setText(sdf.format(cal.getTime()));
            etStart.setFocusable(false);
            etStart.setClickable(true);
            etStart.setOnClickListener(v -> {
                java.util.Calendar c = java.util.Calendar.getInstance();
                new android.app.DatePickerDialog(AddMedicineActivity.this, (picker, y, m, d) -> {
                    java.util.Calendar picked = java.util.Calendar.getInstance();
                    picked.set(y, m, d);
                    etStart.setText(sdf.format(picked.getTime()));
                }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show();
            });
        }
        if (etEnd != null) {
            etEnd.setFocusable(false);
            etEnd.setClickable(true);
            etEnd.setOnClickListener(v -> {
                java.util.Calendar c = java.util.Calendar.getInstance();
                new android.app.DatePickerDialog(AddMedicineActivity.this, (picker, y, m, d) -> {
                    java.util.Calendar picked = java.util.Calendar.getInstance();
                    picked.set(y, m, d);
                    etEnd.setText(sdf.format(picked.getTime()));
                }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show();
            });
        }

        findViewById(R.id.btn_photo).setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pick, REQ_PHOTO);
        });

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText etName = findViewById(R.id.et_name);
                EditText etStrength = findViewById(R.id.et_strength);
                EditText etDoseAmt = findViewById(R.id.et_dose_amount);
                EditText etStart = findViewById(R.id.et_start_date);
                EditText etEnd = findViewById(R.id.et_end_date);
                EditText etInstr = findViewById(R.id.et_instructions);
                EditText etRefills = findViewById(R.id.et_refills);
                EditText etInitialSupply = findViewById(R.id.et_initial_supply);
                EditText etInterval = findViewById(R.id.et_interval_hours);
                EditText etWeekdays = findViewById(R.id.et_weekdays);

                Medicine m = new Medicine();
                m.name = etName.getText().toString();
                m.strength = etStrength.getText().toString();
                try { m.dosageAmount = Double.parseDouble(etDoseAmt.getText().toString()); } catch (Exception e) { m.dosageAmount = 0; }
                m.times = new ArrayList<>(times);
                m.dosesPerDay = m.times.size();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                try { m.startDate = sdf.parse(etStart.getText().toString()); } catch (ParseException e) { m.startDate = new Date(); }
                try { m.endDate = sdf.parse(etEnd.getText().toString()); } catch (ParseException e) { m.endDate = null; }
                m.instructions = etInstr.getText().toString();
                try { m.refills = Integer.parseInt(etRefills.getText().toString()); } catch (Exception e) { m.refills = 0; }
                m.photoUri = photoUri;
                try { m.initialSupply = Integer.parseInt(etInitialSupply.getText().toString()); } catch (Exception e) { m.initialSupply = 0; }
                String sel = spType.getSelectedItem().toString();
                m.scheduleType = sel;
                try { m.intervalHours = Integer.parseInt(etInterval.getText().toString()); } catch (Exception e) { m.intervalHours = null; }
                if (etWeekdays.getText() != null && etWeekdays.getText().length() > 0) {
                    String[] ds = etWeekdays.getText().toString().split(",");
                    List<String> wds = new ArrayList<>();
                    for (String d : ds) wds.add(d.trim().toUpperCase());
                    m.weekdays = wds;
                }

                new Thread(() -> {
                    java.util.List<com.example.smart_dosage.data.Medicine> current = AppDatabase.get(AddMedicineActivity.this).medicineDao().getAllSync();
                    String msg = com.example.smart_dosage.interactions.InteractionChecker.check(current, m);
                    runOnUiThread(() -> {
                        if (msg != null && !msg.isEmpty()) {
                            new android.app.AlertDialog.Builder(AddMedicineActivity.this)
                                    .setTitle("Interaction/Side-effect warnings")
                                    .setMessage(msg)
                                    .setPositiveButton("Continue", (d, w) -> saveMedicine(m))
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            saveMedicine(m);
                        }
                    });
                }).start();
            }
        });
    }

    private void saveMedicine(Medicine m) {
        new Thread(() -> {
            long id = AppDatabase.get(AddMedicineActivity.this).medicineDao().insert(m);
            m.id = id;
            com.example.smart_dosage.supply.SupplyManager.ensureInitial(AddMedicineActivity.this, m);
            com.example.smart_dosage.notifications.ReminderScheduler.scheduleForMedicine(AddMedicineActivity.this, m);
            finish();
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            photoUri = uri != null ? uri.toString() : null;
            // no preview display in simple layout
        }
    }
}
