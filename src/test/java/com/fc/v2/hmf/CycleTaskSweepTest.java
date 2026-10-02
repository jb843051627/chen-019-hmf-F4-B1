package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfArch;
import com.fc.v2.model.auto.THmfCycleLog;
import com.fc.v2.model.auto.THmfCycleTask;
import com.fc.v2.model.custom.CycleSweep;
import com.fc.v2.service.CycleNoticeSender;
import com.fc.v2.service.impl.THmfCycleTaskServiceImpl;

/**
 * 《结息催交照日子走》逐条核对：
 * 挑选只看到期（查单/回写同一把尺、止点钉死不凑批、只提前期可商量、边界那一刻）、
 * 班内/班外/节假日压着、年终封存单另两段且同户同日只认头一笔、办结落两笔、
 * 催交站内+短信两路各记一笔谁也顶不了谁、迁出/失联当场催不动不掐整轮、
 * 逾期撤回转催不动但不盖原定日子与完成刻、一轮三数同次同源可逐条点档、
 * 空轮正常收尾不告警、同秒两笔各算各的、runOnce 旧签名不动。
 */
class CycleTaskSweepTest {

    private static final int WAIT = 0;
    private static final int DONE = 1;
    private static final int FAIL = 2;

    private static final int BIZ_INTEREST = 1;
    private static final int BIZ_URGE = 2;

    private static final int ACT_DONE = 1;
    private static final int ACT_BLOCK = 2;
    private static final int ACT_WITHDRAW = 3;
    private static final int ACT_SEAL_DUP = 4;
    private static final int ACT_HELD = 5;
    private static final int ACT_SEND_STATION = 10;
    private static final int ACT_SEND_SMS = 11;

    private static final long DAY = 24L * 60 * 60 * 1000;

