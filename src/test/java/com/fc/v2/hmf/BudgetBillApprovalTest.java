package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfBudgetBill;
import com.fc.v2.model.auto.THmfBudgetSign;
import com.fc.v2.service.impl.THmfBudgetBillServiceImpl;

/**
 * 《拨付核签口径提示》逐条核对：三档签法、逐笔计数、顺序挨着往前、
 * 退回只退一格不抹名且新旧拨分存、封单、立单人/操作人两格、同年另起新单、拨付额同源、倒序复查。
 */
class BudgetBillApprovalTest {

    // 三档
    private static final int NODE_COMMITTEE = 0;
    private static final int NODE_FIRM = 1;
    private static final int NODE_CENTER = 2;

    // 情形
    private static final int RUNNING = 0;
    private static final int PASSED = 1;
    private static final int VETO = 2;

    // 动作
    private static final int ACT_SIGN = 0;
    private static final int ACT_REJECT = 1;

    private THmfBudgetBillServiceImpl service;
    private InMemoryMapper<THmfBudgetBill> bills;
    private InMemoryMapper<THmfBudgetSign> signs;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfBudgetBillServiceImpl();
        this.bills = new FakeBillMapper();
        this.signs = new FakeSignMapper();
        inject("hmfBudgetBillMapper", bills);
        inject("hmfBudgetSignMapper", signs);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = THmfBudgetBillServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private THmfBudgetBill open() {
        return service.openBill("BG-2026-001", "立单经手人甲", "JZ00", 2026,
                new BigDecimal("128000.00"), "屋面防水");
    }

    private List<THmfBudgetSign> signsOf(Long billId, int node, Integer round, int action) {
        return signs.all().stream()
                .filter(s -> billId.equals(s.getBillId()))
                .filter(s -> s.getNodeNo() == node)
                .filter(s -> round == null || round.equals(s.getRoundNo()))
                .filter(s -> s.getAction() == action)
                .collect(Collectors.toList());
    }

    // ---- 立单与窗口四处字 ----

    @Test
    void openBill_setsCommitteeStageWaitingAndFreezesMakerAndPayAmt() {
        THmfBudgetBill b = open();
        assertNotNull(b.getId());
        assertEquals(NODE_COMMITTEE, b.getNodeNo());
        assertEquals(1, b.getSignMode(), "业委会档：两笔凑齐(1)");
        assertEquals(2, b.getNeedCount(), "这一档该两个人签");
        assertEquals(0, b.getSignCount(), "此刻落了0笔");
        assertEquals(RUNNING, b.getStatus(), "候签，未过");
        assertEquals(1, b.getCurRound());
        assertEquals("立单经手人甲", b.getMaker());
        assertEquals(0, new BigDecimal("128000.00").compareTo(b.getPayAmt()));
        assertEquals("JZ00", b.getSiteNo());
        assertEquals(2026, b.getBillYear());
    }

    @Test
    void openBill_duplicateBillNoRejected() {
        assertNotNull(open());
        THmfBudgetBill again = service.openBill("BG-2026-001", "甲", "JZ00", 2026,
                new BigDecimal("1"), null);
        assertNull(again);
    }

    @Test
    void openBill_blankArgsRejected() {
        assertNull(service.openBill(null, "甲", "JZ00", 2026, new BigDecimal("1"), null));
        assertNull(service.openBill("X", " ", "JZ00", 2026, new BigDecimal("1"), null));
        assertNull(service.openBill("X", "甲", "JZ00", null, new BigDecimal("1"), null));
        assertNull(service.openBill("X", "甲", "JZ00", 2026, null, null));
    }

    // ---- 业委会：两笔凑齐 ----

    @Test
    void committee_needsTwoDistinctSignsAndStaysWaitingAfterFirst() {
        Long id = open().getId();
        THmfBudgetBill after1 = service.approve(id, "业委张三", "同意");
        assertEquals(NODE_COMMITTEE, after1.getNodeNo());
        assertEquals(1, after1.getSignCount(), "只落一笔，窗口仍候签");
        assertEquals(RUNNING, after1.getStatus());
        assertEquals(2, after1.getNeedCount());

        THmfBudgetBill after2 = service.approve(id, "业委李四", "同意");
        assertEquals(NODE_FIRM, after2.getNodeNo(), "两笔凑齐才挨到事务所档");
        assertEquals(0, after2.getSignMode(), "事务所档镜像应为任一人(0)");
        assertEquals(1, after2.getNeedCount());
        assertEquals(0, after2.getSignCount(), "新档此刻0笔");
    }

