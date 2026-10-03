package com.fc.v2.hmf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;

/**
 * 测试用：把 MyBatis-Plus Wrapper（QueryWrapper/UpdateWrapper）里的等值/不等条件与排序
 * 解析出来，在内存 List 上过滤。核签业务只用到 eq/ne 与 orderByAsc/Desc，按此支持即可。
 */
final class WrapperEval {

    private static final Pattern PARAM_REF = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

    private WrapperEval() {
    }

    /** 按 wrapper 的 eq/ne 条件逐列过滤。 */
    static <T> List<T> filter(List<T> rows, Wrapper<T> wrapper) {
        List<T> out = new ArrayList<>();
        for (T row : rows) {
            if (wrapper == null || matches(row, wrapper)) {
                out.add(row);
            }
        }
        return out;
    }

    static boolean matches(Object row, Wrapper<?> wrapper) {
        if (!(wrapper instanceof AbstractWrapper)) {
            return true;
        }
        MergeSegments expr = ((AbstractWrapper<?, ?, ?>) wrapper).getExpression();
        Map<String, Object> params = ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
        List<ISqlSegment> segs = new ArrayList<>();
        expr.getNormal().forEach(o -> {
            if (o instanceof ISqlSegment) {
                segs.add((ISqlSegment) o);
            }
        });
        // 形态：列、比较符、#{...MPGENVALn}、AND、列、比较符、…
        for (int i = 0; i + 2 < segs.size(); i += 4) {
            String column = segs.get(i).getSqlSegment();
            String op = segs.get(i + 1).getSqlSegment();
            String ref = segs.get(i + 2).getSqlSegment();
            Matcher m = PARAM_REF.matcher(ref);
            if (!m.matches()) {
                continue;
            }
            Object expected = params.get(m.group(1));
            Object actual = Property.get(row, column);
            if ("=".equals(op)) {
                if (!valueEquals(expected, actual)) {
                    return false;
                }
            } else if ("!=".equals(op) || "<>".equals(op)) {
                if (valueEquals(expected, actual)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 库式等值：数值跨类型照值比（Integer 对 Long 与库内 bigint 比较同义），其余照 equals。 */
    private static boolean valueEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (expected instanceof Number && actual instanceof Number) {
            return new java.math.BigDecimal(expected.toString())
                    .compareTo(new java.math.BigDecimal(actual.toString())) == 0;
        }
        return false;
    }

    /** 按 wrapper 的 orderBy 段排序。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> Comparator<T> orderOf(Wrapper<T> wrapper) {
        Comparator<T> cmp = (a, b) -> 0;
        if (!(wrapper instanceof AbstractWrapper)) {
            return cmp;
        }
        MergeSegments expr = ((AbstractWrapper<?, ?, ?>) wrapper).getExpression();
        for (Object o : expr.getOrderBy()) {
            if (!(o instanceof ISqlSegment)) {
                continue;
            }
            String text = ((ISqlSegment) o).getSqlSegment();
            boolean desc = text.toUpperCase().endsWith(" DESC");
            String column = text.replace(" DESC", "").replace(" ASC", "").trim();
            Comparator<T> step = (x, y) -> {
                Object vx = Property.get(x, column);
                Object vy = Property.get(y, column);
                if (vx == null && vy == null) {
                    return 0;
                }
                if (vx == null) {
                    return -1;
                }
                if (vy == null) {
                    return 1;
                }
                return ((Comparable) vx).compareTo(vy);
            };
            if (desc) {
                step = step.reversed();
            }
            cmp = cmp.thenComparing(step);
        }
        return cmp;
    }
}