    private THmfCycleTaskServiceImpl service;
    private FakeCycleTaskMapper tasks;
    private FakeCycleLogMapper logs;
    private FakeArchMapper archs;
    private RecordingSender sender;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfCycleTaskServiceImpl();
        this.tasks = new FakeCycleTaskMapper();
        this.logs = new FakeCycleLogMapper();
        this.archs = new FakeArchMapper();
        inject("hmfCycleTaskMapper", tasks);
        inject("hmfCycleLogMapper", logs);
        inject("hmfArchMapper", archs);
        this.sender = new RecordingSender();
        inject("noticeSender", sender);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = THmfCycleTaskServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @SuppressWarnings("unchecked")
    private void holidays(String... days) throws Exception {
        Method m = THmfCycleTaskServiceImpl.class.getDeclaredMethod("registerHolidays", Set.class);
        m.setAccessible(true);
        m.invoke(service, new HashSet<>(Arrays.asList(days)));
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

    private static Date plus(Date d, long ms) {
        return new Date(d.getTime() + ms);
    }

    private static String day(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+8"));
        return sdf.format(d);
    }

    // ---- 底册与条目 ----

    private THmfArch arch(String siteNo, int status, String mobile, Date fillAt) {
        THmfArch a = new THmfArch();
        a.setSiteNo(siteNo);
        a.setStatus(status);
        a.setContactMobile(mobile);
        a.setContactFillAt(fillAt);
        a.setDelFlag(0);
        archs.insert(a);
        return a;
    }

    /** 正常在册、联系人一季度内填过的底册。 */
    private void readyArch(String siteNo, Date now) {
        arch(siteNo, 0, "1390000000" + Math.abs(siteNo.hashCode() % 10), plus(now, -10 * DAY));
    }

    private THmfCycleTask interest(String no, String siteNo, Date due) {
        return task(no, siteNo, BIZ_INTEREST, due, null);
    }

    private THmfCycleTask urge(String no, String siteNo, Date due) {
        return task(no, siteNo, BIZ_URGE, due, null);
    }

    private THmfCycleTask task(String no, String siteNo, int bizType, Date due, BigDecimal leadDays) {
        THmfCycleTask r = new THmfCycleTask();
        r.setItemNo(no);
        r.setSiteNo(siteNo);
        r.setBizType(bizType);
        r.setDueAt(due);
        r.setAmount(leadDays);
        r.setTplCode(bizType == BIZ_URGE ? "TPL-URGE" : "TPL-INTEREST");
        r.setContent(siteNo + "年度事宜");
        r.setStatus(WAIT);
        r.setDelFlag(0);
        tasks.insert(r);
        return r;
    }

    private List<THmfCycleLog> logsOf(Long taskId, int action) {
        return logs.all().stream()
                .filter(l -> taskId.equals(l.getTaskId()))
                .filter(l -> l.getAction() != null && l.getAction() == action)
                .collect(Collectors.toList());
    }

    private static class RecordingSender implements CycleNoticeSender {
        boolean stationAck = true;
        boolean smsAck = true;
        final List<String> stationCalls = new ArrayList<>();
        final List<String> smsCalls = new ArrayList<>();

        @Override
        public boolean sendStationMessage(String siteNo, String text) {
            stationCalls.add(siteNo + "|" + text);
            return stationAck;
        }

        @Override
        public boolean sendSms(String mobile, String text) {
            smsCalls.add(mobile + "|" + text);
            return smsAck;
        }
    }

    // ---- 挑选：只看到期，查单/回写同一把尺 ----

    @Test
    void notDueYet_notTouchedAndEmptyRoundIsNormalClose() {
        readyArch("JZ-A", t("2026-10-09 00:00:00"));
        interest("JX-1", "JZ-A", t("2026-10-10 20:00:00"));
        Date at = t("2026-10-09 09:00:00");

        assertTrue(service.listDue(at).isEmpty(), "止点没到，这一轮不碰");
        CycleSweep s = service.sweepCycleTasks(at);
        assertTrue(s.isEmpty(), "一条该动的都没挑着");
        assertEquals("本轮没有到期条目", s.getMessage(), "这是正常收尾，不是失败告警");
        assertEquals(0, s.getWaitCount() + s.getDoneCount() + s.getFailCount());
        assertEquals(WAIT, service.selectTHmfCycleTaskById(tasks.all().get(0).getId()).getStatus());
        assertTrue(logs.all().isEmpty(), "没到期的连痕迹都不添");
    }

    @Test
    void dueExactlyAtBoundary_isDueInclusive() {
        Date due = t("2026-10-09 09:00:00");
        readyArch("JZ-A", due);
        Long id = interest("JX-1", "JZ-A", due).getId();
        // 恰好落在止点那一刻：该出手那天到了
        assertEquals(Collections.singletonList(id),
                service.listDue(due).stream().map(THmfCycleTask::getId).collect(Collectors.toList()));
        CycleSweep s = service.sweepCycleTasks(due);
        assertEquals(1, s.getDoneCount());
    }

    @Test
    void oneSecondBeforeDue_notPicked() {
        Date due = t("2026-10-09 09:00:00");
        readyArch("JZ-A", due);
        interest("JX-1", "JZ-A", due);
        Date at = plus(due, -1000);
        assertTrue(service.listDue(at).isEmpty());
        assertTrue(service.sweepCycleTasks(at).isEmpty());
    }

    @Test
    void leadDaysOnly_speaksEarlyButDueItselfNeverMoves() {
        Date due = t("2026-10-12 09:00:00");
        readyArch("JZ-A", due);
        THmfCycleTask r = task("JX-1", "JZ-A", BIZ_INTEREST, due, new BigDecimal("3"));
        Date early = t("2026-10-09 09:00:00");
        // 提前三天的同一刻开口：能商量的只有提前几天那一格
        assertEquals(1, service.listDue(early).size());
        assertEquals(1, service.sweepCycleTasks(early).getDoneCount());
        // 止点本身钉死，没被挪
        assertEquals(due, service.selectTHmfCycleTaskById(r.getId()).getDueAt());
    }

    @Test
    void notDueRowsNeverPulledInToFillBatch() {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        readyArch("JZ-B", at);
        interest("JX-DUE", "JZ-A", t("2026-10-09 09:00:00"));
        Long laterId = interest("JX-LATER", "JZ-B", t("2026-10-20 09:00:00")).getId();
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(1, s.getDoneCount());
        // 没到期那条绝不为本轮凑批而被挪进来
        assertFalse(s.getDoneIds().contains(laterId));
        assertEquals(WAIT, service.selectTHmfCycleTaskById(laterId).getStatus());
    }

    @Test
    void finishedOrFailedRowsNotRefetchedAsWaiting() {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        Long doneId = interest("JX-DONE", "JZ-A", t("2026-10-09 09:00:00")).getId();
        service.sweepCycleTasks(at);
        assertEquals(DONE, service.selectTHmfCycleTaskById(doneId).getStatus());

        // 已迁出户：第一轮判催不动
        arch("JZ-B", 1, null, null);
        Long failId = urge("CJ-FAIL", "JZ-B", t("2026-10-09 09:00:00")).getId();
        service.sweepCycleTasks(t("2026-10-09 10:00:00"));
        assertEquals(FAIL, service.selectTHmfCycleTaskById(failId).getStatus());

        // 再扫多少轮，办结与催不动都不会再被当候办捞起来
        CycleSweep again = service.sweepCycleTasks(t("2026-10-12 09:00:00"));
        assertTrue(again.isEmpty());
    }

    // ---- 时段：班内/班外/法定节假日 ----

    @Test
    void nightHoldsDueRowsUntilNextShift() {
        // 止点在次日，提前一天开口：当夜已够上开口的日子，夜里压着，次日班内同一止点日前办成
        Date due = t("2026-10-09 18:00:00");
        readyArch("JZ-A", due);
        Long id = task("JX-1", "JZ-A", BIZ_INTEREST, due, new BigDecimal("1")).getId();
        Date night = t("2026-10-08 21:00:00");
        CycleSweep nightSweep = service.sweepCycleTasks(night);
        assertEquals(1, nightSweep.getWaitCount(), "夜里已到开口日也先压着");
        assertEquals(WAIT, service.selectTHmfCycleTaskById(id).getStatus());
        assertEquals(1, logsOf(id, ACT_HELD).size());

        CycleSweep morning = service.sweepCycleTasks(t("2026-10-09 08:00:00"));
        assertEquals(1, morning.getDoneCount(), "下一轮班内开口就办出去（仍在止点当天，不属逾期）");
    }

    @Test
    void shiftBoundary_0800Acts_2000Holds() {
        Date d1 = t("2026-10-08 07:59:00");
        readyArch("JZ-A", d1);
        Long earlyId = interest("JX-EARLY", "JZ-A", d1).getId();
        // 08:00:00 整动手
        CycleSweep at8 = service.sweepCycleTasks(t("2026-10-08 08:00:00"));
        assertEquals(1, at8.getDoneCount());
        assertEquals(DONE, service.selectTHmfCycleTaskById(earlyId).getStatus());

        Date d2 = t("2026-10-09 19:59:00");
        readyArch("JZ-B", d2);
        Long lateId = interest("JX-LATE", "JZ-B", d2).getId();
        // 20:00:00 整收手，哪怕白天的止点早到了也压
        CycleSweep at20 = service.sweepCycleTasks(t("2026-10-09 20:00:00"));
        assertEquals(1, at20.getWaitCount());
        assertEquals(WAIT, service.selectTHmfCycleTaskById(lateId).getStatus());
    }

    @Test
    void legalHolidayWholeDayHolds_evenInsideShift() throws Exception {
        holidays("2026-10-01");
        // 止点 10/8，提前 8 天开口：9/30 已够日子，10/1 节假日整天压着，10/8 止点当天班内办成
        Date due = t("2026-10-08 18:00:00");
        readyArch("JZ-A", due);
        Long id = task("JX-1", "JZ-A", BIZ_INTEREST, due, new BigDecimal("8")).getId();
        // 法定节假日上午十点，本该班内，整天整段不算出手时间
        CycleSweep holiday = service.sweepCycleTasks(t("2026-10-01 10:00:00"));
        assertEquals(1, holiday.getWaitCount());
        assertEquals(WAIT, service.selectTHmfCycleTaskById(id).getStatus());

        CycleSweep after = service.sweepCycleTasks(t("2026-10-08 09:00:00"));
        assertEquals(1, after.getDoneCount(), "节假日过后、止点当天开口再办");
    }

    // ---- 年终封存：单另两段，同户同日只认头一笔 ----

    @Test
    void yearEndSeal_runsAllNightIgnoringShiftAndHoliday() throws Exception {
        holidays("2026-12-20");
        Date due = t("2026-12-20 12:00:00");
        readyArch("JZ-A", due);
        Long id = interest("JX-1", "JZ-A", due).getId();
        // 止点当天 23:30，又赶上法定节假日：封存期不照班次/节假日分，整日可出手
        CycleSweep s = service.sweepCycleTasks(t("2026-12-20 23:30:00"));
        assertEquals(1, s.getDoneCount());
        assertEquals(DONE, service.selectTHmfCycleTaskById(id).getStatus());
    }

    @Test
    void sealSameSiteSameDay_firstLandedWins_secondRejectedFirstUntouched() {
        Date at = t("2026-12-20 02:00:00");
        readyArch("JZ-A", at);
        Long firstId = interest("JX-1", "JZ-A", t("2026-12-20 00:30:00")).getId();
        Long secondId = urge("CJ-2", "JZ-A", t("2026-12-20 01:00:00")).getId();
        CycleSweep s = service.sweepCycleTasks(at);

        assertEquals(1, s.getDoneCount());
        assertEquals(1, s.getWaitCount(), "同户同日后到的进不了账，前一条原样摆着");
        assertTrue(s.getDoneIds().contains(firstId));
        assertTrue(s.getWaitIds().contains(secondId));
        assertEquals(DONE, service.selectTHmfCycleTaskById(firstId).getStatus());
        assertEquals(WAIT, service.selectTHmfCycleTaskById(secondId).getStatus());
        assertEquals(1, logsOf(secondId, ACT_SEAL_DUP).size());
    }

    @Test
    void sealSameSiteSameDay_acrossRunsSecondLandedLaterStillRejected() {
        readyArch("JZ-A", t("2026-12-20 00:00:00"));
        Long firstId = interest("JX-1", "JZ-A", t("2026-12-20 01:00:00")).getId();
        // 头一回（02:00）只跑到头一条，办结
        CycleSweep r1 = service.sweepCycleTasks(t("2026-12-20 02:00:00"));
        assertEquals(1, r1.getDoneCount());
        // 第二条是白天新开的单，当天 10:00 下一回才够上日子
        Long secondId = urge("CJ-2", "JZ-A", t("2026-12-20 09:00:00")).getId();
        CycleSweep r2 = service.sweepCycleTasks(t("2026-12-20 10:00:00"));
        assertEquals(0, r2.getDoneCount());
        assertEquals(1, r2.getWaitCount(), "同一天任务跑多回，同户第二笔仍进不了账");
        assertEquals(DONE, service.selectTHmfCycleTaskById(firstId).getStatus());
        assertEquals(WAIT, service.selectTHmfCycleTaskById(secondId).getStatus(), "前一条原样摆着");
        assertEquals(1, logsOf(secondId, ACT_SEAL_DUP).size());
    }

    @Test
    void sealSameSiteDifferentDays_eachLands() {
        readyArch("JZ-A", t("2026-12-21 00:00:00"));
        Long d1 = interest("JX-1", "JZ-A", t("2026-12-20 09:00:00")).getId();
        service.sweepCycleTasks(t("2026-12-20 10:00:00"));
        Long d2 = interest("JX-2", "JZ-A", t("2026-12-21 09:00:00")).getId();
        CycleSweep s = service.sweepCycleTasks(t("2026-12-21 10:00:00"));
        assertEquals(DONE, service.selectTHmfCycleTaskById(d1).getStatus());
        assertEquals(1, s.getDoneCount(), "不同日各自落定，头一天那条不把第二天的也顶掉");
        assertTrue(s.getDoneIds().contains(d2));
    }

    // ---- 办结：两笔同脚 ----

    @Test
    void finishWritesBothStatusAndFinishInstant() {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        Long id = interest("JX-1", "JZ-A", at).getId();
        service.sweepCycleTasks(at);
        THmfCycleTask r = service.selectTHmfCycleTaskById(id);
        assertEquals(DONE, r.getStatus(), "去向翻成已办结");
        assertEquals(at, r.getFinishAt(), "完成那一刻同时记上，缺一笔不算完");
        assertEquals(1, logsOf(id, ACT_DONE).size());
    }

    // ---- 催交：两路各送各记，谁也顶不了谁 ----

    @Test
    void urgeBothChannelsDelivered_doneWithTwoSeparateDeliveryRows() {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        Long id = urge("CJ-1", "JZ-A", at).getId();
        CycleSweep s = service.sweepCycleTasks(at);

        assertEquals(1, s.getDoneCount());
        assertEquals(DONE, service.selectTHmfCycleTaskById(id).getStatus());
        assertEquals(at, service.selectTHmfCycleTaskById(id).getFinishAt());
        List<THmfCycleLog> station = logsOf(id, ACT_SEND_STATION);
        List<THmfCycleLog> sms = logsOf(id, ACT_SEND_SMS);
        assertEquals(1, station.size());
        assertEquals(1, sms.size());
        assertEquals(1, station.get(0).getChannel());
        assertEquals(2, sms.get(0).getChannel());
        assertEquals(1, station.get(0).getDelivered());
        assertEquals(1, sms.get(0).getDelivered());
        assertEquals(1, sender.stationCalls.size());
        assertEquals(1, sender.smsCalls.size());
    }

    @Test
    void urgeOneChannelFails_stillWaitingAndEachChannelRecordedHonestly() {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        Long id = urge("CJ-1", "JZ-A", at).getId();
        sender.smsAck = false;
        CycleSweep s = service.sweepCycleTasks(at);

        assertEquals(1, s.getWaitCount(), "短信那路没送达，不能拿站内顶，仍候办");
        assertEquals(WAIT, service.selectTHmfCycleTaskById(id).getStatus());
        assertNull(service.selectTHmfCycleTaskById(id).getFinishAt());
        assertEquals(1, (int) logsOf(id, ACT_SEND_STATION).get(0).getDelivered());
        assertEquals(0, (int) logsOf(id, ACT_SEND_SMS).get(0).getDelivered());
    }

    @Test
    void urgeRetryNextRound_logsBothChannelsAgainUnderNewRound() {
        Date r1 = t("2026-10-09 09:00:00");
        readyArch("JZ-A", r1);
        Long id = urge("CJ-1", "JZ-A", r1).getId();
        sender.stationAck = false;
        sender.smsAck = false;
        service.sweepCycleTasks(r1);
        assertEquals(1, logsOf(id, ACT_SEND_STATION).size());
        assertEquals(1, logsOf(id, ACT_SEND_SMS).size());

        // 下一轮开口，两路各自再送再记，不并账
        sender.stationAck = true;
        sender.smsAck = true;
        CycleSweep r2 = service.sweepCycleTasks(t("2026-10-09 14:00:00"));
        assertEquals(1, r2.getDoneCount());
        assertEquals(2, logsOf(id, ACT_SEND_STATION).size());
        assertEquals(2, logsOf(id, ACT_SEND_SMS).size());
    }

    @Test
    void noSenderWired_recordsNotDeliveredInsteadOfFakingDone() throws Exception {
        Field f = THmfCycleTaskServiceImpl.class.getDeclaredField("noticeSender");
        f.setAccessible(true);
        f.set(service, null);
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        Long id = urge("CJ-1", "JZ-A", at).getId();
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(1, s.getWaitCount());
        assertEquals(0, (int) logsOf(id, ACT_SEND_STATION).get(0).getDelivered());
        assertEquals(0, (int) logsOf(id, ACT_SEND_SMS).get(0).getDelivered());
    }

    // ---- 还没开单先撞墙 ----

    @Test
    void migratedSite_failWithReasonButStillListed() {
        Date at = t("2026-10-09 09:00:00");
        arch("JZ-GONE", 1, null, null);
        Long id = urge("CJ-1", "JZ-GONE", at).getId();
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(1, s.getFailCount());
        THmfCycleTask r = service.selectTHmfCycleTaskById(id);
        assertEquals(FAIL, r.getStatus());
        assertNotNull(r.getFailAt());
        assertTrue(r.getFailReason().contains("迁走"));
        // 页面上仍旧翻得到
        assertTrue(service.selectCycleTaskList().stream().anyMatch(x -> id.equals(x.getId())));
        assertEquals(1, logsOf(id, ACT_BLOCK).size());
    }

    @Test
    void missingSite_failAsMigrated() {
        Date at = t("2026-10-09 09:00:00");
        Long id = urge("CJ-1", "JZ-NOBODY", at).getId();
        assertEquals(1, service.sweepCycleTasks(at).getFailCount());
        assertEquals(FAIL, service.selectTHmfCycleTaskById(id).getStatus());
    }

    @Test
    void contactStaleOneQuarter_failLostContact_evenWithMobile() {
        Date at = t("2026-10-09 09:00:00");
        arch("JZ-STALE", 0, "13800000000", plus(at, -95 * DAY));
        Long id = urge("CJ-1", "JZ-STALE", at).getId();
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(1, s.getFailCount());
        assertTrue(service.selectTHmfCycleTaskById(id).getFailReason().contains("季度"));
    }

    @Test
    void contactNeverFilled_failLostContact() {
        Date at = t("2026-10-09 09:00:00");
        arch("JZ-NOFILL", 0, null, null);
        Long id = urge("CJ-1", "JZ-NOFILL", at).getId();
        assertEquals(1, service.sweepCycleTasks(at).getFailCount());
    }

    @Test
    void oneBlockedRowDoesNotKillWholeRound() {
        Date at = t("2026-10-09 09:00:00");
        arch("JZ-GONE", 1, null, null);
        readyArch("JZ-OK", at);
        urge("CJ-GONE", "JZ-GONE", at);
        interest("JX-OK", "JZ-OK", at);
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(1, s.getFailCount());
        assertEquals(1, s.getDoneCount(), "一处卡住，后面的照旧跑完，不掐整轮");
    }

    // ---- 逾期未办：撤回转催不动 ----

    @Test
    void overdueWithdraw_failButKeepsOriginalDueAndLeavesFinishBlank() {
        Date due = t("2026-10-01 12:00:00");
        readyArch("JZ-A", due);
        Long id = interest("JX-1", "JZ-A", due).getId();
        Date at = t("2026-10-09 09:00:00");
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(1, s.getFailCount());
        THmfCycleTask r = service.selectTHmfCycleTaskById(id);
        assertEquals(FAIL, r.getStatus());
        assertEquals(at, r.getFailAt());
        assertTrue(r.getFailReason().contains("撤回"));
        assertEquals(1, logsOf(id, ACT_WITHDRAW).size(), "撤回这一笔事后查得着");
        assertEquals(due, r.getDueAt(), "头回定的止点不被后一笔盖掉");
        assertNull(r.getFinishAt(), "真正完成那一刻没有就不能被撤回笔盖出来");
    }

    @Test
    void sameDayAfterDueAtNight_justHeldNotWithdrawn() {
        Date due = t("2026-10-08 09:00:00");
        readyArch("JZ-A", due);
        Long id = interest("JX-1", "JZ-A", due).getId();
        // 止点当天夜里：只压着，日子没过完不算逾期
        CycleSweep s = service.sweepCycleTasks(t("2026-10-08 21:00:00"));
        assertEquals(1, s.getWaitCount());
        assertTrue(logsOf(id, ACT_WITHDRAW).isEmpty());
        assertEquals(1, logsOf(id, ACT_HELD).size());
    }

    // ---- 三数同次同源、点档即看屏 ----

    @Test
    void sweepThreeCounts_matchReplayFromArchiveOneByOne() {
        // 一轮里三桶齐：办结两条、催不动一条（迁出）、等着办一条（封存同户同日撞车）
        Date at = t("2026-12-20 02:00:00");
        readyArch("JZ-D1", at);
        readyArch("JZ-D2", at);
        arch("JZ-XF", 1, null, null);

        interest("JX-D1", "JZ-D1", at);
        interest("JX-D2-A", "JZ-D2", t("2026-12-20 00:30:00"));
        urge("JX-D2-B", "JZ-D2", t("2026-12-20 00:40:00"));
        urge("CJ-XF", "JZ-XF", at);

        CycleSweep live = service.sweepCycleTasks(at);
        assertEquals(2, live.getDoneCount());
        assertEquals(1, live.getFailCount());
        assertEquals(1, live.getWaitCount());

        // 看屏那一回与点档那一回是同一次算的：照最近一轮 round_at 从痕迹重数
        CycleSweep replay = service.replayRound(null);
        assertEquals(at, replay.getRoundAt());
        assertEquals(live.getWaitCount(), replay.getWaitCount());
        assertEquals(live.getDoneCount(), replay.getDoneCount());
        assertEquals(live.getFailCount(), replay.getFailCount());
        assertEquals(new HashSet<>(live.getDoneIds()), new HashSet<>(replay.getDoneIds()));
        assertEquals(new HashSet<>(live.getFailIds()), new HashSet<>(replay.getFailIds()));
        assertEquals(new HashSet<>(live.getWaitIds()), new HashSet<>(replay.getWaitIds()));
        // 每个数都点得到具体条目
        assertEquals(2, replay.getDoneIds().size());
        assertEquals(1, replay.getFailIds().size());
        assertEquals(1, replay.getWaitIds().size());
    }

    @Test
    void replayByExplicitRoundAt_isolatesRounds() {
        Date r1 = t("2026-10-08 09:00:00");
        readyArch("JZ-A", r1);
        readyArch("JZ-B", t("2026-10-09 09:00:00"));
        interest("JX-1", "JZ-A", r1);
        service.sweepCycleTasks(r1);
        interest("JX-2", "JZ-B", t("2026-10-09 09:00:00"));
        Date r2 = t("2026-10-09 09:00:00");
        service.sweepCycleTasks(r2);

        assertEquals(1, service.replayRound(r1).getDoneCount(), "头一轮只点得到头一轮那条");
        assertEquals(1, service.replayRound(r2).getDoneCount());
        assertEquals(r2, service.latestRoundAt(), "页面取最近一轮，不取页面自带时刻");
    }

    // ---- 同秒两笔：各算各的 ----

    @Test
    void twoRowsSameSecond_countedSeparately_notMerged_notDropped() {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        readyArch("JZ-B", at);
        Long a = interest("JX-A", "JZ-A", at).getId();
        Long b = interest("JX-B", "JZ-B", at).getId();
        CycleSweep s = service.sweepCycleTasks(at);
        assertEquals(2, s.getDoneCount());
        assertNotSame(a, b);
        assertEquals(2, logs.all().stream().filter(l -> l.getAction() == ACT_DONE).count(),
                "同一秒挤进来也是两笔，不并一条也不少一条");
    }

    private static void assertNotSame(Object a, Object b) {
        assertFalse(a.equals(b));
    }

    // ---- 查单与回写同一把尺 ----

    @Test
    void listDuePicksExactlyTheRowsSweepSettles() {
        Date at = t("2026-10-09 21:00:00"); // 夜里：名单照样挑，执行时按住
        readyArch("JZ-A", at);
        readyArch("JZ-B", at);
        interest("JX-DUE", "JZ-A", t("2026-10-09 18:00:00"));
        urge("CJ-DUE", "JZ-B", t("2026-10-09 18:00:00"));
        interest("JX-FUTURE", "JZ-A", t("2026-12-01 09:00:00"));

        Set<Long> picked = service.listDue(at).stream()
                .map(THmfCycleTask::getId).collect(Collectors.toSet());
        CycleSweep s = service.sweepCycleTasks(at);
        Set<Long> settled = new HashSet<>();
        settled.addAll(s.getWaitIds());
        settled.addAll(s.getDoneIds());
        settled.addAll(s.getFailIds());
        // 查单点中的与回写处理的是同一批（夜里两条按住，未到期那条两边都不进）
        assertEquals(picked, settled);
        assertEquals(2, picked.size());
    }

    // ---- runOnce 旧签名原样不动 ----

    @Test
    void runOnceKeepsSignature_returnsDoneCountAndNewPickerIsSeparateMethod() throws Exception {
        Date at = t("2026-10-09 09:00:00");
        readyArch("JZ-A", at);
        interest("JX-1", "JZ-A", at);
        // 旧签名 int runOnce(Date) 仍在，回话本轮办结条数
        int done = service.runOnce(at);
        assertEquals(1, done);
        // 换的挑单写法另立方法名挂着
        assertNotNull(THmfCycleTaskServiceImpl.class.getMethod("sweepCycleTasks", Date.class));
    }
}
