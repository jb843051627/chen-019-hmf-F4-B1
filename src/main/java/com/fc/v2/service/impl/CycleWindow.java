package com.fc.v2.service.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/**
 * 出手历窗口径（查单与回写共用这一把尺，不许两处各算各的）。
 *
 * 平常日子被值班表割成两截：班内 08:00（含）至 20:00（不含）才是出手时间；
 * 夜里整段不算出手时间。法定节假日那一整天整段不算出手时间，手里有已到期的也先压着。
 * 落在窗口边界那一刻听这一口的回话：08:00:00 整动手，20:00:00 整收手。
 *
 * 年终封存那半个月（12/16 至 12/31，含首尾）不照上面班内/班外/节假日的分法走，
 * 单另起封存专用时段：期内整日都可出手，夜里、法定节假日都不压。
 * 封存期内同户同日只认头一回落定的结果，去重发生在服务层，历窗只管此刻开不开。
 *
 * @author fuce
 * @date 2026-10-02
 */
class CycleWindow {

    static final TimeZone ZONE = TimeZone.getTimeZone("GMT+8");

    /** 班内起：08:00（含） */
    static final int SHIFT_FROM_HOUR = 8;
    /** 班内止：20:00（不含） */
    static final int SHIFT_TO_HOUR = 20;

    /** 年终封存期：12/16（含）至 12/31（含） */
    static final int SEAL_MONTH = Calendar.DECEMBER;
    static final int SEAL_FROM_DAY = 16;
    static final int SEAL_TO_DAY = 31;

    /** 法定节假日（yyyy-MM-dd），随当年放假安排另册填，本类不内置。 */
    private final Set<String> legalHolidays = new HashSet<>();

    void setLegalHolidays(Set<String> days) {
        this.legalHolidays.clear();
        if (days != null) {
            this.legalHolidays.addAll(days);
        }
    }

    boolean isLegalHoliday(Date at) {
        return this.legalHolidays.contains(dayKey(at));
    }

    /** 年终封存那半个月（按 GMT+8 的月日认）。 */
    boolean inYearEndSeal(Date at) {
        Calendar c = calendar(at);
        return c.get(Calendar.MONTH) == SEAL_MONTH
                && c.get(Calendar.DAY_OF_MONTH) >= SEAL_FROM_DAY
                && c.get(Calendar.DAY_OF_MONTH) <= SEAL_TO_DAY;
    }

    /** 此刻是不是出手时间。封存期整日开；平常日子要班内且不是法定节假日。 */
    boolean openAt(Date at) {
        if (at == null) {
            return false;
        }
        if (inYearEndSeal(at)) {
            return true;
        }
        if (isLegalHoliday(at)) {
            return false;
        }
        Calendar c = calendar(at);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= SHIFT_FROM_HOUR && hour < SHIFT_TO_HOUR;
    }

    /** 压着不开口时给页面/痕迹的缘由。 */
    String closedReason(Date at) {
        if (inYearEndSeal(at)) {
            return "";
        }
        if (isLegalHoliday(at)) {
            return "法定节假日整段不算出手时间，先压着等下一轮开口";
        }
        Calendar c = calendar(at);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour < SHIFT_FROM_HOUR || hour >= SHIFT_TO_HOUR) {
            return "夜里（班外）整段不算出手时间，先压着等下一轮开口";
        }
        return "";
    }

    /** 是否同一个封存日（同日同户去重用；只在封存期内有意义）。 */
    boolean sameSealDay(Date a, Date b) {
        return dayKey(a).equals(dayKey(b));
    }

    static String dayKey(Date at) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(ZONE);
        return sdf.format(at);
    }

    static Date parseDay(String day) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(ZONE);
            return sdf.parse(day);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Calendar calendar(Date at) {
        Calendar c = Calendar.getInstance(ZONE);
        c.setTime(at);
        return c;
    }
}