    @Test
    void committee_samePersonCannotSignTwiceInOneRound() {
        Long id = open().getId();
        assertNotNull(service.approve(id, "业委张三", null));
        assertNull(service.approve(id, "业委张三", "再签一笔"), "同人本拨重签按没签处理");
        assertEquals(1, signsOf(id, NODE_COMMITTEE, 1, ACT_SIGN).size());
    }

    // ---- 事务所：一笔即可 ----

    @Test
    void firm_firstSignaturePassesStage() {
        Long id = open().getId();
        service.approve(id, "业委张三", null);
        service.approve(id, "业委李四", null);
        THmfBudgetBill b = service.approve(id, "事务所王五", null);
        assertEquals(NODE_CENTER, b.getNodeNo(), "头一笔落地这一档就过");
        assertEquals(0, b.getSignCount(), "中心档新档候签");
    }

    // ---- 中心：一笔封单 ----

    @Test
    void center_firstSignatureSealsBill() {
        Long id = walkToCenter();
        THmfBudgetBill sealed = service.approve(id, "中心赵六", "准予拨付");
        assertEquals(PASSED, sealed.getStatus());
        assertEquals(NODE_CENTER, sealed.getNodeNo());
        assertEquals(1, sealed.getSignCount());
        // 封住后谁都不能再添名，也退不动
        assertNull(service.approve(id, "中心钱七", null));
        assertNull(service.reject(id, "中心赵六", "反悔"));
        assertEquals(1, signsOf(id, NODE_CENTER, 1, ACT_SIGN).size());
    }

    // ---- 顺序：越档不认 ----

    @Test
    void signingAtCenterWithoutFirmSignatureNotRecognized() {
        Long id = open().getId();
        // 业委会两笔凑齐
        service.approve(id, "业委张三", null);
        service.approve(id, "业委李四", null);
        // 事务所一字未签，单据被直接推到中心——中心盖章不认
        THmfBudgetBill cur = service.selectTHmfBudgetBillById(id);
        cur.setNodeNo(NODE_CENTER);
        bills.updateById(cur);

        assertNull(service.approve(id, "中心赵六", null), "中心越过事务所直接盖章，一样不认");
        assertEquals(0, signs.all().stream().filter(s -> NODE_CENTER == s.getNodeNo()).count());
    }

    @Test
    void signingAtFirmWithoutCommitteeCompleteNotRecognized() {
        Long id = open().getId();
        service.approve(id, "业委张三", null); // 业委会只有一笔
        THmfBudgetBill cur = service.selectTHmfBudgetBillById(id);
        cur.setNodeNo(NODE_FIRM);
        bills.updateById(cur);
        assertNull(service.approve(id, "事务所王五", null), "前一档没满，后一档签了白签");
        assertEquals(0, signs.all().stream().filter(s -> NODE_FIRM == s.getNodeNo()).count());
    }

    // ---- 齐不齐只认署名流水，不认人手记数 ----

    @Test
    void signCountAlwaysRecomputedFromSignRows_ignoringTamperedCounter() {
        Long id = open().getId();
        service.approve(id, "业委张三", null);
        // 经手人自记的数/屏上被手改的数都只是参照：把单子计数拨成 99
        THmfBudgetBill cur = service.selectTHmfBudgetBillById(id);
        cur.setSignCount(99);
        bills.updateById(cur);

        THmfBudgetBill b = service.approve(id, "业委李四", null);
        assertEquals(NODE_FIRM, b.getNodeNo(),
                "屏上99不算数：系统照流水点出2笔凑齐，才会挨到事务所档");
        assertEquals(0, b.getSignCount(), "挪到新档后镜像为新档已落名数0笔");
        // 业委会档第1拨实打实点出两笔，与留痕条数对齐
        assertEquals(2, signsOf(id, NODE_COMMITTEE, 1, ACT_SIGN).size());
    }

