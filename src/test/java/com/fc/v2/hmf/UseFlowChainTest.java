package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfPayRow;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.auto.THmfUseFlowStageLog;
import com.fc.v2.model.custom.UseFlowAdvance;
import com.fc.v2.model.custom.UseFlowAudit;
import com.fc.v2.model.custom.UseFlowVerdict;
import com.fc.v2.service.impl.THmfUseFlowServiceImpl;

/**
 * 《使用申请四档链口径》逐条核对：
 * 待发起/立项/公示/施工/结算四档、立项四样、公示天数、合同与验收监理、列支扣余额、
 * 只进紧邻一档、段次照痕迹重放、同档第二遍不另起一行、退回收档旧记沉底新拨重攒、
 * 作废冻字冻张、同申请号唯一在办、两条来路并一处、倒查多记载/缺档/事后补录/列支对不上。
 */
class UseFlowChainTest {

    // 段
    private static final int INIT = 0;
    private static final int PUB = 1;
    private static final int BUILD = 2;
    private static final int SETTLE = 3;

    // 会落
    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int VOID = 2;
    private static final int DONE = 3;

    // 痕迹动作
    private static final int ACT_PASS = 0;
    private static final int ACT_BACK = 1;
    private static final int ACT_PAY = 2;
    private static final int ACT_VOID = 3;

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final Date T0 = new Date(1_700_000_000_000L);

    private THmfUseFlowServiceImpl service;
    private InMemoryMapper<THmfUseFlow> flows;
    private InMemoryMapper<THmfUseFlowStageLog> logs;
    private InMemoryMapper<THmfAcctCard> cards;
    private InMemoryMapper<THmfPayRow> payRows;
    private FakePayBookMapper books;

