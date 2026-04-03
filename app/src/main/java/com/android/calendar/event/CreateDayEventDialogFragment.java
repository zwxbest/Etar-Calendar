package com.android.calendar.event;

import ws.xsoh.etar.R;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
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

    private Context mContext;

    private String mDateString;
    private long mStartTime;
    private long mEndTime;

    private CalendarEventModel mModel;
    private long mCalendarId = -1;
    private String mCalendarOwner;

    String patternFinal = "^(\\d+)年(\\d+)月(\\d+)日(\\d+):(\\d+)~(\\d+)年(\\d+)月(\\d+)日(\\d+):(\\d+)";// 2025年3月1日11:20~2025年3月1日11:30
    String pattern00 = "^(\\d+)年(\\d+)月(\\d+)日(\\d+):(\\d+)~(\\d+):(\\d+)";// 2025年3月1日11:20~11:30
    String pattern0 = "^(\\d+)月(\\d+)日(\\d+):(\\d+)~(\\d+):(\\d+)";// 3月1日11:20~11:30
    private String pattern1 = "^(\\d+):(\\d+)~(\\d+):(\\d+)";// 11:20~11:30 ，可能跨天
    private String pattern2 = "^(\\d+)分到(\\d+)分";// 5分到10分，同一个小时，忽略小时
    private String pattern3 = "^(\\d+)~(\\d+)"; //10~20，忽略小时
    private String pattern4 = "^(\\d+)到(\\d+)";//5到10
    private String pattern5 = "^(\\d+)月(\\d+)日";//3月1日

    private String pattern6 = "^(\\d+)年(\\d+)月(\\d+)日";

    private String pattern7 = "^(\\d+):(\\d+)";//11:30,直接记录11:30~11:59

    private String pattern8 = "^(\\d+)点"; //11点

    private String patternmulti1 = "^(\\d+)月(\\d+)日到(\\d+)月(\\d+)日";//3月1日到3月4日，也就是每天都得干这个，可能跨年
    private String patternmulti2 = "^(\\d+)月(\\d+)日到(\\d+)日";//3月1日到4日

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
        mContext = requireContext();
        initView();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEventTitle = view.findViewById(R.id.et_content);
        view.findViewById(R.id.save_event).setOnClickListener(this);
        mEventTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            // 【2】内容变化中调用（实时监听，最常用）
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentContent = s.toString().trim();
                Log.d(TAG, "实时内容：" + currentContent);
                if (TextUtils.getTrimmedLength(currentContent) > 0) {
                    doOnClick();
                }


            }

            // 【3】内容变化后调用（可选，比如最终确认）
            @Override
            public void afterTextChanged(Editable s) {
            }
        });

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

    // 语音识别请求码（唯一即可）
    private static final int REQ_CODE_VOICE_RECOGNITION = 1001;
    // 录音权限请求码
    private static final int REQ_CODE_RECORD_PERMISSION = 1002;


    /**
     * 检查录音权限，权限通过后调起语音输入
     */
    private void checkRecordPermissionAndStartVoiceInput() {
        // Android 6.0+ 需动态申请录音权限
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_CODE_RECORD_PERMISSION);
        } else {
            // 权限已获取，直接调起语音输入
            startSystemVoiceRecognition();
        }
    }

    /**
     * 调起系统原生语音识别界面
     */
    private void startSystemVoiceRecognition() {
        // 2. 构建语音识别 Intent
        Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        // 配置语音识别参数
        // 语音识别模式：自由输入（支持日常对话/随意说话）
        voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // 语音识别提示语（显示在语音界面）
        voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话，结束后自动识别");
        // 设置识别语言（默认系统语言，这里指定中文）
        voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString());
        // 只返回最佳识别结果（可选，默认返回多个）
        voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        // 3. 启动语音识别界面
        startActivityForResult(voiceIntent, REQ_CODE_VOICE_RECOGNITION);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CODE_RECORD_PERMISSION) {
            // 权限申请通过
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSystemVoiceRecognition();
            }
            // 权限被拒绝
            else {
                Toast.makeText(mContext, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show();

            }
        }
    }

    public void focusAndShowInput() {
        mEventTitle.requestFocus();
//        checkRecordPermissionAndStartVoiceInput();
    }

    // 接收语音识别结果
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 匹配语音识别请求码
        if (requestCode == REQ_CODE_VOICE_RECOGNITION) {
            // 识别成功（RESULT_OK）
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 获取识别结果列表（EXTRA_RESULTS 是 ArrayList<String> 类型）
                ArrayList<String> recognitionResults =
                        data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                if (recognitionResults != null && !recognitionResults.isEmpty()) {
                    // 取第一个结果（最佳匹配）
                    String voiceContent = recognitionResults.get(0);
                    // 将结果填充到输入框
                    mEventTitle.setText(voiceContent);
                    // 将光标移到文本末尾
                    mEventTitle.setSelection(voiceContent.length());
                    Toast.makeText(mContext, "识别成功：" + voiceContent, Toast.LENGTH_SHORT).show();
                }
            }
            // 识别取消（用户点击返回/取消）
            else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(mContext, "已取消语音识别", Toast.LENGTH_SHORT).show();
            }
            // 识别失败
            else {
                Toast.makeText(mContext, "语音识别失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void triggerKeyboardVoiceInput(EditText editText) {
        // 适配主流输入法的语音 Intent（微信/讯飞/百度）
        Intent voiceIntent = new Intent();
        // 通用语音输入 Action
        voiceIntent.setAction("android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH");
        voiceIntent.setPackage("com.iflytek.inputmethod"); // 获取当前激活的输入法包名
        voiceIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            mContext.startActivity(voiceIntent);
        } catch (Exception e) {
            // 输入法不支持则降级为系统语音识别
        }
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
                if (starthour > endhour) {
                    return true;
                }
            }
        }
        return false;
    }


    private TimeCheckResult checkTime(int year, int month, int day, int starthour, int startminute, int endhour, int endminute) {
        try {
            LocalDateTime.of(year, month, day, starthour, startminute);
            LocalDateTime.of(year, month, day, endhour, endminute);
        } catch (Exception e) {
            return new TimeCheckResult(2, e.getMessage());
        }
        if (LocalDateTime.of(year, month, day, starthour, startminute).isAfter(LocalDateTime.of(year, month, day, endhour, endminute))) {
            return new TimeCheckResult(2, "开始时间不能晚于结束时间");
        }
        return new TimeCheckResult();
    }

    //    补齐标题中的时间,格式为2025年3月1日11:20~2025年3月1日11:30
