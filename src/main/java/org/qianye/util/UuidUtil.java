package org.qianye.util;

import java.util.UUID;

public class UuidUtil {
    public static String generateShortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
