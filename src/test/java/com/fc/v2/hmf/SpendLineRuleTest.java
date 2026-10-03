package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THmfSpendLine;
import com.fc.v2.service.impl.THmfSpendLineServiceImpl;

/**
 * 《支出审定额线口径》逐条核对：
 * 生效窗（启用含当日、交棒不含当日、空端放开）、停用/删过的不进门、
 * 没到启用日不许提早用、缺规则缺阈值回 0 不搅断、
 * 列表/计数/单条可用/单规则定位同一把尺、优先级与同号新版本次序。
 */
class SpendLineRuleTest {

    private THmfSpendLineServiceImpl service;
    private FakeSpendLineMapper lines;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfSpendLineServiceImpl();
        this.lines = new FakeSpendLineMapper();
        inject("hmfSpendLineMapper", lines);
    }

    private void inject(String name, Object value) throws Exception {
        Class<?> c = THmfSpendLineServiceImpl.class;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(service, value);
                return;
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Date t(String text) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private THmfSpendLine line(String code, String th1, String th2, String th3,
                               String start, String end, int priority, int status) {
        THmfSpendLine r = new THmfSpendLine();
        r.setRuleCode(code);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(start == null ? null : t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        lines.insert(r);
        return r;
    }

    // ---- 生效窗：启用含、交棒不含，空端放开 ----

    @Test
    void effStartBoundary_inclusive_effEndBoundary_exclusive() {
        line("R", "100", "200", "300", "2026-01-01 00:00:00", "2026-06-01 00:00:00", 1, 0);
        // 启用前一刻：不许提早用
        assertEquals(0, service.evaluate("R", new BigDecimal("50"), t("2025-12-31 23:59:59")));
        assertFalse(service.usable(idOf("R"), t("2025-12-31 23:59:59")));
        // 恰好落在启用那一刻：能用
        assertEquals(1, service.evaluate("R", new BigDecimal("50"), t("2026-01-01 00:00:00")));
        // 交棒前一刻：还算它的
        assertEquals(2, service.evaluate("R", new BigDecimal("150"), t("2026-05-31 23:59:59")));
        // 交棒那一刻整：已不说话
        assertEquals(0, service.evaluate("R", new BigDecimal("150"), t("2026-06-01 00:00:00")));
        assertFalse(service.usable(idOf("R"), t("2026-06-01 00:00:00")));
    }

    @Test
    void nullEndpoints_meansAlwaysStarted_neverExpires() {
        line("R", "100", null, null, null, null, 1, 0);
        assertEquals(1, service.evaluate("R", new BigDecimal("1"), t("2000-01-01 00:00:00")));
        assertEquals(1, service.evaluate("R", new BigDecimal("1"), t("2099-12-31 23:59:59")));
    }

    // ---- 停用/删过：不进门 ----

    @Test
    void stoppedAndDeletedLines_neverAvailable() {
        line("ON", "100", null, null, null, null, 1, 0);
        line("OFF", "100", null, null, null, null, 1, 1);
        THmfSpendLine deleted = line("DEL", "100", null, null, null, null, 1, 0);
        deleted.setDelFlag(1);
        lines.updateById(deleted);
        Date at = t("2026-05-01 00:00:00");

        assertEquals(1, service.listAvailable(at).size());
        assertEquals("ON", service.listAvailable(at).get(0).getRuleCode());
        assertEquals(1, service.countAvailable(at));
        assertEquals(0, service.evaluate("OFF", new BigDecimal("1"), at));
        assertEquals(0, service.evaluate("DEL", new BigDecimal("1"), at));
        assertFalse(service.usable(idOf("OFF"), at));
    }

    // ---- 缺值：回 0，不抛错 ----

    @Test
    void noRuleOrNoThresholds_returnsZero_notThrows() {
        Date at = t("2026-05-01 00:00:00");
        assertEquals(0, service.evaluate("NOBODY", new BigDecimal("1"), at));
        line("EMPTY", null, null, null, null, null, 1, 0);
        assertEquals(0, service.evaluate("EMPTY", new BigDecimal("1"), at));
        assertEquals(0, service.evaluate(" ", new BigDecimal("1"), at));
        assertEquals(0, service.evaluate("R", null, at));
        assertEquals(0, service.evaluate("R", new BigDecimal("1"), null));
        assertEquals(0, service.evaluateTop(new BigDecimal("1"), null));
        assertEquals(0, service.evaluateTop(new BigDecimal("-1"), at));
        assertEquals(0, service.evaluateTop(new BigDecimal("100.01"), at));
    }

    // ---- 分档：等于上限取上一层 ----

    @Test
    void tiers_equalThresholdGoesUpper_aboveTopIsFour() {
        line("R", "90", "144", "300", null, null, 1, 0);
        Date at = t("2026-05-01 00:00:00");
        assertEquals(1, service.evaluate("R", new BigDecimal("90"), at));
        assertEquals(2, service.evaluate("R", new BigDecimal("90.01"), at));
        assertEquals(2, service.evaluate("R", new BigDecimal("144"), at));
        assertEquals(3, service.evaluate("R", new BigDecimal("300"), at));
        assertEquals(4, service.evaluate("R", new BigDecimal("300.01"), at));
    }

    // ---- 列表/计数/单条/定位：同一把尺，一处排序 ----

    @Test
    void listCountUsableEvaluate_allUseSameRuler() {
        Date inside = t("2026-05-01 00:00:00");
        THmfSpendLine a = line("A", "1000", null, null, null, null, 1, 0);
        THmfSpendLine b = line("B", "10", "1000", null, null, null, 5, 0);
        List<Long> availableIds = service.listAvailable(inside).stream()
                .map(THmfSpendLine::getId).collect(Collectors.toList());
        // 优先级高的 B 排前头
        assertEquals(b.getId(), availableIds.get(0));
        assertEquals(2, service.countAvailable(inside));
        assertTrue(service.usable(a.getId(), inside));
        assertTrue(service.usable(b.getId(), inside));
        // 输入 50：按高优先级 B 折到第 2 档，不被低优先级 A 的宽松线盖掉
        assertEquals(2, service.evaluateTop(new BigDecimal("50"), inside));
    }

    @Test
    void sameCodeTwoVersions_higherPriorityOrLaterOneSpeaks() {
        // 同号两版：老线宽松（1 档兜到 1000），新线严（10 以内才 1 档）
        line("R", "1000", null, null, "2025-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        line("R", "10", null, null, "2026-01-01 00:00:00", null, 1, 0);
        // 新线启用前听老线
        assertEquals(1, service.evaluate("R", new BigDecimal("50"), t("2025-06-01 00:00:00")));
        // 交棒后听新线
        assertEquals(4, service.evaluate("R", new BigDecimal("50"), t("2026-02-01 00:00:00")));
    }

    @Test
    void listEmptyRound_returnsEmptyCountZero() {
        assertEquals(0, service.listAvailable(t("2026-05-01 00:00:00")).size());
        assertEquals(0, service.countAvailable(t("2026-05-01 00:00:00")));
        assertEquals(0, service.evaluateTop(new BigDecimal("50"), t("2026-05-01 00:00:00")));
    }

    private Long idOf(String code) {
        return lines.all().stream().filter(l -> code.equals(l.getRuleCode()))
                .map(THmfSpendLine::getId).findFirst().orElse(null);
    }
}