//    并检查输入的格式日期时间是否正确
    private TimeCheckResult fillupTimeInTitle(String title) {
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
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + month + "月" + day + "日" +
                        starthour + ":" + startminute + "~" + currentTime.getYear() + "年" + month + "月" + day + "日" + endhour + ":" + endminute, title.replaceAll(pattern00, ""));
            }
        } else if (timeMatch(pattern0, title)) {
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
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + month + "月" + day + "日" +
                        starthour + ":" + startminute + "~" + currentTime.getYear() + "年" + month + "月" + day + "日" + endhour + ":" + endminute, title.replaceAll(pattern0, ""));
            }
        } else if (timeMatch(pattern1, title)) {
            Pattern r = Pattern.compile(pattern1);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int starthour = Integer.valueOf(m.group(1));
                int startminute = Integer.valueOf(m.group(2));
                int endhour = Integer.valueOf(m.group(3));
                int endminute = Integer.valueOf(m.group(4));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), starthour, startminute, endhour, endminute);
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        starthour + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" + endhour + ":" + endminute,
                        title.replaceAll(pattern1, ""));
            }
        } else if (timeMatch(pattern2, title)) {
            Pattern r = Pattern.compile(pattern2);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int startminute = Integer.valueOf(m.group(1));
                int endminute = Integer.valueOf(m.group(2));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), currentTime.getHour(), startminute, currentTime.getHour(), endminute);
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + endminute, title.replaceAll(pattern2, ""));
            }
        } else if (timeMatch(pattern3, title)) {
            Pattern r = Pattern.compile(pattern3);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int startminute = Integer.valueOf(m.group(1));
                int endminute = Integer.valueOf(m.group(2));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), currentTime.getHour(), startminute, currentTime.getHour(), endminute);
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + endminute, title.replaceAll(pattern3, ""));
            }
        } else if (timeMatch(pattern4, title)) {
            Pattern r = Pattern.compile(pattern4);
            Matcher m = r.matcher(title);
            if (m.find()) {
                int startminute = Integer.valueOf(m.group(1));
                int endminute = Integer.valueOf(m.group(2));
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), currentTime.getHour(), startminute, currentTime.getHour(), endminute);
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        currentTime.getHour() + ":" + endminute, title.replaceAll(pattern4, ""));
            }
        } else if (timeMatch(pattern5, title)) {
            Pattern r = Pattern.compile(pattern5);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String startmonth = m.group(1);
                String startday = m.group(2);
                //调用TimeCheck检查时间是否合法
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), Integer.valueOf(startmonth), Integer.valueOf(startday), 0, 0, 0, 59);
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }

                Time titleDateThenTomorrow = Time.setAndGet(currentTime.getYear(), Integer.valueOf(startmonth) - 1, Integer.valueOf(startday), 0, 0, 0).addAndGet(Time.MONTH_DAY, 1);
