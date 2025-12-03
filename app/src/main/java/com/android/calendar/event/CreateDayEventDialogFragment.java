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
import com.android.calendar.calendarcommon2.Time;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.SettingsActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;


import java.util.Calendar;
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

    public String extractTimestamp(String title) {
//        智能识别内容中的日期
//        格式为几月几日几点多少到几点多少
//        也可以不带日期

        String pattern1 = "(\\d+):(\\d+)~(\\d+):(\\d+)";//        11:20~11:30
        String pattern2 = "(\\d+)分到(\\d+)分";//5分到10分，同一个小时，忽略小时
        String pattern3 = "(\\d+)~(\\d+)"; //10~20，忽略小时
        String pattern4 = "(\\d+)到(\\d+)";//5到10
        String pattern5 = "(\\d+)月(\\d+)日";//3月1日
        String pattern6 = "(\\d+)月(\\d+)日到(\\d+)月(\\d+)日";//3月1日到3月4日，也就是每天都得干这个
        if (timeMatch(pattern1, title)) {
            Pattern r = Pattern.compile(pattern1);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String starthour = m.group(1);
                String startmiute = m.group(2);
                String endhour = m.group(3);
                String endminute = m.group(4);

                Time starttime = new Time();
                starttime.setTime(Integer.parseInt(starthour), Integer.parseInt(startmiute));
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.setTime(Integer.parseInt(endhour), Integer.parseInt(endminute));
                mEndTime = endtime.toMillis();
            }
        } else if (timeMatch(pattern2, title)) {
            // 5分到15分，按照当前小时计算
            Pattern r = Pattern.compile(pattern2);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String startminute = m.group(1);
                String endminute = m.group(2);

                Time starttime = new Time();
                starttime.setMinuteAndDefault(Integer.parseInt(startminute));
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.setMinuteAndDefault(Integer.parseInt(endminute));
                mEndTime = endtime.toMillis();
            }
        } else if (timeMatch(pattern3, title)) {
//            15~20
            Pattern r = Pattern.compile(pattern3);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String startminute = m.group(1);
                String endminute = m.group(2);

                Time starttime = new Time();
                starttime.setMinuteAndDefault(Integer.parseInt(startminute));
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.setMinuteAndDefault(Integer.parseInt(endminute));
                mEndTime = endtime.toMillis();
            }
        }else if (timeMatch(pattern4,title)){
            Pattern r = Pattern.compile(pattern4);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String startminute = m.group(1);
                String endminute = m.group(2);

                Time starttime = new Time();
                starttime.setMinuteAndDefault(Integer.parseInt(startminute));
                mStartTime = starttime.toMillis();
                Time endtime = new Time();
                endtime.setMinuteAndDefault(Integer.parseInt(endminute));
                mEndTime = endtime.toMillis();
            }
        } else if(timeMatch(pattern5,title)){
            Pattern r = Pattern.compile(pattern5);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String month = m.group(1);
                String day = m.group(2);
                Time starttime = Time.getCurrentTime();
                starttime.set(0,0,0,Integer.parseInt(day),Integer.parseInt(month) -1,starttime.getYear());
                mStartTime = starttime.toMillis();

                Time endtime = Time.getCurrentTime();
                endtime.set(0,0,1,Integer.parseInt(day),Integer.parseInt(month) - 1,starttime.getYear());
                mEndTime = endtime.toMillis();

                title = title.replaceAll(pattern5,"");
            }
        } else {
            //如果都不满足，那就是没带时间，加到0-1点作为今日待办准备安排的
            if(title.startsWith("今天")){
                Time starttime = new Time();
                starttime.SetHourAndDefault(0);

                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.SetHourAndDefault(1);
                mEndTime = endtime.toMillis();
                title = title.replace("今天", "");

            } else if (title.startsWith("明天")) {
                Time starttime = new Time();
                starttime.SetHourAndDefault(0);
                //                明天的
                starttime.add(Time.MONTH_DAY,1);
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.SetHourAndDefault(1);
                endtime.add(Time.MONTH_DAY,1);
                mEndTime = endtime.toMillis();

                title = title.replace("明天", "");

            } else if (title.startsWith("后天")) {
                Time starttime = new Time();
                starttime.SetHourAndDefault(0);
                //                明天的
                starttime.add(Time.MONTH_DAY,2);
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.SetHourAndDefault(1);
                endtime.add(Time.MONTH_DAY,2);
                mEndTime = endtime.toMillis();

                title = title.replace("后天", "");

            }else if (title.startsWith("大后天")) {
                Time starttime = new Time();
                starttime.SetHourAndDefault(0);
                //                明天的
                starttime.add(Time.MONTH_DAY,3);
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.SetHourAndDefault(1);
                endtime.add(Time.MONTH_DAY,3);
                mEndTime = endtime.toMillis();

                title = title.replace("大后天", "");

            } else if (title.startsWith("接下来") || title.startsWith("然后") || title.startsWith("下一步")) {
                Time curTime = new Time().SetCurTime();

                curTime.add(Time.MINUTE,30);

                int startminute = curTime.getMinute() / 30 * 30;
                int endminute = curTime.getMinute() / 30 * 30 + 29;


                Time starttime = new Time();
                starttime.set(0,startminute,curTime.getHour(),curTime.getDay(),curTime.getMonth(),curTime.getYear());
                mStartTime = starttime.toMillis();

                Time endtime = new Time();
                endtime.set(0,endminute,curTime.getHour(),curTime.getDay(),curTime.getMonth(),curTime.getYear());
                mEndTime = endtime.toMillis();
                title = title.replace("接下来","");
                title = title.replace("然后","");
                title = title.replace("下一步","");
            }  else {
//                用当前的30分钟时间点建任务
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
        }
        return title;
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
            String pattern6 = "(\\d+)月(\\d+)日到(\\d+)月(\\d+)日";//3月1日到3月4日，也就是每天都得干这个
            if(timeMatch(pattern6,title)){
//                创建重复的多个活动
                Pattern r = Pattern.compile(pattern6);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    String startmonth = m.group(1);
                    String startday = m.group(2);
                    String endmonth = m.group(3);
                    String endday = m.group(4);
                    Time starttime = Time.getCurrentTime();
                    starttime.set(0,0,0,Integer.parseInt(startday),Integer.parseInt(startmonth) -1,starttime.getYear());

                    Time endtime = Time.getCurrentTime();
                    endtime.set(0,0,1,Integer.parseInt(endday),Integer.parseInt(endmonth) -1 ,starttime.getYear());
                    int succcount = 0;
                    while (starttime.compareTo(endtime) < 0) {
                        String maintitle  = title.replaceAll(pattern6,"");
                        title = String.format("%s月%s日", starttime.getMonth() + 1,starttime.getDay())+ maintitle;
                        title = extractTimestamp(title);
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
                        starttime.add(Time.MONTH_DAY,1);
                    }
                    if (succcount > 0){
                        dismiss();
                        Toast.makeText(getActivity(), String.format("成功创建%s条任务",succcount), Toast.LENGTH_SHORT).show();
                    }
                }
            }else {
                title = extractTimestamp(title);
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

