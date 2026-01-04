package com.android.calendar.event;

import ws.xsoh.etar.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.Utils;
import com.android.calendar.calendarcommon2.DateUtils;
import com.android.calendar.calendarcommon2.Time;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.SettingsActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ws.xsoh.etar.databinding.DialogCreateEventFragmentBinding;

public class CreateDayEventDialogFragment extends BottomSheetDialogFragment implements View.OnClickListener {

    private DialogCreateEventFragmentBinding binding;

    private static final String TAG = "CreateDayEventDialogFragment";

    private static final int TOKEN_CALENDARS = 1 << 3;
    private CreateDayEventDialogFragment.CalendarQueryService mService;

    private TextInputEditText mEventTitle;

    private CalendarController mController;
    private EditEventHelper mEditEventHelper;

    private String mDateString;
    private long mStartTime;
    private long mEndTime;

    private CalendarEventModel mModel;
    private long mCalendarId = -1;
    private String mCalendarOwner;

    String pattern00  = "^(\\d+)年(\\d+)月(\\d+)日(\\d+):(\\d+)~(\\d+):(\\d+)";// 2025年3月1日11:20~11:30
    String pattern0  = "^(\\d+)月(\\d+)日(\\d+):(\\d+)~(\\d+):(\\d+)";// 3月1日11:20~11:30
    private String pattern1 = "^(\\d+):(\\d+)~(\\d+):(\\d+)";// 11:20~11:30 ，可能跨天
    private String pattern2 = "^(\\d+)分到(\\d+)分";// 5分到10分，同一个小时，忽略小时
    private String pattern3 = "^(\\d+)~(\\d+)"; //10~20，忽略小时
    private String pattern4 = "^(\\d+)到(\\d+)";//5到10
    private String pattern5 = "^(\\d+)月(\\d+)日";//3月1日

    private String pattern6 =  "^(\\d+)年(\\d+)月(\\d+)日";

    private String patternmulti1 = "^(\\d+)月(\\d+)日到(\\d+)月(\\d+)日";//3月1日到3月4日，也就是每天都得干这个，可能跨年
    /**
     * /**
     * setStyle 圆角效果
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DialogCreateEventFragmentBinding.inflate(inflater);
        initView();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEventTitle = view.findViewById(R.id.et_content);
        view.findViewById(R.id.save_event).setOnClickListener(this);
        focusAndShowInput();

    }

    private void initView() {
        final Context context = getActivity();
        mController = CalendarController.getInstance(getActivity());
        mEditEventHelper = new EditEventHelper(context);
        mModel = new CalendarEventModel(context);
        mService = new CalendarQueryService(context);
        mService.startQuery(TOKEN_CALENDARS, null, Calendars.CONTENT_URI,
                EditEventHelper.CALENDARS_PROJECTION,
                EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE, null,
                null);
    }

    public void focusAndShowInput(){
        mEventTitle.requestFocus();
    }


    /**
     * 设置固定高度
     */
    @Override
    public void onStart() {
        super.onStart();
//        //拿到系统的 bottom_sheet
//        val view: FrameLayout = dialog?.findViewById(R.id.design_bottom_sheet)!!
//        //获取behavior
//        val behavior = BottomSheetBehavior.from(view)
//        //设置弹出高度
//        behavior.peekHeight = 350
    }


    private boolean timeMatch(String pattern, String title) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(title);
        return m.find();
    }



//    是否是多个任务
    private boolean isMultiTask(String title) {
        if (timeMatch(pattern1, title)) {
            Pattern r = Pattern.compile(pattern1);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int starthour = Integer.valueOf(m.group(1));
                int endhour = Integer.valueOf(m.group(3));
//                比如22:00-7:00点睡觉
                if (starthour > endhour){
                    return true;
                }
            }
        }
        return false;
    }


    private TimeCheckResult checkTime(int year, int month, int day, int starthour, int startminute, int endhour, int endminute){
        try {
            LocalDateTime.of(year, month, day, starthour, startminute);
            LocalDateTime.of(year, month, day, endhour, endminute);
        } catch (Exception e){
            return new TimeCheckResult(2, e.getMessage());
        }
        if(LocalDateTime.of(year, month, day, starthour, startminute).isAfter(LocalDateTime.of(year, month, day, endhour, endminute))){
            return new TimeCheckResult(2,"开始时间不能晚于结束时间");
        }
        return new TimeCheckResult();
    }

