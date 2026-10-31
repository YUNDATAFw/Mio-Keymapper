package io.takamiya.mio.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class RelativeTimeUtil {

    private static final DateTimeFormatter INPUT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MD_FMT   = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter YMD_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String toRelativeTime(String dateTimeStr) {
        LocalDateTime time = LocalDateTime.parse(dateTimeStr, INPUT_FMT);
        LocalDateTime now  = LocalDateTime.now();

        if (!time.isBefore(now)) {
            return time.format(YMD_FMT);          // 未来时间，直接返回日期
        }

        long seconds = ChronoUnit.SECONDS.between(time, now);
        if (seconds < 60) {
            return "刚刚";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "小时前";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "天前";
        }
        if (days == 7) {
            return "一周前";
        }
        // 超过7天：满一年显示 年-月-日，否则显示 月-日
        if (ChronoUnit.YEARS.between(time, now) >= 1) {
            return time.format(YMD_FMT);
        }
        return time.format(MD_FMT);
    }

    public static String toRelativeTimeSafe(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) return "";
        try {
            return toRelativeTime(dateTimeStr);
        } catch (Exception e) {
            return dateTimeStr;
        }
    }
}