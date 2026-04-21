package org.qianye.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.qianye.DTO.CallTimeRange;
import org.qianye.DTO.QueueDetailDTO;

import java.util.ArrayList;
import java.util.List;

public class CommonUtil {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("HH:mm");

    public static String buildGroupLockKey(String instanceId, String groupCode, String env) {
        return "group_lock:" + instanceId + ":" + groupCode + ":" + env;
    }

    public static String buildPhoneCacheKey(QueueDetailDTO queue) {
        return "phone_cache:" + queue.getInstanceId() + ":" + queue.getTaskCode() + ":" + queue.getCallee();
    }

    public static String simplifyTimeRange(String timeRange) {
        return timeRange != null ? timeRange : "";
    }

    public static String simplifyTimeRange(CallTimeRange callTimeRange) {
        return callTimeRange != null ? callTimeRange.toString() : "";
    }

    /**
     * 构建重试组编码
     */
    public static String buildRetryGroupCode(String preGroupCode) {
        int retryCount = getGroupRetryCount(preGroupCode);
        return preGroupCode + "_R" + (retryCount + 1);
    }

    /**
     * 获取组的重试次数
     */
    public static int getGroupRetryCount(String queueGroupCode) {
        if (queueGroupCode == null || !queueGroupCode.contains("_R")) {
            return 0;
        }
        try {
            String[] parts = queueGroupCode.split("_R");
            if (parts.length > 1) {
                return Integer.parseInt(parts[parts.length - 1]);
            }
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        return 0;
    }

    /**
     * 将DateTime转换为字符串时间格式 (HH:mm)
     */
    public static String parse(DateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toString(TIME_FORMATTER);
    }

    /**
     * 将时间字符串转换为今天的DateTime对象
     */
    public static DateTime convertToTodayTime(String timing) {
        if (timing == null || timing.isEmpty()) {
            return null;
        }
        try {
            DateTime now = DateTime.now();
            String[] parts = timing.split(":");
            if (parts.length >= 2) {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                return now.withTimeAtStartOfDay().withHourOfDay(hour).withMinuteOfHour(minute);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }

    /**
     * 将列表按指定大小分区
     */
    public static <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        if (list == null || list.isEmpty() || size <= 0) {
            return partitions;
        }
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return partitions;
    }
}