//              这样会显示成当天的横条，一直在顶部
                return new TimeCheckResult(currentTime.getYear() + "年" + startmonth + "月" + startday + "日" +
                        0 + ":" + 0 + "~" + titleDateThenTomorrow.getYear() + "年" + (titleDateThenTomorrow.getMonth() + 1) + "月" + titleDateThenTomorrow.getDay() + "日" + 0 + ":" + 0, title.replaceAll(pattern5, ""), true);
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

                TimeCheckResult timeCheckResult = checkTime(Integer.valueOf(startyear), Integer.valueOf(startmonth), Integer.valueOf(startday), 0, 0, 0, 59);
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }

                Time titleDate = Time.setAndGet(Integer.valueOf(startyear), Integer.valueOf(startmonth) - 1, Integer.valueOf(startday), 0, 0, 0);
                Time titleTomorrowDate = Time.setAndGet(Integer.valueOf(startyear), Integer.valueOf(startmonth) - 1, Integer.valueOf(startday), 0, 0, 0).addAndGet(Time.MONTH_DAY, 1);
                return new TimeCheckResult(titleDate.getYear() + "年" + (titleDate.getMonth() + 1) + "月" + titleDate.getDay() + "日" +
                        0 + ":" + 0 + "~" + titleTomorrowDate.getYear() + "年" + (titleTomorrowDate.getMonth() + 1) + "月" + titleTomorrowDate.getDay() + "日" + 0 + ":" + 0, title.replaceAll(pattern6, ""), true);
            }
        } else if (timeMatch(pattern7, title)) {
//            正则提取年月日,分组第一个是年,第二个是月,第三个是日
            Pattern r = Pattern.compile(pattern7);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String starthour = m.group(1);
                String titleminute = m.group(2);
                //调用TimeCheck检查时间是否合法
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), Integer.valueOf(starthour), Integer.valueOf(titleminute),
                        Integer.valueOf(starthour), Integer.valueOf(titleminute));
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                int startminute = Integer.valueOf(titleminute) / 30 * 30;
                int endminute = startminute + 29;

                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        starthour + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        starthour + ":" + endminute, title.replaceAll(pattern7, ""));
            }
        } else if (timeMatch(pattern8, title)) {
            Pattern r = Pattern.compile(pattern8);
            Matcher m = r.matcher(title);
            if (m.find()) {
                String starthour = m.group(1);
                String titleminute = "0";
                //调用TimeCheck检查时间是否合法
                TimeCheckResult timeCheckResult = checkTime(currentTime.getYear(), currentTime.getMonth() + 1, currentTime.getDay(), Integer.valueOf(starthour), Integer.valueOf(titleminute),
                        Integer.valueOf(starthour), Integer.valueOf(titleminute));
                if (timeCheckResult.code != 1) {
                    return timeCheckResult;
                }
                int startminute = Integer.valueOf(titleminute) / 30 * 30;
                int endminute = startminute + 29;

                return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        starthour + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                        starthour + ":" + endminute, title.replaceAll(pattern8, ""));
            }
        } else if (title.startsWith("今天")) {
            title = title.replace("今天，", "");
            title = title.replace("今天", "");
            Time tomorrow = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 1);
            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    0 + ":" + 0 + "~" + tomorrow.getYear() + "年" + (tomorrow.getMonth() + 1) + "月" + tomorrow.getDay() + "日" + 0 + ":" + 0, title, true);

        } else if (title.startsWith("明天")) {
            title = title.replace("明天，", "");
            title = title.replace("明天", "");
            Time tomorrow = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 1);
            Time tomorrow2 = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 2);
            return new TimeCheckResult(tomorrow.getYear() + "年" + (tomorrow.getMonth() + 1) + "月" + tomorrow.getDay() + "日" +
                    0 + ":" + 0 + "~" + tomorrow2.getYear() + "年" + (tomorrow2.getMonth() + 1) + "月" + tomorrow2.getDay() + "日" + 0 + ":" + 0, title, true);


        } else if (title.startsWith("后天")) {
            title = title.replace("后天，", "");
            title = title.replace("后天", "");
            Time tomorrow2 = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 2);
            Time tomorrow3 = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 3);
            return new TimeCheckResult(tomorrow2.getYear() + "年" + (tomorrow2.getMonth() + 1) + "月" + tomorrow2.getDay() + "日" +
                    0 + ":" + 0 + "~" + tomorrow3.getYear() + "年" + (tomorrow3.getMonth() + 1) + "月" + tomorrow3.getDay() + "日" + 0 + ":" + 0, title, true);

        } else if (title.startsWith("大后天")) {
            title = title.replace("大后天，", "");
            title = title.replace("大后天", "");

            Time tomorrow3 = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 3);
            Time tomorrow4 = Time.getCurrentTime().addAndGet(Time.MONTH_DAY, 4);

            return new TimeCheckResult(tomorrow3.getYear() + "年" + (tomorrow3.getMonth() + 1) + "月" + tomorrow3.getDay() + "日" +
                    0 + ":" + 0 + "~" + tomorrow4.getYear() + "年" + (tomorrow4.getMonth() + 1) + "月" + tomorrow4.getDay() + "日" + 0 + ":" + 0, title, true);

        } else if (title.startsWith("然后然后") || title.startsWith("然后，然后")) {
            currentTime.add(Time.MINUTE, 60);
            int startminute = currentTime.getMinute() / 30 * 30;
            int endminute = currentTime.getMinute() / 30 * 30 + 29;

            title = title.replace("然后然后，", "");
            title = title.replace("然后然后", "");
            title = title.replace("然后，然后，", "");
            title = title.replace("然后，然后", "");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    currentTime.getHour() + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" + currentTime.getHour() + ":" + endminute, title);

        } else if (title.startsWith("接下来") || title.startsWith("然后") || title.startsWith("下一步")) {
            currentTime.add(Time.MINUTE, 30);

            int startminute = currentTime.getMinute() / 30 * 30;
            int endminute = currentTime.getMinute() / 30 * 30 + 29;
            title = title.replace("接下来，", "");
            title = title.replace("接下来", "");
            title = title.replace("然后，", "");
            title = title.replace("然后", "");
            title = title.replace("下一步，", "");
            title = title.replace("下一步", "");

            return new TimeCheckResult(currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" +
                    currentTime.getHour() + ":" + startminute + "~" + currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" + currentTime.getHour() + ":" + endminute, title);
        }
        return new TimeCheckResult(1, title);

    }

    //    已经统一转换为xx年xx月xx日xx:xx~xx:xx的格式了
    private TimeCheckResult extractTimestamp(String title) {
        if (TextUtils.isEmpty(title)) {
            return new TimeCheckResult(2, "任务内容不能为空");
        }

        TimeCheckResult timeCheckResult = fillupTimeInTitle(title);
        if (timeCheckResult.code != 1) {
            return timeCheckResult;
        }

        String titleWithNoTime = timeCheckResult.titleWithNoTime;
        String timeTag = timeCheckResult.timeTag;

        if (timeMatch(patternFinal, timeTag)) {
            Pattern r = Pattern.compile(patternFinal);
            Matcher m = r.matcher(timeTag);
            if (m.find()) {
                int startyear = Integer.valueOf(m.group(1));
                int startmonth = Integer.valueOf(m.group(2));
                int startday = Integer.valueOf(m.group(3));
                int starthour = Integer.valueOf(m.group(4));
                int startmiute = Integer.valueOf(m.group(5)) / 30 * 30;//四舍五入到最近的30分钟

                int endyear = Integer.valueOf(m.group(6));
                int endmonth = Integer.valueOf(m.group(7));
                int endday = Integer.valueOf(m.group(8));
                int endhour = Integer.valueOf(m.group(9));
                int endminute = Integer.valueOf(m.group(10));
                if (endminute == 30) {
                    endminute = 29;
                }
                if (endminute != 0) {
                    endminute = endminute / 30 * 30 + 29; //0就是0，20就是29，40就是59
                }

                Time starttime = Time.getCurrentTime();
                starttime.set(0, startmiute, starthour, startday, startmonth - 1, startyear);
                mStartTime = starttime.toMillis();

                Time endtime = Time.getCurrentTime();
                endtime.set(0, endminute, endhour, endday, endmonth - 1, endyear);

                mEndTime = endtime.toMillis();

//                如果分钟是0,29,30,59,则去掉前面所有的时间部分
//                否则只去掉年月日
                Set<Integer> minuteArray = Set.of(0, 29, 30, 59);
                if (minuteArray.contains(startmiute) && minuteArray.contains(endminute)) {
                    title = timeTag.replaceAll(patternFinal, "") + titleWithNoTime.replaceAll("^，", "");
                } else {
                    title = timeTag.replaceAll(pattern6, "") + titleWithNoTime;
                }
            }
        } else {
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
        TimeCheckResult res = new TimeCheckResult(1, title);
        res.isAllDay = timeCheckResult.isAllDay;
        return res;
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


    public void doOnClick(){
        if (true) {
            String title = mEventTitle.getText().toString().trim();

            if (timeMatch(patternmulti1, title)) {
//                创建重复的多个活动
                Pattern r = Pattern.compile(patternmulti1);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    int startmonth = Integer.parseInt(m.group(1));
                    int startday = Integer.parseInt(m.group(2));
                    int endmonth = Integer.parseInt(m.group(3));
                    int endday = Integer.parseInt(m.group(4));

                    Time starttime = Time.getCurrentTime();
                    starttime.set(0, 0, 0, startday, startmonth - 1, starttime.getYear());

                    Time endtime = Time.getCurrentTime();
                    if (startmonth <= endmonth) {
                        endtime.set(0, 0, 1, endday, endmonth - 1, starttime.getYear());
                    } else {
//                        跨年，比如12-20日到1-5日
                        endtime.set(0, 0, 1, endday, endmonth - 1, endtime.getYear() + 1);
                    }
                    try {
                        if (DateUtils.getDiffDays(starttime.getYear(), startmonth, startday, endtime.getYear(), endmonth, endday) > 60) {
                            Toast.makeText(getActivity(), String.format("多任务最长持续2个月，可临期再续"), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    int succcount = 0;
                    String maintitle = title.replaceAll(patternmulti1, "");
                    while (starttime.compareTo(endtime) < 0) {
                        title = String.format("%s年%s月%s日", starttime.getYear(), starttime.getMonth() + 1, starttime.getDay()) + maintitle;
                        TimeCheckResult timeCheckResult = extractTimestamp(title);
                        if (timeCheckResult.code != 1) {
                            dismiss();
                            Toast.makeText(getActivity(), timeCheckResult.titleWithNoTime, Toast.LENGTH_LONG).show();
                            return;
                        }
                        title = timeCheckResult.titleWithNoTime;
                        mModel.mStart = mStartTime;
                        mModel.mEnd = mEndTime;
                        mModel.mTitle = title;
                        mModel.mAllDay = false;
                        mModel.mCalendarId = mCalendarId;
                        mModel.mOwnerAccount = mCalendarOwner;
                        boolean b = mEditEventHelper.saveEvent(mModel, null, 0);
                        if (b) {
//                            保存记录
                            succcount++;
                        }
                        starttime.add(Time.YEAR_DAY, 1);
                    }
                    if (succcount > 0) {
                        dismiss();
                        Toast.makeText(getActivity(), String.format("成功创建%s条任务", succcount), Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (timeMatch(patternmulti2, title)) {
//                创建重复的多个活动
                Pattern r = Pattern.compile(patternmulti2);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    int startmonth = Integer.parseInt(m.group(1));
                    int startday = Integer.parseInt(m.group(2));
                    int endday = Integer.parseInt(m.group(3));


                    if (endday <= startday) {
                        Toast.makeText(getActivity(), String.format("结束日期必须大于开始日期"), Toast.LENGTH_LONG).show();
                        return;
                    }

                    Time starttime = Time.getCurrentTime();
                    starttime.set(0, 0, 0, startday, startmonth - 1, starttime.getYear());
                    Time endtime = Time.getCurrentTime();
                    endtime.set(0, 0, 1, endday, startmonth - 1, starttime.getYear());
                    int succcount = 0;
                    String maintitle = title.replaceAll(patternmulti2, "");
                    while (starttime.compareTo(endtime) < 0) {
                        title = String.format("%s年%s月%s日", starttime.getYear(), starttime.getMonth() + 1, starttime.getDay()) + maintitle;
                        TimeCheckResult timeCheckResult = extractTimestamp(title);
                        if (timeCheckResult.code != 1) {
                            dismiss();
                            Toast.makeText(getActivity(), timeCheckResult.titleWithNoTime, Toast.LENGTH_LONG).show();
                            return;
                        }
                        title = timeCheckResult.titleWithNoTime;
                        mModel.mStart = mStartTime;
                        mModel.mEnd = mEndTime;
                        mModel.mTitle = title;
                        mModel.mAllDay = false;
                        mModel.mCalendarId = mCalendarId;
                        mModel.mOwnerAccount = mCalendarOwner;
                        boolean b = mEditEventHelper.saveEvent(mModel, null, 0);
                        if (b) {
                            succcount++;
                        }
                        starttime.add(Time.YEAR_DAY, 1);
                    }
                    if (succcount > 0) {
                        dismiss();
                        Toast.makeText(getActivity(), String.format("成功创建%s条任务", succcount), Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (timeMatch(pattern1, title) && isMultiTask(title)) {
//                隔天的任务,比如22:00-7:00点睡觉
                Pattern r = Pattern.compile(pattern1);
                Matcher m = r.matcher(title);
                if (m.find()) {
                    String starthour = m.group(1);
                    String startmiute = m.group(2);
                    String endhour = m.group(3);
                    String endminute = m.group(4);
                    String maintitle = title.replaceAll(pattern1, "");
                    Time currentTime = Time.getCurrentTime();
                    String title1 = currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" + starthour + ":" + startmiute + "~" + "23:59" + maintitle;
                    currentTime.add(Time.YEAR_DAY, 1);
                    String title2 = currentTime.getYear() + "年" + (currentTime.getMonth() + 1) + "月" + currentTime.getDay() + "日" + "00:00~" + endhour + ":" + endminute + maintitle;
                    String[] titles = new String[]{title1, title2};
                    int succcount = 0;
                    for (String t : titles) {
                        TimeCheckResult timeCheckResult = extractTimestamp(t);
                        if (timeCheckResult.code != 1) {
                            dismiss();
                            Toast.makeText(getActivity(), timeCheckResult.titleWithNoTime, Toast.LENGTH_LONG).show();
                            return;
                        }
                        title = timeCheckResult.titleWithNoTime;
                        mModel.mStart = mStartTime;
                        mModel.mEnd = mEndTime;
                        mModel.mTitle = title;
                        mModel.mAllDay = false;
                        mModel.mCalendarId = mCalendarId;
                        mModel.mOwnerAccount = mCalendarOwner;
                        boolean b = mEditEventHelper.saveEvent(mModel, null, 0);
                        if (b) {
                            succcount++;
                        }
                    }
                    if (succcount > 0) {
                        Toast.makeText(getActivity(), String.format("成功创建%s条任务", succcount), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), "创建任务失败", Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                }
            } else {
                TimeCheckResult timeCheckResult = extractTimestamp(title);
                if (timeCheckResult.code != 1) {
                    dismiss();
                    Toast.makeText(getActivity(), timeCheckResult.titleWithNoTime, Toast.LENGTH_LONG).show();
                    return;
                }

                int succcount = 0;
                while (mStartTime < mEndTime) {
                    EventEditUtils.SettingCheckInput settingCheckInput = new EventEditUtils.SettingCheckInput(mStartTime,mEndTime);
                    EventEditUtils.SettingCheckResult checkSettingResult = EventEditUtils.checkTime(mContext,settingCheckInput);
                    if (!timeCheckResult.isAllDay && !checkSettingResult.getResultType().equals(EventEditUtils.ResultType.NORMAL)) {
                        mStartTime += android.text.format.DateUtils.MINUTE_IN_MILLIS * 30;
                        Toast.makeText(getActivity(), checkSettingResult.getResultType().getDesc(), Toast.LENGTH_LONG).show();
                        continue;
                    }
                    mStartTime = checkSettingResult.getStartTime();
                    mEndTime = checkSettingResult.getEndTime();
                    
                    title = timeCheckResult.titleWithNoTime;
                    mModel.mStart = mStartTime;
                    if (timeCheckResult.isAllDay) {
                        mModel.mEnd = mEndTime;
                    } else {
                        mModel.mEnd = mStartTime + android.text.format.DateUtils.MINUTE_IN_MILLIS * 29;
                    }
                    mModel.mTitle = title;
                    mModel.mAllDay = false;
                    mModel.mCalendarId = mCalendarId;
                    mModel.mOwnerAccount = mCalendarOwner;
                    boolean b = mEditEventHelper.saveEvent(mModel, null, 0);
                    if (b) {
                        succcount++;
                    }
                    if (timeCheckResult.isAllDay) {
                        break;
                    }
//                    继续下一个30分钟的分段，如果是6点-7点，这里会增加6:00-6:29,然后6:30-6:59，然后7:00等于endTime，不会创建7:00-7:29的任务
                    mStartTime += android.text.format.DateUtils.MINUTE_IN_MILLIS * 30;
                }

                if (succcount == 0) {
                    Toast.makeText(getActivity(), "创建任务失败", Toast.LENGTH_SHORT).show();
                } else if (succcount == 1) {
                    Toast.makeText(getActivity(), "创建活动:"+mModel.mTitle, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getActivity(), String.format("成功创建%s条任务", succcount), Toast.LENGTH_SHORT).show();
                }
                dismiss();

            }
        }
    }

    @Override
    public void onClick(View v) {
        mContext.sendBroadcast(new Intent("com.yourapp.CANCEL_GESTURE"));

    }

    private class TimeCheckResult {
        // 1-正常,2-时间错误
        private int code = 1;
        private String titleWithNoTime = "";

        private boolean isAllDay = false;

        private String timeTag = "";

        public TimeCheckResult() {

        }

        public TimeCheckResult(String timeTag, String title) {
            this.code = 1;
            this.timeTag = timeTag;
            this.titleWithNoTime = title;
        }

        public TimeCheckResult(String timeTag, String title, boolean isAllDay) {
            this(timeTag, title);
            this.isAllDay = isAllDay;
        }


        public TimeCheckResult(int code, String title) {
            this.code = code;
            this.titleWithNoTime = title;
        }

        public TimeCheckResult(int code) {
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