//    补齐标题中的时间,格式为xx年xx月xx日xx时xx分到xx时xx分
//    并检查输入的格式日期时间是否正确
    private TimeCheckResult fillupTimeInTitle(String title){
        Time currentTime = Time.getCurrentTime();
        if (timeMatch(pattern00, title)) {
            Pattern r = Pattern.compile(pattern00);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int year = Integer.valueOf(m.group(1));
                int month = Integer.valueOf(m.group(2));
                int day = Integer.valueOf(m.group(3));
                int starthour = Integer.valueOf(m.group(4));
                int startminute = Integer.valueOf(m.group(5));
                int endhour = Integer.valueOf(m.group(6));
                int endminute = Integer.valueOf(m.group(7));
                TimeCheckResult timeCheckResult = checkTime(year, month, day, starthour, startminute, endhour, endminute);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(title);
            }
        }else if (timeMatch(pattern0,title)){
                Pattern r = Pattern.compile(pattern0);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    int month = Integer.valueOf(m.group(1));
                    int day = Integer.valueOf(m.group(2));
                    int starthour = Integer.valueOf(m.group(3));
                    int startminute = Integer.valueOf(m.group(4));
                    int endhour = Integer.valueOf(m.group(5));
                    int endminute = Integer.valueOf(m.group(6));
                    TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), starthour, startminute, endhour, endminute);
                    if(timeCheckResult.code != 1){
                        return timeCheckResult;
                    }
                    return new TimeCheckResult(currentTime.getYear() + "年" + month + "月" + day + "日" +
                            starthour + ":" + startminute + "~" + endhour + ":" + endminute + title.replaceAll(pattern0,""));
                }
        }else if (timeMatch(pattern1, title)) {
            Pattern r = Pattern.compile(pattern1);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int starthour = Integer.valueOf(m.group(1));
                int startminute = Integer.valueOf(m.group(2));
                int endhour = Integer.valueOf(m.group(3));
                int endminute = Integer.valueOf(m.group(4));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), starthour, startminute, endhour, endminute);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        starthour + ":" + startminute + "~" + endhour + ":" + endminute + title.replaceAll(pattern1,""));
            }
        }else if (timeMatch(pattern2, title)) {
            Pattern r = Pattern.compile(pattern2);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int startminute = Integer.valueOf(m.group(1));
                int endminute = Integer.valueOf(m.group(2));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), currentTime.getHour(), startminute, currentTime.getHour(), endminute);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + startminute + "~" + currentTime.getHour() + ":" + endminute + title.replaceAll(pattern2,""));
            }
        }else if (timeMatch(pattern3, title)) {
            Pattern r = Pattern.compile(pattern3);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int startminute = Integer.valueOf(m.group(1));
                int endminute = Integer.valueOf(m.group(2));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), currentTime.getHour(), startminute, currentTime.getHour(), endminute);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + startminute + "~" + currentTime.getHour() + ":" + endminute + title.replaceAll(pattern3,""));
            }
        }else if (timeMatch(pattern4, title)) {
            Pattern r = Pattern.compile(pattern4);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int startminute = Integer.valueOf(m.group(1));
                int endminute = Integer.valueOf(m.group(2));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), currentTime.getHour(), startminute, currentTime.getHour(), endminute);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + startminute + "~" + currentTime.getHour() + ":" + endminute + title.replaceAll(pattern4,""));
            }
        }else if (timeMatch(pattern5, title)) {
            Pattern r = Pattern.compile(pattern5);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String startmonth = m.group(1);
                String startday = m.group(2);
                //调用TimeCheck检查时间是否合法
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), Integer.valueOf(startmonth), Integer.valueOf(startday),0, 0, 0, 59);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + startmonth + "月" + startday + "日" +
                       0 + ":" + 0 + "~" + 0 + ":" + 59 + title.replaceAll(pattern5,""));
            }
        } else if (timeMatch(pattern6, title)) {
//            正则提取年月日,分组第一个是年,第二个是月,第三个是日
            Pattern r = Pattern.compile(pattern6);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String startyear = m.group(1);
                String startmonth = m.group(2);
                String startday = m.group(3);
                //调用TimeCheck检查时间是否合法
                TimeCheckResult timeCheckResult = checkTime(Integer.valueOf(startyear), Integer.valueOf(startmonth), Integer.valueOf(startday),0, 0, 0, 59);
                if(timeCheckResult.code != 1){
                    return timeCheckResult;
                }
                return new TimeCheckResult(startyear + "年" + startmonth + "月" + startday + "日" +
                       0 + ":" + 0 + "~" + 0 + ":" + 59 + title.replaceAll(pattern6,""));
            }
        }else if(title.startsWith("今天")){
            title = title.replace("今天，", "");
            title = title.replace("今天", "");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    0 + ":" + 0 + "~" + 0 + ":" + 59 + title);

        } else if (title.startsWith("明天")) {
            currentTime.add(Time.YEAR_DAY,1);
            title = title.replace("明天，", "");
            title = title.replace("明天", "");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    0 + ":" + 0 + "~" + 0 + ":" + 59 + title);


        } else if (title.startsWith("后天")) {
            currentTime.add(Time.YEAR_DAY,2);
            title = title.replace("后天，", "");
            title = title.replace("后天", "");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    0 + ":" + 0 + "~" + 0 + ":" + 59 + title);

        }else if (title.startsWith("大后天")) {
            title = title.replace("大后天，", "");
            title = title.replace("大后天", "");
            currentTime.add(Time.YEAR_DAY,3);
            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    0 + ":" + 0 + "~" + 0 + ":" + 59 + title);


        } else if (title.startsWith("然后然后") || title.startsWith("然后，然后")) {
            currentTime.add(Time.MINUTE,60);
            int startminute = currentTime.getMinute() / 30 * 30;
            int endminute = currentTime.getMinute() / 30 * 30 + 29;

            title = title.replace("然后然后，","");
            title = title.replace("然后然后","");
            title = title.replace("然后，然后，","");
            title = title.replace("然后，然后","");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    currentTime.getHour() + ":" + startminute + "~" + currentTime.getHour() + ":" + endminute + title);

        } else if (title.startsWith("接下来") || title.startsWith("然后") || title.startsWith("下一步")) {
            currentTime.add(Time.MINUTE,30);

            int startminute = currentTime.getMinute() / 30 * 30;
            int endminute = currentTime.getMinute() / 30 * 30 + 29;
            title = title.replace("接下来，","");
            title = title.replace("接下来","");
            title = title.replace("然后，","");
            title = title.replace("然后","");
            title = title.replace("下一步，","");
            title = title.replace("下一步","");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    currentTime.getHour() + ":" + startminute + "~" + currentTime.getHour() + ":" + endminute + title);
        }
        return new TimeCheckResult(1,title);

    }
