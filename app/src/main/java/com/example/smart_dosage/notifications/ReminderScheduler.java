package com.example.smart_dosage.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.example.smart_dosage.data.Medicine;

import java.util.Calendar;
import java.util.List;

public class ReminderScheduler {
    public static void scheduleForMedicine(Context context, Medicine m) {
        if (m.scheduleType == null || m.scheduleType.equals("TIMES")) {
            List<String> times = m.times;
            if (times == null) return;
            for (String t : times) {
                String[] parts = t.split(":");
                int h = Integer.parseInt(parts[0]);
                int min = Integer.parseInt(parts[1]);
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, h);
                c.set(Calendar.MINUTE, min);
                c.set(Calendar.SECOND, 0);
                if (c.getTimeInMillis() < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
                int code = (int)(m.id * 1000 + h * 60 + min);
                setAlarm(context, m, c.getTimeInMillis(), code);
            }
        } else if (m.scheduleType.equals("EVERY_X_HOURS")) {
            int interval = m.intervalHours != null ? m.intervalHours : 8;
            long trigger = System.currentTimeMillis() + interval * 60L * 60L * 1000L;
            int code = (int)(m.id * 1000 + 999);
            setAlarm(context, m, trigger, code);
        } else if (m.scheduleType.equals("WEEKDAYS")) {
            List<String> times = m.times;
            List<String> days = m.weekdays;
            if (times == null || days == null) return;
            for (String d : days) {
                int dow = mapDay(d);
                for (String t : times) {
                    String[] parts = t.split(":");
                    int h = Integer.parseInt(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    Calendar c = Calendar.getInstance();
                    c.set(Calendar.DAY_OF_WEEK, dow);
                    c.set(Calendar.HOUR_OF_DAY, h);
                    c.set(Calendar.MINUTE, min);
                    c.set(Calendar.SECOND, 0);
                    if (c.getTimeInMillis() < System.currentTimeMillis()) c.add(Calendar.WEEK_OF_YEAR, 1);
                    int code = (int)(m.id * 1000 + dow * 10000 + h * 60 + min);
                    setAlarm(context, m, c.getTimeInMillis(), code);
                }
            }
        }
    }

    private static int mapDay(String d) {
        String s = d.toUpperCase();
        switch (s) {
            case "SUN": return Calendar.SUNDAY;
            case "MON": return Calendar.MONDAY;
            case "TUE": return Calendar.TUESDAY;
            case "WED": return Calendar.WEDNESDAY;
            case "THU": return Calendar.THURSDAY;
            case "FRI": return Calendar.FRIDAY;
            case "SAT": return Calendar.SATURDAY;
            default: return Calendar.MONDAY;
        }
    }

    private static void setAlarm(Context context, Medicine m, long triggerAtMillis, int requestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra("medicineId", m.id);
        i.putExtra("name", m.name);
        i.putExtra("dose", m.strength + " • " + m.dosageAmount);
        i.putExtra("scheduledAt", triggerAtMillis);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
    }

    public static void snooze(Context context, long medicineId, int minutes) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra("medicineId", medicineId);
        PendingIntent pi = PendingIntent.getBroadcast(context, (int) medicineId, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = System.currentTimeMillis() + minutes * 60_000L;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
    }

    public static void scheduleAdaptiveCatchup(Context context, com.example.smart_dosage.data.Medicine m, long scheduledMillis) {
        long base = System.currentTimeMillis();
        long minDelay = 45 * 60_000L;
        long spacing = (m.intervalHours != null ? m.intervalHours : 6) * 60L * 60L * 1000L;
        long trigger = Math.max(base + minDelay, scheduledMillis + spacing / 2);
        int code = (int)(m.id * 1000 + 888);
        setAlarm(context, m, trigger, code);
    }

    public static void scheduleAll(Context context) {
        try {
            java.util.List<com.example.smart_dosage.data.Medicine> meds = com.example.smart_dosage.data.AppDatabase.get(context).medicineDao().getAllSync();
            if (meds == null) return;
            for (com.example.smart_dosage.data.Medicine m : meds) scheduleForMedicine(context, m);
        } catch (Exception ignore) {}
    }
}
