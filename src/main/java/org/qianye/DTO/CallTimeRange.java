package org.qianye.DTO;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Date;
import java.util.List;

@Data
@ToString
@Slf4j
public class CallTimeRange {
    private List<CallTime> callTimes;
    private String timeZone;
    private String taskCode;
    private Date taskStartTime;
    private Date taskEndTime;

    public CallInfo calCallTimeInfo() {
        CallInfo callInfo = new CallInfo();
        callInfo.setInCurrentCallTimeRange(isInCallTimeRange());
        callInfo.setInTaskTimeRange(isInTaskTimeRange());
        return callInfo;
    }

    private boolean isInTaskTimeRange() {
        if (taskStartTime == null || taskEndTime == null) {
            return false;
        }
        Date now = new Date();
        return now.after(taskStartTime) && now.before(taskEndTime);
    }

    public boolean isInCallTimeRange(DateTime dateTime) {
        if (!isInTaskTimeRange()) {
            return false;
        }
        // 提取时间部分（忽略日期）
        long timeOfDay = dateTime.getMillisOfDay();
        for (CallTime callTime : callTimes) {
            // 提取开始和结束时间的时间部分
            long startTimeOfDay = callTime.getStartTime().getMillisOfDay();
            long endTimeOfDay = callTime.getEndTime().getMillisOfDay();
            // 处理跨天的情况（例如 23:00 到 02:00）
            if (startTimeOfDay > endTimeOfDay) {
                // 跨天时间段，如 23:00-02:00
                if (timeOfDay >= startTimeOfDay || timeOfDay <= endTimeOfDay) {
                    return true;
                }
            } else {
                // 不跨天时间段，如 09:00-17:00
                if (timeOfDay >= startTimeOfDay && timeOfDay <= endTimeOfDay) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 呼叫时间是否在指定时间范围内（仅比较时间部分，忽略日期）
     *
     * @return 如果时间在范围内返回true，否则返回false
     */
    public boolean isInCallTimeRange() {
        DateTime now = DateTime.now();
        return isInCallTimeRange(now);
    }

    @Data
    public static class CallTime {
        private DateTime startTime;
        private DateTime endTime;

        /**
         * 设置开始时间，支持整数格式（如 900 表示 09:00）或字符串格式
         */
        @JSONField(name = "startTime")
        public void setStartTime(Object time) {
            this.startTime = parseTimeValue(time);
        }

        /**
         * 设置结束时间，支持整数格式（如 2359 表示 23:59）或字符串格式
         */
        @JSONField(name = "endTime")
        public void setEndTime(Object time) {
            this.endTime = parseTimeValue(time);
        }

        /**
         * 解析时间值，支持多种格式
         */
        private DateTime parseTimeValue(Object time) {
            if (time == null) {
                return null;
            }
            if (time instanceof Integer) {
                return parseTimeInt((Integer) time);
            }
            if (time instanceof String) {
                String timeStr = (String) time;
                // 处理带方括号时区的格式，如 "2026-03-06T23:59:00[Asia/Shanghai]"
                if (timeStr.contains("[")) {
                    timeStr = timeStr.substring(0, timeStr.indexOf('['));
                }
                // 使用 ISODateTimeFormat 解析
                return ISODateTimeFormat.dateTimeParser().parseDateTime(timeStr);
            }
            if (time instanceof DateTime) {
                return (DateTime) time;
            }
            return null;
        }

        /**
         * 将整数时间转换为 DateTime
         * @param timeInt 整数时间，如 900 表示 09:00，2359 表示 23:59
         * @return 今天的指定时间的 DateTime
         */
        private DateTime parseTimeInt(int timeInt) {
            int hour = timeInt / 100;
            int minute = timeInt % 100;
            return new DateTime().withTimeAtStartOfDay().withHourOfDay(hour).withMinuteOfHour(minute);
        }
    }

    @Data
    public static class CallInfo {
        boolean inTaskTimeRange;
        boolean inCurrentCallTimeRange;
    }
}