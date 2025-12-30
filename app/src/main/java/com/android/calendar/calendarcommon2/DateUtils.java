package com.android.calendar.calendarcommon2;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DateUtils {

    public static int getDiffDays(int startYear, int startMonth, int startDay,
                                  int endYear, int endMonth, int endDay) {
        // 1. 创建起始日期的 LocalDate 对象（LocalDate 月份是 1-based，无需转换）
        LocalDate startDate = LocalDate.of(startYear, startMonth, startDay);
        // 2. 创建结束日期的 LocalDate 对象
        LocalDate endDate = LocalDate.of(endYear, endMonth, endDay);

        // 3. 计算两个日期的天数差（ChronoUnit.DAYS.between 返回 long 类型）
        // between(start, end)：end 在 start 之后返回正数，反之返回负数，取绝对值得到总天数
        long diffDays = Math.abs(ChronoUnit.DAYS.between(startDate, endDate));

        // 4. 转换为 int 返回（日常日期差不会超出 int 范围）
        return (int) diffDays;
    }
}