//    已经统一转换为xx年xx月xx日xx:xx~xx:xx的格式了
    private TimeCheckResult extractTimestamp(String title) {

        TimeCheckResult timeCheckResult = fillupTimeInTitle(title);
        if (timeCheckResult.code != 1){
            return timeCheckResult;
        }

        title = timeCheckResult.title;

        if (timeMatch(pattern00, title)) {
            Pattern r = Pattern.compile(pattern00);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int year = Integer.valueOf(m.group(1));
                int month = Integer.valueOf(m.group(2));
                int day = Integer.valueOf(m.group(3));
                int starthour = Integer.valueOf(m.group(4));
                int startmiute = Integer.valueOf(m.group(5));
                int endhour = Integer.valueOf(m.group(6));
                int endminute = Integer.valueOf(m.group(7));

                Time starttime = Time.getCurrentTime();
                starttime.set(0,startmiute,starthour,day,month-1,year);
                mStartTime = starttime.toMillis();

                Time endtime =  Time.getCurrentTime();
                endtime.set(0,endminute,endhour,day,month-1,year);

                mEndTime = endtime.toMillis();

//                如果分钟是0,29,30,59,则去掉前面所有的时间部分
//                否则只去掉年月日
                Set<Integer> minuteArray = Set.of(0,29,30,59);
                if(minuteArray.contains(startmiute) && minuteArray.contains(endminute)){
                    title = title.replaceAll(pattern00+"，","");
                    title = title.replaceAll(pattern00,"");
                }else{
                    title = title.replaceAll(pattern6+"，","");
                    title = title.replaceAll(pattern6,"");
                }
            }
        }  else {
//               用当前的30分钟时间点建任务
                Time starttime = new Time();
                int curmiute = starttime.getCur(Calendar.MINUTE);
                int startminute = curmiute / 30 * 30;
                int endminute = curmiute / 30 * 30 + 29;
                starttime.setMinuteAndDefault(startminute);
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.setMinuteAndDefault(endminute);
                mEndTime = endtime.toMillis();
        }
        return new TimeCheckResult(1,title);
    }

    // Find the calendar position in the cursor that matches calendar in
    // preference
    private void setDefaultCalendarView(Context context, Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            // Create an error message for the user that, when clicked,
            // will exit this activity without saving the event.
            final Activity activity = getActivity();
            dismiss();
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            builder.setTitle(ws.xsoh.etar.R.string.no_syncable_calendars)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(ws.xsoh.etar.R.string.no_calendars_found)
                    .setPositiveButton(ws.xsoh.etar.R.string.add_calendar, (dialog, which) -> {
                        if (activity != null) {
                            Intent nextIntent = new Intent(activity, SettingsActivity.class);
                            activity.startActivity(nextIntent);
                        }
                    })
                    .setNegativeButton(android.R.string.no, null);
            builder.show();
            return;
        }


        String defaultCalendar = null;
        final Activity activity = getActivity();
        if (activity != null) {
            defaultCalendar = Utils.getSharedPreference(activity,
                    GeneralPreferences.KEY_DEFAULT_CALENDAR, (String) null);
        } else {
            Log.e(TAG, "Activity is null, cannot load default calendar");
        }

        int calendarOwnerIndex = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        int calendarNameIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        int accountNameIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int accountTypeIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String calendarOwner = cursor.getString(calendarOwnerIndex);
            String calendarName = cursor.getString(calendarNameIndex);
            String currentCalendar = calendarOwner + "/" + calendarName;
            if (defaultCalendar == null) {
                // There is no stored default upon the first time running.  Use a primary
                // calendar in this case.
                if (calendarOwner != null &&
                        calendarOwner.equals(cursor.getString(accountNameIndex)) &&
                        !CalendarContract.ACCOUNT_TYPE_LOCAL.equals(
                                cursor.getString(accountTypeIndex))) {
                    setCalendarFields(cursor);
                    return;
                }
            } else if (defaultCalendar.equals(currentCalendar)) {
                // Found the default calendar.
                setCalendarFields(cursor);
                return;
            }
        }
        cursor.moveToFirst();
        setCalendarFields(cursor);
    }

    private void setCalendarFields(Cursor cursor) {
        int calendarIdIndex = cursor.getColumnIndexOrThrow(Calendars._ID);
        int colorIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
        int calendarNameIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        int accountNameIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int calendarOwnerIndex = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);

        mCalendarId = cursor.getLong(calendarIdIndex);
        mCalendarOwner = cursor.getString(calendarOwnerIndex);
        String accountName = cursor.getString(accountNameIndex);
        String calendarName = cursor.getString(calendarNameIndex);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.save_event) {
            String title = mEventTitle.getText().toString();

            if(timeMatch(patternmulti1,title)){
//                创建重复的多个活动
                Pattern r = Pattern.compile(patternmulti1);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    int startmonth = Integer.parseInt(m.group(1));
                    int startday = Integer.parseInt(m.group(2));
                    int endmonth = Integer.parseInt(m.group(3));
                    int endday = Integer.parseInt(m.group(4));

                    Time starttime = Time.getCurrentTime();
                    starttime.set(0,0,0,startday,startmonth -1,starttime.getYear());

                    Time endtime = Time.getCurrentTime();
                    if(startmonth <= endmonth){
                        endtime.set(0,0,1,endday,endmonth -1 ,starttime.getYear());
                    }else {
//                        跨年，比如12-20日到1-5日
                        endtime.set(0,0,1,endday,endmonth -1 ,endtime.getYear()+1);
                    }
                    try {
                        if(DateUtils.getDiffDays(starttime.getYear(),startmonth,startday,endtime.getYear(),endmonth,endday) > 60 ){
                            Toast.makeText(getActivity(), String.format("多任务最长持续2个月，可临期再续"), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }catch (Exception e){
                        Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    int succcount = 0;
                    String maintitle  = title.replaceAll(patternmulti1,"");
                    while (starttime.compareTo(endtime) < 0) {
                        title = String.format("%s年%s月%s日",starttime.getYear(), starttime.getMonth() + 1,starttime.getDay())+ maintitle;
                        TimeCheckResult timeCheckResult = extractTimestamp(title);
                        if (timeCheckResult.code != 1){
                            dismiss();
                            Toast.makeText(getActivity(), timeCheckResult.title, Toast.LENGTH_LONG).show();
                            return;
                        }
                        title = timeCheckResult.title;
                        mModel.mStart = mStartTime;
                        mModel.mEnd = mEndTime;
                        mModel.mTitle = title;
                        mModel.mAllDay = false;
                        mModel.mCalendarId = mCalendarId;
                        mModel.mOwnerAccount = mCalendarOwner;
                        boolean b = mEditEventHelper.saveEvent(mModel, null, 0);
                        if (b){
                            succcount ++;
                        }
                        starttime.add(Time.YEAR_DAY,1);
                    }
                    if (succcount > 0){
                        dismiss();
                        Toast.makeText(getActivity(), String.format("成功创建%s条任务",succcount), Toast.LENGTH_SHORT).show();
                    }
                }
            }else if (timeMatch(pattern1, title) && isMultiTask(title)){
//                隔天的任务,比如22:00-7:00点睡觉
                Pattern r = Pattern.compile(pattern1);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    String starthour = m.group(1);
                    String startmiute = m.group(2);
                    String endhour = m.group(3);
                    String endminute = m.group(4);
                    String maintitle  = title.replaceAll(pattern1,"");
                    Time currentTime = Time.getCurrentTime();
                    String title1 = currentTime.getYear()+"年"+(currentTime.getMonth()+1)+"月"+currentTime.getDay()+"日"+starthour+":"+ startmiute +"~"+ "23:59" +maintitle;
                    currentTime.add(Time.YEAR_DAY,1);
                    String title2 = currentTime.getYear()+"年"+(currentTime.getMonth()+1)+"月"+currentTime.getDay()+"日"+"00:00~"+ endhour+":"+ endminute + maintitle;
                    String[] titles = new String[]{title1,title2};
                    int succcount = 0;
                    for (String t : titles){
                        TimeCheckResult timeCheckResult = extractTimestamp(t);
                        if (timeCheckResult.code != 1){
                            dismiss();
                            Toast.makeText(getActivity(), timeCheckResult.title, Toast.LENGTH_LONG).show();
                            return;
                        }
                        title = timeCheckResult.title;
                        mModel.mStart = mStartTime;
                        mModel.mEnd = mEndTime;
                        mModel.mTitle = title;
                        mModel.mAllDay = false;
                        mModel.mCalendarId = mCalendarId;
                        mModel.mOwnerAccount = mCalendarOwner;
                        boolean b = mEditEventHelper.saveEvent(mModel, null, 0);
                        if (b){
                            succcount ++;
                        }
                    }
                    if (succcount > 0){
                        dismiss();
                        Toast.makeText(getActivity(), String.format("成功创建%s条任务",succcount), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                TimeCheckResult timeCheckResult = extractTimestamp(title);
                if (timeCheckResult.code != 1){
                    dismiss();
                    Toast.makeText(getActivity(), timeCheckResult.title, Toast.LENGTH_LONG).show();
                    return;
                }
                title = timeCheckResult.title;
                mModel.mStart = mStartTime;
                mModel.mEnd = mEndTime;
                mModel.mTitle = title;
                mModel.mAllDay = false;
                mModel.mCalendarId = mCalendarId;
                mModel.mOwnerAccount = mCalendarOwner;

                if (mEditEventHelper.saveEvent(mModel, null, 0)) {
                    dismiss();
                    Toast.makeText(getActivity(), R.string.creating_event, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private class TimeCheckResult{
// 1-正常,2-时间错误
        private int code = 1;
        private String title = "";

        public TimeCheckResult(){

        }
        public TimeCheckResult(int code, String title){
            this.code = code;
            this.title = title;
        }

        public TimeCheckResult(String title){
            this.title = title;
        }

        public TimeCheckResult(int code){
            this.code = code;
        }
    }

    private class CalendarQueryService extends AsyncQueryService {

        /**
         * @param context
         */
        public CalendarQueryService(Context context) {
            super(context);
        }

        @Override
        public void onQueryComplete(int token, Object cookie, Cursor cursor) {
            setDefaultCalendarView(requireContext(), cursor);
            if (cursor != null) {
                cursor.close();
            }
        }
    }


}

