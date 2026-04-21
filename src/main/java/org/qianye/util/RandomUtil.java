package org.qianye.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {
    public static <T> T chanceSelect(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
