package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfSpendLine;
import com.fc.v2.service.impl.THmfSpendLineServiceImpl;

/**
 * 《支出审定额线口径》逐条核对：
 * 名单只认窗内（未启用不提早办、交了棒不翻出、停用不露头、启用当日含、交棒当日不含）、
 * 名单定序钉死重进重出一个样、单规则定位与名单同一把尺、压着线计入上一层、
 * 缺线条目不搅断整轮、没有条目可处置回零不抛错、可用判定与计数跟名单走。
 */
class SpendLineRuleTest {

    private static final int CURRENT = 0;
    private static final int STOPPED = 1;

    private THmfSpendLineServiceImpl service;
    private FakeSpendLineMapper lines;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfSpendLineServiceImpl();
        this.lines = new FakeSpendLineMapper();
        Field f = THmfSpendLineServiceImpl.class.getDeclaredField("hmfSpendLineMapper");
        f.setAccessible(true);
        f.set(service, lines);
    }

    private static Date t(String text) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+8"));
            return sdf.parse(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private THmfSpendLine rule(String code, int priority, String th1, String th2, String th3,
                               Date effStart, Date effEnd, int status) {
        THmfSpendLine r = new THmfSpendLine();
        r.setRuleCode(code);
        r.setRuleName(code + "线");
        r.setPriority(priority);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(effStart);
        r.setEffEnd(effEnd);
        r.setStatus(status);
        r.setDelFlag(0);
        lines.insert(r);
        return r;
    }

    private THmfSpendLine current(String code, int priority, String th1, String th2, String th3) {
        return rule(code, priority, th1, th2, th3,
                t("2026-01-01 00:00:00"), null, CURRENT);
    }

    // ---- 名单只认窗内：不提早办、不翻出、不露头 ----

    @Test
    void listAvailable_onlyInWindowCurrentRows_andStableOrder() {
        Date at = t("2026-10-09 09:00:00");
        current("RL-B", 5, "100", "200", "300");
        current("RL-A", 9, "90", "144", "300");
        current("RL-C", 5, "50", "100", "150");
        // 没启用的不提早办
        rule("RL-FUTURE", 99, "1", "2", "3", t("2026-12-01 00:00:00"), null, CURRENT);
        // 交了棒的不再被翻出来当可用的办
        rule("RL-OLD", 99, "1", "2", "3", t("2025-01-01 00:00:00"), t("2026-01-01 00:00:00"), CURRENT);
        // 停了用的不露头
        rule("RL-STOP", 99, "1", "2", "3", null, null, STOPPED);

        List<THmfSpendLine> avail = service.listAvailable(at);
        assertEquals(3, avail.size());
        // 定序钉死：让线顺位降序、代号降序
        assertEquals("RL-A", avail.get(0).getRuleCode());
        assertEquals("RL-C", avail.get(1).getRuleCode());
        assertEquals("RL-B", avail.get(2).getRuleCode());
        // 重进重出一个样，不靠库回话先后的运气
        List<THmfSpendLine> again = service.listAvailable(at);
        assertEquals(avail.get(0).getId(), again.get(0).getId());
        assertEquals(avail.get(1).getId(), again.get(1).getId());
        assertEquals(avail.get(2).getId(), again.get(2).getId());
    }

    @Test
    void boundary_startDayInclusive_handoverDayExclusive() {
        Date at = t("2026-10-09 09:00:00");
        rule("RL-WIN", 1, "100", "200", "300",
                t("2026-10-09 09:00:00"), t("2026-10-20 09:00:00"), CURRENT);
        // 压着启用那一刻：算可用，不漏在旁边
        assertEquals(1, service.listAvailable(at).size());
        // 压着交棒那一刻：已交棒，不算可用
        assertEquals(0, service.listAvailable(t("2026-10-20 09:00:00")).size());
        // 启用前一刻：不提早办
        assertEquals(0, service.listAvailable(t("2026-10-09 08:59:59")).size());
    }

    // ---- 求值：没有条目回零不抛错；单规则定位与名单同一把尺 ----

    @Test
    void noRuleAtAll_returnsZero_notThrow() {
        Date at = t("2026-10-09 09:00:00");
        assertEquals(0, service.evaluate("RL-NONE", new BigDecimal("100"), at), "查无此线本该回零，不许抛错");
        assertEquals(0, service.evaluateTop(new BigDecimal("100"), at), "没有条目可处置本该回零，不许抛错");
        assertEquals(0, service.countAvailable(at));
        assertTrue(service.listAvailable(at).isEmpty());
    }

    @Test
    void evaluate_picksTheVersionInWindow_sameCodeTwoVersions() {
        // 同代号两版：旧版 10/1 交棒，新版 10/1 接上——压着点到期的那版不被漏在旁边
        rule("RL-V", 1, "100", "200", "300", t("2026-01-01 00:00:00"), t("2026-10-01 00:00:00"), CURRENT);
        rule("RL-V", 1, "500", "800", "1000", t("2026-10-01 00:00:00"), null, CURRENT);
        BigDecimal input = new BigDecimal("150");
        // 旧版窗内：150 落旧版二线（100≤150<200）→ 2 档
        assertEquals(2, service.evaluate("RL-V", input, t("2026-09-15 09:00:00")));
        // 交棒当日那一刻：新版已接上（150<500）→ 1 档
        assertEquals(1, service.evaluate("RL-V", input, t("2026-10-01 00:00:00")));
        assertEquals(1, service.evaluate("RL-V", input, t("2026-10-09 09:00:00")));
    }

    @Test
    void evaluateTop_highestPrioritySpeaks_reenterSameResult() {
        Date at = t("2026-10-09 09:00:00");
        // 录入先后与顺位无关：后录的顺位高，该它说话
        current("RL-LOW", 1, "10", "20", "30");
        current("RL-HIGH", 9, "1000", "2000", "3000");
        BigDecimal input = new BigDecimal("15");
        // 低顺位线判 2 档、高顺位线判 1 档：照顺位高的回话
        assertEquals(1, service.evaluateTop(input, at));
        // 退出、重新进入，得出的结果和上次一个样
        assertEquals(1, service.evaluateTop(input, at));
        assertEquals(1, service.evaluateTop(input, at));
    }

    @Test
    void negativeOrMissingInput_returnsZero() {
        Date at = t("2026-10-09 09:00:00");
        current("RL-A", 1, "100", "200", "300");
        assertEquals(0, service.evaluate("RL-A", new BigDecimal("-1"), at), "金额没有负的，越界回零");
        assertEquals(0, service.evaluateTop(new BigDecimal("-0.01"), at));
        assertEquals(0, service.evaluate("RL-A", null, at));
        assertEquals(0, service.evaluate(null, new BigDecimal("1"), at));
        assertEquals(0, service.evaluateTop(null, at));
    }

    // ---- 折档：压着线计入上一层；缺线不搅断整轮 ----

    @Test
    void equalToLimit_countsIntoUpperTier() {
        Date at = t("2026-10-09 09:00:00");
        current("RL-BAND", 1, "90", "144", "300");
        assertEquals(1, service.evaluate("RL-BAND", new BigDecimal("89.99"), at));
        assertEquals(2, service.evaluate("RL-BAND", new BigDecimal("90"), at), "压着低界计入上一层");
        assertEquals(2, service.evaluate("RL-BAND", new BigDecimal("143.99"), at));
        assertEquals(3, service.evaluate("RL-BAND", new BigDecimal("144"), at), "压着中界计入上一层");
        assertEquals(4, service.evaluate("RL-BAND", new BigDecimal("300"), at), "压着高界计入上一层");
        assertEquals(4, service.evaluate("RL-BAND", new BigDecimal("99999"), at));
    }

    @Test
    void missingThresholdLine_doesNotBreakEvaluation() {
        Date at = t("2026-10-09 09:00:00");
        // 缺一根线：缺的那根不卡，一条缺值不搅断整轮
        current("RL-GAP", 1, "90", null, "300");
        assertEquals(1, service.evaluate("RL-GAP", new BigDecimal("50"), at));
        assertEquals(3, service.evaluate("RL-GAP", new BigDecimal("100"), at));
        assertEquals(4, service.evaluate("RL-GAP", new BigDecimal("500"), at));
        // 三线全缺：判不出来回零，不许抛错
        current("RL-EMPTY", 1, null, null, null);
        assertEquals(0, service.evaluate("RL-EMPTY", new BigDecimal("50"), at));
    }

    // ---- 可用判定与计数：跟名单同一把尺 ----

    @Test
    void usableAndCount_followSameRuleAsList() {
        Date at = t("2026-10-09 09:00:00");
        THmfSpendLine ok = current("RL-OK", 1, "100", "200", "300");
        THmfSpendLine future = rule("RL-F", 1, "1", "2", "3", t("2027-01-01 00:00:00"), null, CURRENT);
        THmfSpendLine stopped = rule("RL-S", 1, "1", "2", "3", null, null, STOPPED);

        assertTrue(service.usable(ok.getId(), at));
        assertFalse(service.usable(future.getId(), at), "没启用的不算可用");
        assertFalse(service.usable(stopped.getId(), at), "停了用的不算可用");
        assertFalse(service.usable(404L, at));
        assertFalse(service.usable(ok.getId(), null));
        assertEquals(1, service.countAvailable(at), "计数与名单同一把尺");
        assertEquals(service.listAvailable(at).size(), service.countAvailable(at));
    }
}