    // ---- 退回：只退一格、旧名不抹、新旧两拨分存、从遭退档接着添 ----

    @Test
    void rejectMovesOnlyOneStageBackKeepsOldNamesAndStartsNewRound() {
        Long id = walkToCenter();
        // 事务所曾在第1拨签过（走到中心的路上）
        assertEquals(1, signsOf(id, NODE_FIRM, 1, ACT_SIGN).size());

        // 中心这一档把单退回：只挪回紧挨的上一档（事务所）
        THmfBudgetBill back = service.reject(id, "中心赵六", "材料待补");
        assertEquals(NODE_FIRM, back.getNodeNo());
        assertEquals(VETO, back.getStatus());
        assertEquals(2, back.getCurRound());
        assertEquals(0, back.getSignCount(), "遭退档新拨从0笔候签");
        // 先前各档签下的名字一个不抹
        assertEquals(2, signsOf(id, NODE_COMMITTEE, 1, ACT_SIGN).size());
        assertEquals(1, signsOf(id, NODE_FIRM, 1, ACT_SIGN).size(), "事务所第1拨旧名留存");
        // 退回本身也留痕：谁、在哪一档、第几拨
        List<THmfBudgetSign> rej = signsOf(id, NODE_CENTER, 1, ACT_REJECT);
        assertEquals(1, rej.size());
        assertEquals("中心赵六", rej.get(0).getSigner());
        assertNotNull(rej.get(0).getSignTime());

        // 重新走签从遭退的事务所档接着往上添：同一人新拨可再签
        THmfBudgetBill reFirm = service.approve(id, "事务所王五", "补签");
        assertEquals(NODE_CENTER, reFirm.getNodeNo());
        assertEquals(2, reFirm.getCurRound());
        assertEquals(1, signsOf(id, NODE_FIRM, 2, ACT_SIGN).size(), "第2拨新名单独存档");
        assertEquals(1, signsOf(id, NODE_FIRM, 1, ACT_SIGN).size(), "第1拨原样未动");

        THmfBudgetBill sealed = service.approve(id, "中心赵六", null);
        assertEquals(PASSED, sealed.getStatus());
    }

    @Test
    void rejectAtCommitteeRejected_nothingBeforeIt() {
        Long id = open().getId();
        assertNull(service.reject(id, "业委张三", null), "头一档无处可退");
        assertEquals(0, signs.all().size());
    }

    @Test
    void rejectWithBlankApproverRejected() {
        assertNull(service.reject(999L, "谁", null), "单据不存在");
        Long id = open().getId();
        assertNull(service.reject(id, "  ", null));
    }

    @Test
    void rollbackUsesCurrentLoggedInOperatorAndMovesOneStageBack() {
        Long id = walkToCenter();
        // rollback 不带操作人：后一格跟着事务走，取当前登录操作人（无 Shiro 测试环境兜底为 "Task"）
        THmfBudgetBill back = service.rollback(id, "退回补正");
        assertEquals(NODE_FIRM, back.getNodeNo());
        THmfBudgetSign rej = signsOf(id, NODE_CENTER, 1, ACT_REJECT).get(0);
        assertNotNull(rej.getSigner());
        assertFalse(rej.getSigner().trim().isEmpty(), "退回笔须实记实际操作人，不许留空或笼统记成立单人");
        assertEquals("退回补正", rej.getRemark());
    }

    // ---- 记人格子：立单人不动，操作人逐笔实记 ----