    /** 定点时钟：起单/作废等未显式给时刻的动作统一落在 T0，保证痕迹时间序可验。 */
    private long clock = T0.getTime();

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfUseFlowServiceImpl() {
            @Override
            protected Date nowTime() {
                return new Date(clock);
            }
        };
        this.flows = new FakeUseFlowMapper();
        this.logs = new FakeStageLogMapper();
        this.cards = new FakeAcctCardMapper();
        this.payRows = new FakePayRowMapper();
        inject("hmfUseFlowMapper", flows);
        inject("hmfUseFlowStageLogMapper", logs);
        inject("hmfAcctCardMapper", cards);
        inject("hmfPayRowMapper", payRows);
        this.books = new FakePayBookMapper();
        inject("hmfPayBookMapper", books);
        fund("JZ00", "FH-0001", new BigDecimal("500000.00"));
    }

    /** 给某底册挂一个分户账号，并存入一笔已销账交存（旧批法行：无册头、在现行册面层）。 */
    private void fund(String siteNo, String acctNo, BigDecimal qty) {
        THmfAcctCard c = new THmfAcctCard();
        c.setBillNo(acctNo);
        c.setSiteNo(siteNo);
        c.setDelFlag(0);
        cards.insert(c);
        THmfPayRow row = new THmfPayRow();
        row.setBatchNo("B-1");
        row.setItemCode(acctNo);
        row.setQty(qty);
        row.setStatus(1);
        row.setLayer(0);
        row.setDelFlag(0);
        payRows.insert(row);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = THmfUseFlowServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private Date at(long dayOffset) {
        return new Date(T0.getTime() + dayOffset * DAY);
    }

    private THmfUseFlow open() {
        return openAt("JZ00", "SY-2026-001");
    }

    private THmfUseFlow openAt(String siteNo, String bizNo) {
        return service.openFlow(bizNo, siteNo, "梧桐里苑3号楼屋面", new BigDecimal("128000.00"),
                "顺安防水工程队", "物业报的", "柜员甲", "屋面防水");
    }

    private UseFlowAdvance fwd(Long id) {
        UseFlowAdvance a = new UseFlowAdvance();
        a.setId(id);
        a.setAction(1);
        a.setOperator("柜员甲");
        a.setNow(T0);
        return a;
    }

    private Long toPublic(Long id) {
        UseFlowAdvance a = fwd(id);
        a.setPublicDays(7);
        UseFlowVerdict v = service.pushUseFlow(a);
        assertEquals(UseFlowVerdict.CODE_MOVED, v.getCode());
        assertEquals(PUB, v.getStage());
        return id;
    }

    private Long toBuild(Long id) {
        toPublic(id);
        UseFlowAdvance a = fwd(id);
        a.setNow(at(7));
        UseFlowVerdict v = service.pushUseFlow(a);
        assertEquals(UseFlowVerdict.CODE_MOVED, v.getCode());
        assertEquals(BUILD, v.getStage());
        return id;
    }

    private UseFlowVerdict toSettle(Long id) {
        UseFlowAdvance a = fwd(id);
        a.setNow(at(10));
        a.setContractNo("HT-2026-077");
        a.setAcceptRecord("验收合格记载");
        a.setSupervisor("监理钱工");
        return service.pushUseFlow(a);
    }

    private List<THmfUseFlowStageLog> logsOf(Long id, int action) {
        return logs.all().stream()
                .filter(l -> id.equals(l.getFlowId()))
                .filter(l -> l.getAction() == action)
                .collect(Collectors.toList());
    }

    // ---- 立项：四样齐才起得来 ----

    @Test
    void openFlow_fourItemsComplete_entersInitWithOnePassLog() {
        THmfUseFlow r = open();
        assertNotNull(r.getId());
        assertEquals(INIT, r.getStage());
        assertEquals(RUNNING, r.getStatus());
        assertEquals(1, r.getCurRound());
        assertEquals("梧桐里苑3号楼屋面", r.getProjectPlace());
        assertEquals(0, new BigDecimal("128000.00").compareTo(r.getBudgetAmt()));
        assertEquals("顺安防水工程队", r.getBuilderName());
        assertEquals("物业报的", r.getApplySource());
        List<THmfUseFlowStageLog> pass = logsOf(r.getId(), ACT_PASS);
        assertEquals(1, pass.size());
        assertEquals(INIT, pass.get(0).getStageNo());
        assertEquals("柜员甲", pass.get(0).getOperator());
    }

    @Test
    void openFlow_anyOfFourItemsMissing_rejected() {
        // (单号, 底册, 地点, 金额, 施工单位, 来源, 经办, 备注)
        assertNull(service.openFlow(null, "JZ00", "地点", new BigDecimal("1"), "施工队", "业委会报的", "甲", null));
        assertNull(service.openFlow("X", " ", "地点", new BigDecimal("1"), "施工队", "业委会报的", "甲", null),
                "无底册代号，余额没点处");
        assertNull(service.openFlow("X", "JZ00", "  ", new BigDecimal("1"), "施工队", "业委会报的", "甲", null));
        assertNull(service.openFlow("X", "JZ00", "地点", null, "施工队", "业委会报的", "甲", null));
        assertNull(service.openFlow("X", "JZ00", "地点", new BigDecimal("0"), "施工队", "业委会报的", "甲", null));
        assertNull(service.openFlow("X", "JZ00", "地点", new BigDecimal("1"), " ", "业委会报的", "甲", null));
        assertNull(service.openFlow("X", "JZ00", "地点", new BigDecimal("1"), "施工队", null, "甲", null));
    }

    @Test
    void openFlow_secondSheetBlockedWhilePriorRunningOrVoid_allowedAfterDone() {
        Long id = open().getId();
        // 前一张在办（含退回候重走的中止态），起不了第二张
        assertNull(service.openFlow("SY-2026-001", "JZ00", "地点", new BigDecimal("1"), "队", "物业报的", "甲", null));
        toBuild(id);
        UseFlowAdvance back = fwd(id);
        back.setAction(-1);
        back.setNow(at(8));
        assertEquals(UseFlowVerdict.CODE_MOVED, service.pushUseFlow(back).getCode(), "退回候重走=中止态");
        assertNull(service.openFlow("SY-2026-001", "JZ00", "地点", new BigDecimal("1"), "队", "物业报的", "甲", null),
                "已中止也起不了第二张");

        // 走完一趟办结后，同一申请号可另起新单（两条来路并一处，台账上各自独立）
        UseFlowAdvance reDue = fwd(id);
        reDue.setNow(at(15));
        assertEquals(BUILD, service.pushUseFlow(reDue).getStage(), "新拨公示走满才再进施工");
        UseFlowAdvance reSettle = fwd(id);
        reSettle.setNow(at(16));
        reSettle.setContractNo("HT-2");
        reSettle.setAcceptRecord("复验合格");
        reSettle.setSupervisor("监理孙工");
        assertEquals(DONE, service.pushUseFlow(reSettle).getBill().getStatus());
        THmfUseFlow second = service.openFlow("SY-2026-001", "JZ00", "新地点", new BigDecimal("2"), "新队",
                "业委会报的", "乙", null);
        assertNotNull(second);
        assertFalse(id.equals(second.getId()));
    }

    // ---- 公示档：守天数 ----

    @Test
    void initToPublic_requiresPublicDays() {
        Long id = open().getId();
        UseFlowVerdict v = service.pushUseFlow(fwd(id));
        assertEquals(UseFlowVerdict.CODE_STAY, v.getCode());
        assertEquals(INIT, v.getStage());
        assertEquals(1, logs.all().size(), "门槛不过不添痕迹");
    }

    @Test
    void publicGate_closedBeforeDaysElapsed_evenWithAllMaterials() {
        Long id = toPublic(open().getId());
        // 材料递得再齐，天数没走完施工门也不开
        UseFlowAdvance early = fwd(id);
        early.setNow(at(6));
        early.setContractNo("HT-1");
        early.setAcceptRecord("验收");
        early.setSupervisor("监理");
        UseFlowVerdict v = service.pushUseFlow(early);
        assertEquals(UseFlowVerdict.CODE_STAY, v.getCode());
        assertEquals(PUB, v.getStage());
        assertTrue(v.getMessage().contains("公示"));
        assertEquals(2, logs.all().size(), "立项、进公示各一行，没添新行");

        UseFlowAdvance due = fwd(id);
        due.setNow(at(7));
        assertEquals(BUILD, service.pushUseFlow(due).getStage(), "天数一走完，同一脚就收");
    }

    @Test
    void sweepByDays_andClerkHandPush_mergeIntoOneGate_noSecondRow() {
        // 来路一：系统按公示天数自推
        Long idA = toPublic(open().getId());
        assertEquals(0, service.sweepDuePublicity(at(6)), "天数没到，扫不动");
        assertEquals(PUB, service.selectTHmfUseFlowById(idA).getStage());
        assertEquals(1, service.sweepDuePublicity(at(7)), "天数到了自动进施工");
        assertEquals(BUILD, service.selectTHmfUseFlowById(idA).getStage());
        assertEquals(0, service.sweepDuePublicity(at(8)), "再扫不另起一行");
        // 柜员后手点来，屏上还带着公示旧段号：以系统回话为准，不补行
        UseFlowAdvance late = fwd(idA);
        late.setNow(at(8));
        late.setExpectStage(PUB);
        UseFlowVerdict v = service.pushUseFlow(late);
        assertEquals(UseFlowVerdict.CODE_STAY, v.getCode());
        assertEquals(BUILD, v.getStage());
        assertEquals(3, logs.all().size(), "立项/进公示/天数到过门，共三行");

        // 来路二：柜员先在手点门点过，定时来路再来也空走
        Long idB = toPublic(openSecond().getId());
        UseFlowAdvance hand = fwd(idB);
        hand.setNow(at(7));
        assertEquals(BUILD, service.pushUseFlow(hand).getStage());
        assertEquals(0, service.sweepDuePublicity(at(7)), "柜员已点过，自推不抢第二脚");
    }

    private THmfUseFlow openSecond() {
        return service.openFlow("SY-2026-002", "JZ00", "二号屋面", new BigDecimal("500.00"),
                "乙队", "业委会报的", "柜员乙", null);
    }

    // ---- 施工/结算：合同、验收、监理、余额 ----

    @Test
    void settleGate_eachMissingPieceStays() {
        Long id = toBuild(open().getId());
        UseFlowAdvance a = fwd(id);
        a.setNow(at(10));
        a.setAcceptRecord("验收");
        a.setSupervisor("监理");
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(a).getCode(), "合同要件没挂齐");

        a.setContractNo("HT-1");
        a.setAcceptRecord(null);
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(a).getCode(), "验收记载没到");

        a.setAcceptRecord("验收");
        a.setSupervisor(null);
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(a).getCode(), "监理那一名没到");
        assertEquals(BUILD, service.selectTHmfUseFlowById(id).getStage(), "几次被拒都停在施工档");
    }

    @Test
    void settleGate_insufficientBalance_wholeAccountUntouched() {
        // 另起一个余额不足的底册：户面只到 127999.99，列支要 128000.00
        fund("JZPOOR", "FH-POOR", new BigDecimal("127999.99"));
        Long id = toBuild(openAt("JZPOOR", "SY-POOR-001").getId());
        int logsBefore = logs.all().size();
        UseFlowVerdict v = toSettle(id);
        assertEquals(UseFlowVerdict.CODE_STAY, v.getCode());
        assertEquals(BUILD, v.getStage(), "停在结算门外");
        assertTrue(v.getMessage().contains("余额"));
        THmfUseFlow r = service.selectTHmfUseFlowById(id);
        assertEquals(BUILD, r.getStage());
        assertNull(r.getPayAmt(), "整张账不动：列支没落地");
        assertNull(r.getAcceptRecord());
        assertNull(r.getSupervisor());
        assertEquals(logsBefore, logs.all().size(), "不添过口、不添列支，一笔不动");
    }

    @Test
    void settleGate_clear_paysAndClosesTrip() {
        Long id = toBuild(open().getId());
        UseFlowVerdict v = toSettle(id);
        assertEquals(UseFlowVerdict.CODE_MOVED, v.getCode());
        assertEquals(SETTLE, v.getStage());
        THmfUseFlow r = service.selectTHmfUseFlowById(id);
        assertEquals(DONE, r.getStatus(), "走到结算档才算办结一趟");
        assertEquals(0, new BigDecimal("128000.00").compareTo(r.getPayAmt()),
                "列支一落地按预算额从户面划走");
        assertNotNull(r.getPayTime());
        assertEquals("HT-2026-077", r.getContractNo());
        // 过口、列支同一刻两行
        List<THmfUseFlowStageLog> pay = logsOf(id, ACT_PAY);
        assertEquals(1, pay.size());
        assertEquals(0, new BigDecimal("128000.00").compareTo(pay.get(0).getPayAmt()));
        assertEquals(at(10), pay.get(0).getLogTime());
        // 办结后推不动、退不回
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(fwd(id)).getCode());
    }

    // ---- 同档第二遍：不另起一行 ----

    @Test
    void sameStageSecondSubmission_doesNotAddRow() {
        Long id = open().getId();
        UseFlowAdvance again = fwd(id);
        again.setExpectStage(INIT);
        UseFlowVerdict v = service.pushUseFlow(again);
        assertEquals(UseFlowVerdict.CODE_DUP, v.getCode());
        assertEquals(INIT, v.getStage());
        assertEquals(1, logs.all().size(), "账上还是头一遍那句话");
    }

    // ---- 段次只认痕迹重放，纸面段号不作数 ----

    @Test
    void stageAlwaysRecomputedFromLogs_paperStageIgnored() {
        Long id = toPublic(open().getId());
        // 纸面已被手写到施工/结算，系统照痕迹点出来仍在公示
        THmfUseFlow tampered = service.selectTHmfUseFlowById(id);
        tampered.setStage(SETTLE);
        flows.updateById(tampered);

        UseFlowAdvance a = fwd(id);
        a.setNow(at(7));
        UseFlowVerdict v = service.pushUseFlow(a);
        assertEquals(UseFlowVerdict.CODE_MOVED, v.getCode());
        assertEquals(BUILD, v.getStage(), "算出来的为准：公示天数走完只进紧邻的施工档");
        assertEquals(BUILD, service.selectTHmfUseFlowById(id).getStage(), "镜像被校正");
    }

    @Test
    void forwardOnlyAdjacent_backwardOnlyOneStage() {
        Long id = open().getId();
        UseFlowAdvance jump = fwd(id);
        jump.setExpectStage(BUILD);
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(jump).getCode(), "不许越档往前");

        toBuild(id);
        UseFlowAdvance farBack = fwd(id);
        farBack.setAction(-1);
        farBack.setExpectStage(INIT);
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(farBack).getCode(), "不许一次退两档");
    }

    @Test
    void backwardAtInit_rejected() {
        Long id = open().getId();
        UseFlowAdvance back = fwd(id);
        back.setAction(-1);
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(back).getCode(), "头一档无处可退");
        assertEquals(1, logs.all().size());
    }

    // ---- 退回：收一档、旧记沉底、新拨重攒 ----

    @Test
    void rollback_sinksOldArtifacts_andReplaysInNewRound() {
        Long id = toBuild(open().getId());
        UseFlowAdvance back = fwd(id);
        back.setAction(-1);
        back.setNow(at(8));
        back.setRemark("公示异议，退回");
        UseFlowVerdict v = service.pushUseFlow(back);
        assertEquals(UseFlowVerdict.CODE_MOVED, v.getCode());
        assertEquals(PUB, v.getStage());
        assertEquals(2, service.selectTHmfUseFlowById(id).getCurRound(), "另起一拨");
        THmfUseFlow r = service.selectTHmfUseFlowById(id);
        assertEquals(at(8), r.getPublicStart(), "公示从头再攒：起算拨到退回此刻");
        assertEquals(Integer.valueOf(7), r.getPublicDays(), "当地定的公示天数还在");
        // 旧行原样沉底不抹，退回本身也逐笔留痕
        List<THmfUseFlowStageLog> backRows = logsOf(id, ACT_BACK);
        assertEquals(1, backRows.size());
        assertEquals(BUILD, backRows.get(0).getStageNo());
        assertEquals("公示异议，退回", backRows.get(0).getRemark());
        assertEquals(4, logs.all().size(), "立项/公示/施工过口 + 退回一行");

        // 新拨公示期没满，施工门依旧不开
        UseFlowAdvance early = fwd(id);
        early.setNow(at(10));
        assertEquals(UseFlowVerdict.CODE_STAY, service.pushUseFlow(early).getCode());
        UseFlowAdvance due = fwd(id);
        due.setNow(at(15));
        assertEquals(BUILD, service.pushUseFlow(due).getStage(), "重走仍从公示档挨到施工");
    }

    @Test
    void rollbackToInit_sinksPublicDaysToo() {
        Long id = toPublic(open().getId());
        UseFlowAdvance back = fwd(id);
        back.setAction(-1);
        back.setNow(at(1));
        assertEquals(INIT, service.pushUseFlow(back).getStage());
        THmfUseFlow r = service.selectTHmfUseFlowById(id);
        assertEquals(2, r.getCurRound());
        assertNull(r.getPublicDays(), "收回来那档原先记的事沉底，重走重新攒");
        assertNull(r.getPublicStart());
    }

    // ---- 作废：字改不得、张删不得、续不动 ----

    @Test
    void voidedBill_frozen() {
        Long id = toBuild(open().getId());
        THmfUseFlow v = service.voidUseFlow(id, "柜员乙", "申报不实");
        assertEquals(VOID, v.getStatus());
        assertEquals(1, logsOf(id, ACT_VOID).size());

        UseFlowVerdict push = service.pushUseFlow(fwd(id));
        assertEquals(UseFlowVerdict.CODE_VOID, push.getCode(), "作后续不动记事");
        UseFlowAdvance back = fwd(id);
        back.setAction(-1);
        assertEquals(UseFlowVerdict.CODE_VOID, service.pushUseFlow(back).getCode());
        assertNull(service.voidUseFlow(id, "甲", null), "已作废不许再盖印");
        // 张还在、旧痕一条不删
        assertNotNull(service.selectTHmfUseFlowById(id));
        // 同申请号底下一张已中止（作废），第二张起不来
        assertNull(service.openFlow("SY-2026-001", "JZ00", "地点", new BigDecimal("1"), "队", "物业报的", "甲", null));
    }

    // ---- 倒查那笔账 ----

    @Test
    void audit_fullTripPasses() {
        Long id = toBuild(open().getId());
        toSettle(id);
        UseFlowAudit a = service.auditUseFlow(id);
        assertTrue(a.isPass(), a.getReason());
        // 从结算倒回立项：结算（过口+列支）在前，立项在后
        List<Integer> stages = a.getTrail().stream().map(THmfUseFlowStageLog::getStageNo)
                .collect(Collectors.toList());
        assertEquals(SETTLE, stages.get(0));
        assertEquals(INIT, stages.get(stages.size() - 1));
    }

    @Test
    void audit_missingStageTrace_fails() {
        Long id = toBuild(open().getId());
        UseFlowAudit a = service.auditUseFlow(id);
        assertFalse(a.isPass());
        assertTrue(a.getReason().contains("结算"), "缺结算档痕迹");
    }

    @Test
    void audit_extraStageRecord_fails() {
        Long id = toBuild(open().getId());
        toSettle(id);
        // 多出一条施工档过口记载（同一拨同一档第二条）
        THmfUseFlowStageLog extra = new THmfUseFlowStageLog();
        extra.setFlowId(id);
        extra.setStageNo(BUILD);
        extra.setRoundNo(1);
        extra.setAction(ACT_PASS);
        extra.setOperator("事后的人");
        extra.setLogTime(at(9));
        extra.setCreateTime(at(9));
        extra.setDelFlag(0);
        logs.insert(extra);

        UseFlowAudit a = service.auditUseFlow(id);
        assertFalse(a.isPass(), "多出一条记载，这趟没走通");
        assertEquals(1, a.getBadRows().size());
    }

    @Test
    void audit_backfilledRow_fails() {
        Long id = toPublic(open().getId());
        // 把进公示那行的落笔时刻往前改、落库时刻留今：事后补录的形状
        THmfUseFlowStageLog pubRow = logsOf(id, ACT_PASS).stream()
                .filter(l -> l.getStageNo() == PUB).findFirst().get();
        pubRow.setLogTime(at(-30));
        logs.updateById(pubRow);

        UseFlowAudit a = service.auditUseFlow(id);
        assertFalse(a.isPass());
        assertTrue(a.getReason().contains("补录"));
    }

    @Test
    void audit_payMismatch_fails() {
        Long id = toBuild(open().getId());
        toSettle(id);
        THmfUseFlowStageLog pay = logsOf(id, ACT_PAY).get(0);
        pay.setPayAmt(new BigDecimal("1.00"));
        logs.updateById(pay);
        UseFlowAudit a = service.auditUseFlow(id);
        assertFalse(a.isPass());
        assertTrue(a.getReason().contains("列支"));
    }

    @Test
    void audit_voidedBill_fails() {
        Long id = open().getId();
        service.voidUseFlow(id, "甲", null);
        assertFalse(service.auditUseFlow(id).isPass());
    }

    @Test
    void audit_missingBill_reportsFalse() {
        UseFlowAudit a = service.auditUseFlow(404L);
        assertFalse(a.isPass());
        assertNull(service.pushUseFlow(null));
    }

    // ---- 户面余额与册子核销同源：撤册行/往期行不算，旧批法无册头行照旧算 ----

    @Test
    void householdBalance_followsSameRuleAsBookReconcile() {
        String site = "JZALIGN";
        THmfAcctCard c = new THmfAcctCard();
        c.setBillNo("FH-AL");
        c.setSiteNo(site);
        c.setDelFlag(0);
        cards.insert(c);

        // 已撤册册头下的已销现行行：随册作废，不进户面
        com.fc.v2.model.auto.THmfPayBook withdrawn = new com.fc.v2.model.auto.THmfPayBook();
        withdrawn.setBatchNo("BK-W");
        withdrawn.setStatus(1);
        withdrawn.setDelFlag(0);
        books.insert(withdrawn);
        payRows.insert(payRow("BK-W", withdrawn.getId(), "FH-AL", "100000.00", 1, 0));

        // 在册册头下挪到往期层的行：现行册面不露，不进户面
        com.fc.v2.model.auto.THmfPayBook active = new com.fc.v2.model.auto.THmfPayBook();
        active.setBatchNo("BK-A");
        active.setStatus(0);
        active.setDelFlag(0);
        books.insert(active);
        payRows.insert(payRow("BK-A", active.getId(), "FH-AL", "100000.00", 1, 1));
        // 同册挂起行（现行层但未销）：也不进户面
        payRows.insert(payRow("BK-A", active.getId(), "FH-AL", "100000.00", 2, 0));

        // 三笔都不进户面：128000 的列支过不去余额门
        Long id = toBuild(openAt(site, "SY-ALIGN-001").getId());
        assertEquals(UseFlowVerdict.CODE_STAY, toSettle(id).getCode(),
                "撤册行、往期行、挂起行都不许 inflate 户面");

        // 旧批法行：无册头、已销、现行层——照旧进户面，补足后列支过门
        payRows.insert(payRow("B-OLD", null, "FH-AL", "128000.00", 1, 0));
        UseFlowVerdict v = toSettle(id);
        assertEquals(UseFlowVerdict.CODE_MOVED, v.getCode(),
                "旧批法无册头的已销现行行照旧计户面，与册子规矩合在一处");
    }

    private THmfPayRow payRow(String batchNo, Long bookId, String acct, String qty, int status, int layer) {
        THmfPayRow r = new THmfPayRow();
        r.setBatchNo(batchNo);
        r.setBookId(bookId);
        r.setItemCode(acct);
        r.setQty(new BigDecimal(qty));
        r.setStatus(status);
        r.setLayer(layer);
        r.setDelFlag(0);
        return r;
    }
}
