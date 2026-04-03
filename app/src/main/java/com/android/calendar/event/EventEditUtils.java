package com.android.calendar.event;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.calendar.settings.GeneralPreferences;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class EventEditUtils {

    public static class SettingCheckInput {
        private final long startTime;
        private final long endTime;

        public SettingCheckInput(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }
    }

    // ==============================
    // 【结果类】包含 时间 + 状态 + 描述
    // ==============================
    public static class SettingCheckResult {
        private final long startTime;
        private final long endTime;
        private final ResultType resultType;

        // 构造
        public SettingCheckResult(long startTime, long endTime, ResultType resultType) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.resultType = resultType;
        }

        // Getter
        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public ResultType getResultType() {
            return resultType;
        }
    }

    public enum ResultType {
        NORMAL(0, "正常"),
        IGNORE_PAST_TIME(1, "忽略过去的时间");

        private final int code;
        private final String desc;

        ResultType(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }


    // ==============================
    // 核心检查方法（已改造）
    // ==============================
    public static EventEditUtils.SettingCheckResult checkTime(Context context, EventEditUtils.SettingCheckInput input) {
        long startTime = input.getStartTime();
        long endTime = input.getEndTime();

        SharedPreferences sp = context.getSharedPreferences(
                GeneralPreferences.SHARED_PREFS_NAME,
                Context.MODE_PRIVATE
        );

        String handlePolicy = sp.getString("pref_input_category_handle_before", "0");

        long currentTime = System.currentTimeMillis();

        if (handlePolicy.equals("0")) {
            //正常创建
            return new EventEditUtils.SettingCheckResult(
                    startTime,
                    endTime,
                    EventEditUtils.ResultType.NORMAL
            );
        }else if (handlePolicy.equals("1") ) {
            //忽略过去时间
            if (endTime < currentTime) {
                return new EventEditUtils.SettingCheckResult(
                        startTime,
                        endTime,
                        EventEditUtils.ResultType.IGNORE_PAST_TIME
                );
            }
        }else if (handlePolicy.equals("2") ) {
            //修复特定时间
            if (currentTime > endTime) {
                Calendar startCal = Calendar.getInstance();
                startCal.setTimeInMillis(startTime);
                int startHour = startCal.get(Calendar.HOUR_OF_DAY);

                // 判断：开始时间的小时 < 20
                if (startHour < 14) {
                    startTime = startTime + 36000000L; // +10小时
                    endTime = endTime + 36000000L; // +10小时
                }

                // ============= 3. 正常 =============
                return new EventEditUtils.SettingCheckResult(
                        startTime,
                        endTime,
                        EventEditUtils.ResultType.NORMAL
                );
            }

        }
        // ============= 3. 正常 =============
        return new EventEditUtils.SettingCheckResult(
                startTime,
                endTime,
                EventEditUtils.ResultType.NORMAL
        );
    }
}

