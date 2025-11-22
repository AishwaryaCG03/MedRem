package com.example.smart_dosage;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smart_dosage.data.AppDatabase;
import com.example.smart_dosage.data.DoseEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {
    private final List<Map<String, String>> timeline = new ArrayList<>();
    private TimelineAdapter timelineAdapter;
    private CalendarAdapter calendarAdapter;
    private java.util.Date selectedDay;
    private final Map<Long, String> medNameCache = new HashMap<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        TextView tvMetrics = findViewById(R.id.tv_metrics);
        TextView tvStreak = findViewById(R.id.tv_streak);
        RecyclerView rvTimeline = findViewById(R.id.rv_timeline);
        RecyclerView rvCalendar = findViewById(R.id.rv_calendar);
        rvTimeline.setLayoutManager(new LinearLayoutManager(this));
        timelineAdapter = new TimelineAdapter(timeline);
        rvTimeline.setAdapter(timelineAdapter);
        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        calendarAdapter = new CalendarAdapter(new ArrayList<>(), day -> {
            selectedDay = day;
            loadTimelineForDay(day);
        });
        rvCalendar.setAdapter(calendarAdapter);

        loadStats(tvMetrics, tvStreak);
        findViewById(R.id.btn_day).setOnClickListener(v -> loadStats(tvMetrics, tvStreak));
        findViewById(R.id.btn_week).setOnClickListener(v -> loadStats(tvMetrics, tvStreak));
        findViewById(R.id.btn_month).setOnClickListener(v -> loadStats(tvMetrics, tvStreak));
    }

    private void loadStats(TextView tvMetrics, TextView tvStreak) {
        new Thread(() -> {
            Calendar cal = Calendar.getInstance();
            java.util.Date today = cal.getTime();
            java.util.Date weekStart, monthStart, monthEnd;
            cal.add(Calendar.DAY_OF_YEAR, -6);
            weekStart = cal.getTime();
            cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            monthStart = cal.getTime();
            cal.add(Calendar.MONTH, 1); cal.add(Calendar.MILLISECOND, -1);
            monthEnd = cal.getTime();

            int takenWeek = AppDatabase.get(this).doseEventDao().countTaken(weekStart, today);
            int scheduledWeek = AppDatabase.get(this).doseEventDao().countScheduled(weekStart, today);
            int weekPct = scheduledWeek == 0 ? 0 : (int) Math.round((takenWeek * 100.0) / scheduledWeek);

            int takenMonth = AppDatabase.get(this).doseEventDao().countTaken(monthStart, monthEnd);
            int scheduledMonth = AppDatabase.get(this).doseEventDao().countScheduled(monthStart, monthEnd);
            int monthPct = scheduledMonth == 0 ? 0 : (int) Math.round((takenMonth * 100.0) / scheduledMonth);

            List<DoseEvent> monthEvents = AppDatabase.get(this).doseEventDao().historyAllList(monthStart, monthEnd);
            Map<String, int[]> dayCounts = new HashMap<>(); // key=yyyy-MM-dd, [scheduled, taken]
            SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
            if (monthEvents != null) {
                for (DoseEvent e : monthEvents) {
                    String key = dayFmt.format(e.scheduledTime != null ? e.scheduledTime : e.actionTime);
                    int[] arr = dayCounts.getOrDefault(key, new int[]{0,0});
                    arr[0] += 1; // scheduled
                    if ("TAKEN".equalsIgnoreCase(e.action)) arr[1] += 1;
                    dayCounts.put(key, arr);
                }
            }

            int streak = 0; // consecutive fully taken days
            Calendar scan = Calendar.getInstance();
            for (int i=0;i<30;i++){
                String k = dayFmt.format(scan.getTime());
                int[] arr = dayCounts.get(k);
                if (arr != null && arr[0] > 0 && arr[1] == arr[0]) streak++; else break;
                scan.add(Calendar.DAY_OF_YEAR, -1);
            }

            List<DayCell> monthCells = buildMonthCells(dayCounts);
            final int finalWeekPct = weekPct;
            final int finalMonthPct = monthPct;
            final int finalStreak = streak;
            runOnUiThread(() -> {
                ((android.widget.ProgressBar)findViewById(R.id.pb_week)).setProgress(finalWeekPct);
                tvMetrics.setText("Weekly adherence: " + finalWeekPct + "% • Monthly: " + finalMonthPct + "%");
                tvStreak.setText("🏅 Streak: " + finalStreak + " days");
                ObjectAnimator anim = ObjectAnimator.ofFloat(tvStreak, "scaleX", 1f, 1.06f, 1f);
                anim.setDuration(800); anim.start();
                ObjectAnimator anim2 = ObjectAnimator.ofFloat(tvStreak, "scaleY", 1f, 1.06f, 1f); anim2.setDuration(800); anim2.start();

                calendarAdapter.setCells(monthCells);
                if (selectedDay == null) selectedDay = new java.util.Date();
                loadTimelineForDay(selectedDay);

                android.widget.FrameLayout pie = findViewById(R.id.pie_container);
                pie.removeAllViews();
                pie.addView(new PieChartView(HistoryActivity.this, monthPct));
            });
        }).start();
    }

    private List<DayCell> buildMonthCells(Map<String,int[]> dayCounts){
        List<DayCell> cells = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH,1); cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sunday
        int offset = (firstDayOfWeek + 6) % 7; // make Monday=0
        for (int i=0;i<offset;i++) cells.add(new DayCell(null, 0));
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        for (int d=1; d<=daysInMonth; d++){
            cal.set(Calendar.DAY_OF_MONTH,d);
            String k = fmt.format(cal.getTime());
            int[] arr = dayCounts.get(k);
            int status = 0; // 0=none,1=missed,2=partial,3=full
            if (arr != null && arr[0]>0){
                if (arr[1]==0) status=1; else if (arr[1]<arr[0]) status=2; else status=3;
            }
            cells.add(new DayCell(cal.getTime(), status));
        }
        return cells;
    }

    private void loadTimelineForDay(java.util.Date day){
        new Thread(() -> {
            Calendar start = Calendar.getInstance(); start.setTime(day); start.set(Calendar.HOUR_OF_DAY,0); start.set(Calendar.MINUTE,0); start.set(Calendar.SECOND,0); start.set(Calendar.MILLISECOND,0);
            Calendar end = Calendar.getInstance(); end.setTime(day); end.set(Calendar.HOUR_OF_DAY,23); end.set(Calendar.MINUTE,59); end.set(Calendar.SECOND,59); end.set(Calendar.MILLISECOND,999);
            List<DoseEvent> events = AppDatabase.get(this).doseEventDao().historyAllList(start.getTime(), end.getTime());
            List<Map<String,String>> maps = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a");
            if (events != null){
                for (DoseEvent e : events){
                    Map<String,String> m = new HashMap<>();
                    String act = e.action;
                    String medName = getMedName(e.medicineId);
                    String title = sdf.format(e.scheduledTime==null?e.actionTime:e.scheduledTime) + " — " + (medName!=null?medName:("Medicine " + e.medicineId)) + " — " + act;
                    String subtitle = ("TAKEN".equals(act) && e.actionTime!=null) ? ("Taken at " + sdf.format(e.actionTime)) : "";
                    m.put("title", title);
                    m.put("subtitle", subtitle);
                    maps.add(m);
                }
            }
            runOnUiThread(() -> { timeline.clear(); timeline.addAll(maps); timelineAdapter.notifyDataSetChanged(); });
        }).start();
    }

    private String getMedName(long id){
        String cached = medNameCache.get(id);
        if (cached != null) return cached;
        com.example.smart_dosage.data.Medicine m = AppDatabase.get(this).medicineDao().getById(id);
        if (m != null && m.name != null){ medNameCache.put(id, m.name); return m.name; }
        return null;
    }

    static class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.VH> {
        private final List<Map<String,String>> items;
        TimelineAdapter(List<Map<String,String>> items){ this.items = items; }
        static class VH extends RecyclerView.ViewHolder { TextView t1; TextView t2; VH(View v){ super(v); t1=v.findViewById(android.R.id.text1); t2=v.findViewById(android.R.id.text2);} }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType){ View v = android.view.LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false); return new VH(v);}    
        @Override public void onBindViewHolder(VH h, int pos){ Map<String,String> m = items.get(pos); h.t1.setText(m.get("title")); h.t2.setText(m.get("subtitle")); }
        @Override public int getItemCount(){ return items.size(); }
    }

    static class DayCell { final java.util.Date day; final int status; DayCell(java.util.Date d,int s){ day=d; status=s; }}
    interface OnDayClick { void onClick(java.util.Date day); }
    static class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.VH> {
        private final List<DayCell> cells; private final OnDayClick onDayClick;
        CalendarAdapter(List<DayCell> cells, OnDayClick click){ this.cells=cells; this.onDayClick=click; }
        void setCells(List<DayCell> newCells){ cells.clear(); cells.addAll(newCells); notifyDataSetChanged(); }
        static class VH extends RecyclerView.ViewHolder { TextView tv; VH(TextView v){ super(v); tv=v; } }
        @Override public VH onCreateViewHolder(ViewGroup parent,int viewType){ TextView tv=new TextView(parent.getContext()); tv.setPadding(8,16,8,16); tv.setTextSize(16f); tv.setGravity(android.view.Gravity.CENTER); return new VH(tv);}    
        @Override public void onBindViewHolder(VH h, int pos){ DayCell c=cells.get(pos); if (c.day==null){ h.tv.setText(""); h.tv.setBackgroundColor(0x00000000); h.tv.setOnClickListener(null); return; } java.util.Calendar cal=java.util.Calendar.getInstance(); cal.setTime(c.day); h.tv.setText(String.valueOf(cal.get(java.util.Calendar.DAY_OF_MONTH))); int color = 0xFF777777; if (c.status==3) color=0xFF2E7D32; else if (c.status==2) color=0xFFF9A825; else if (c.status==1) color=0xFFC62828; android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setCornerRadius(24f); bg.setColor(0xFF2B2B2B); bg.setStroke(4, color); h.tv.setBackground(bg); h.tv.setOnClickListener(v -> onDayClick.onClick(c.day)); }
        @Override public int getItemCount(){ return cells.size(); }
    }

    static class PieChartView extends View {
        private final int pct;
        private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        PieChartView(android.content.Context ctx, int pct){ super(ctx); this.pct=pct; pFill.setStyle(Paint.Style.FILL); pFill.setColor(0xFFBB86FC); pBg.setStyle(Paint.Style.FILL); pBg.setColor(0xFF3E3E3E); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); float w=getWidth(), h=getHeight(); float r=Math.min(w,h)/2f-8f; float cx=w/2f, cy=h/2f; RectF oval=new RectF(cx-r, cy-r, cx+r, cy+r); c.drawArc(oval, 0, 360, true, pBg); c.drawArc(oval, -90, 360f*pct/100f, true, pFill); Paint pText=new Paint(Paint.ANTI_ALIAS_FLAG); pText.setColor(0xFFFFFFFF); pText.setTextSize(36f); pText.setTextAlign(Paint.Align.CENTER); c.drawText(pct+"%", cx, cy+12f, pText);}        
    }
}
