package com.fc.v2.hmf;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** 测试用：snake_case 列名取实体字段值。 */
final class Property {

    private static final Map<Class<?>, Map<String, Field>> CACHE = new HashMap<>();

    private Property() {
    }

    static Object get(Object bean, String column) {
        if (bean == null) {
            return null;
        }
        String name = camel(column);
        Map<String, Field> fields = CACHE.computeIfAbsent(bean.getClass(), Property::index);
        Field f = fields.get(name);
        if (f == null) {
            throw new IllegalArgumentException("无列: " + column + " (" + name + ") on " + bean.getClass());
        }
        try {
            return f.get(bean);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Field> index(Class<?> type) {
        Map<String, Field> map = new HashMap<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                map.putIfAbsent(f.getName(), f);
            }
            c = c.getSuperclass();
        }
        return map;
    }

    private static String camel(String column) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : column.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