    @Test
    void makerStaysFixedWhileEachSignRowRecordsActualOperator() {
        Long id = open().getId();
        service.approve(id, "业委张三", null);
        service.approve(id, "业委李四", null);
        service.approve(id, "事务所王五", null);
        service.approve(id, "中心赵六", null);

        assertEquals("立单经手人甲", service.selectTHmfBudgetBillById(id).getMaker(),
                "起单到归档立单经手人不换");
        List<String> signers = signs.all().stream()
                .filter(s -> s.getAction() == ACT_SIGN)
                .map(THmfBudgetSign::getSigner)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("业委张三", "业委李四", "事务所王五", "中心赵六"), signers,
                "后一格谁实际操作续谁的名，不许笼统写成固定一人");
        assertFalse(signers.stream().allMatch("立单经手人甲"::equals));
    }

    // ---- 同小区同年：另起新单，互不并入 ----

    @Test
    void sameSiteSameYearOpensSeparateBillAndSignaturesDoNotMerge() {
        THmfBudgetBill first = service.openBill("BG-2026-001", "甲", "JZ00", 2026,
                new BigDecimal("100.00"), null);
        THmfBudgetBill second = service.openBill("BG-2026-002", "甲", "JZ00", 2026,
                new BigDecimal("200.00"), null);
        assertNotNull(first);
        assertNotNull(second, "同年再来一张：另起新单");
        assertFalse(first.getId().equals(second.getId()));

        // 第一张签了一笔业委会
        service.approve(first.getId(), "业委张三", null);
        // 第二张从头候签，签认条数不并到旧单
        assertEquals(1, signsOf(first.getId(), NODE_COMMITTEE, null, ACT_SIGN).size());
        assertEquals(0, signsOf(second.getId(), NODE_COMMITTEE, null, ACT_SIGN).size());
        assertEquals(0, service.selectTHmfBudgetBillById(second.getId()).getSignCount());
        assertEquals(0, new BigDecimal("200.00").compareTo(
                service.selectTHmfBudgetBillById(second.getId()).getPayAmt()));
    }

    // ---- 拨付额：库存与屏显同源，流转中不被改 ----

    @Test
    void payAmtComesFromApprovedAmountOnceAndNeverChanges() {
        Long id = walkToCenter();
        assertEquals(0, new BigDecimal("128000.00")
                .compareTo(service.selectTHmfBudgetBillById(id).getPayAmt()));
        service.approve(id, "中心赵六", null);
        assertEquals(0, new BigDecimal("128000.00")
                .compareTo(service.selectTHmfBudgetBillById(id).getPayAmt()),
                "签批全程无手填改动，库内存的就是审定额取数");
    }

    // ---- 复查：从中心倒着点回业委会，逐档咬合 ----

    @Test
    void signTrailListsCenterToCommitteeAndEveryStageRechecks() {
        Long id = walkToCenter();
        service.reject(id, "中心赵六", null);          // 中心退回，留一笔打回
        service.approve(id, "事务所王五", null);       // 事务所第2拨
        service.approve(id, "中心赵六", null);         // 中心第2拨封单

        List<THmfBudgetSign> trail = service.listSignTrail(id);
        assertNotNull(trail);
        // 倒序：中心(2) → 事务所(1) → 业委会(0)
        for (int i = 1; i < trail.size(); i++) {
            assertTrue(trail.get(i - 1).getNodeNo() >= trail.get(i).getNodeNo());
        }
        // 每一档单独点也经得起复核，且与逐笔留痕条数咬合
        assertEquals(2, signsOf(id, NODE_COMMITTEE, null, ACT_SIGN).size());
        long firmRounds = signs.all().stream()
                .filter(s -> s.getBillId().equals(id) && s.getNodeNo() == NODE_FIRM)
                .filter(s -> s.getAction() == ACT_SIGN)
                .map(THmfBudgetSign::getRoundNo).distinct().count();
        assertEquals(2, firmRounds, "事务所新旧两拨都在");
        assertEquals(1, signsOf(id, NODE_CENTER, null, ACT_REJECT).size());
        assertEquals(1, signsOf(id, NODE_CENTER, null, ACT_SIGN).size(),
                "中心第1拨是打回、第2拨才签认，签认只1笔");
        // 全部留痕条数：业委2 + 事务所(拨1+拨2)2 + 中心签认1 + 中心打回1 = 6
        assertEquals(6, trail.size());
    }

    @Test
    void listSignTrailForMissingBillReturnsNull() {
        assertNull(service.listSignTrail(404L));
    }

    /** 业委两笔 + 事务所一笔，把单送到中心档候签。 */
    private Long walkToCenter() {
        Long id = open().getId();
        service.approve(id, "业委张三", null);
        service.approve(id, "业委李四", null);
        service.approve(id, "事务所王五", null);
        return id;
    }
}
