package com.example.smart_dosage.supply;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.Medicine;
import com.example.smart_dosage.data.Supply;
import com.example.smart_dosage.data.SupplyDao;
import com.example.smart_dosage.notifications.RefillReceiver;

import java.util.Calendar;

public class SupplyManager {
    public static void ensureInitial(Context context, Medicine m) {
        SupplyDao dao = AppDatabase.get(context).supplyDao();
        Supply s = dao.getByMedicine(m.id);
        if (s == null) {
            s = new Supply();
            s.medicineId = m.id;
            s.remaining = m.initialSupply;
            s.refillLeadDays = 3;
            s.lastUpdated = new java.util.Date();
            dao.insert(s);
        }
        evaluate(context, m, s);
    }

    public static void decrement(Context context, long medicineId) {
        SupplyDao dao = AppDatabase.get(context).supplyDao();
        Supply s = dao.getByMedicine(medicineId);
        if (s == null) return;
        s.remaining = Math.max(0, s.remaining - 1);
        s.lastUpdated = new java.util.Date();
        dao.update(s);
        Medicine m = AppDatabase.get(context).medicineDao().getById(medicineId);
        if (m != null) evaluate(context, m, s);
    }

    private static void evaluate(Context context, Medicine m, Supply s) {
        int dosesPerDay = Math.max(1, m.dosesPerDay);
        if (s.refillLeadDays <= 0) return; // disabled
        int threshold = dosesPerDay * s.refillLeadDays;
        if (s.remaining <= threshold) scheduleRefill(context, m);
    }

    private static void scheduleRefill(Context context, Medicine m) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, RefillReceiver.class);
        i.putExtra("medicineId", m.id);
        i.putExtra("name", m.name);
        PendingIntent pi = PendingIntent.getBroadcast(context, (int) (m.id + 5000), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = Calendar.getInstance().getTimeInMillis() + 60_000L;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }
}
