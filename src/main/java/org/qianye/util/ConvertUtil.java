package org.qianye.util;

public class ConvertUtil {
    @SuppressWarnings("unchecked")
    public static <S, T> T convert(S source, Class<T> targetClass) {
        try {
            return targetClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Convert failed", e);
        }
    }
}
