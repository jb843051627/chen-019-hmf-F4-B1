package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfPayBook;
import com.fc.v2.model.auto.THmfPayRow;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.model.custom.PayRowDiff;
import com.fc.v2.service.impl.THmfPayRowServiceImpl;

/**
 * 《册子核销口径》逐条核对：
 * 册次进门定死、行次自占、销账认户号、户面累加不覆盖、超额行就地挂起、同册别行照走、
 * 同号第二遍只比对不改、超限整册退回一行不留、全册不能核写空册不写核讫、
 * 挂起连跨两个对账季挪往期、季末含当日算本期、新册不受往期牵住、撤册另走手续、
 * 回执三数同源且销+挂=本、接不平当场列差行、核讫却查无册不认。
 */
class PayBookReconcileTest {

    // 行去向
    private static final int PENDING = 0;
    private static final int CLEARED = 1;
    private static final int HELD = 2;

    // 所在层
    private static final int LAYER_CURRENT = 0;
    private static final int LAYER_ARCHIVED = 1;

    // 回执结果
    private static final int RESULT_CHECKED = 1;
    private static final int RESULT_EMPTY = 2;

    private THmfPayRowServiceImpl service;
    private FakePayBookMapper books;
    private FakePayRowMapper rows;
    private FakeAcctCardMapper cards;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfPayRowServiceImpl();
        this.books = new FakePayBookMapper();
        this.rows = new FakePayRowMapper();
        this.cards = new FakeAcctCardMapper();
        inject("hmfPayBookMapper", books);
        inject("hmfPayRowMapper", rows);
        inject("hmfAcctCardMapper", cards);
        // 三户：甲乙应缴各 14000，丙应缴 5000
        card("FH-JIA", new BigDecimal("14000.00"));
        card("FH-YI", new BigDecimal("14000.00"));
        card("FH-BING", new BigDecimal("5000.00"));
    }

    private void inject(String name, Object value) throws Exception {
        Field f = THmfPayRowServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private void card(String acctNo, BigDecimal fineAmt) {
        THmfAcctCard c = new THmfAcctCard();
        c.setBillNo(acctNo);
        c.setSiteNo("JZ00");
        c.setNodeNo(1);
        c.setFineAmt(fineAmt);
        c.setDelFlag(0);
        cards.insert(c);
    }

    private Date day(String text) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private THmfPayRow row(String acct, String qty, String payDate) {
        THmfPayRow r = new THmfPayRow();
        r.setItemCode(acct);
        r.setQty(new BigDecimal(qty));
        r.setPayDate(day(payDate));
        return r;
    }

    private List<THmfPayRow> rows(String batchNo, Integer status) {
        return rows.all().stream()
                .filter(r -> batchNo.equals(r.getBatchNo()))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .collect(Collectors.toList());
    }

    // ---- 进门两件事：册次定死、行次自占 ----

    @Test
    void firstSubmission_fixesBatchNoAndAutoRowNumbers_andWritesHeadCountsOnce() {
        List<THmfPayRow> in = new ArrayList<>();
        in.add(row("FH-JIA", "8000.00", "2026-01-05"));
        in.add(row("FH-YI", "6000.00", "2026-01-06"));
        PayBookReceipt rc = service.reconcilePayBook("CE-2026-01", in);

        assertEquals(PayBookReceipt.CODE_ACCEPTED, rc.getCode());
        THmfPayBook book = rc.getBook();
        assertNotNull(book.getId());
        assertEquals("CE-2026-01", book.getBatchNo());
        // 回执三数、册首三数、逐行点出来的同出一回
        assertEquals(2, rc.getTotalRows());
        assertEquals(2, rc.getClearedRows());
        assertEquals(0, rc.getHeldRows());
        assertEquals(2, book.getTotalRows().intValue());
        assertEquals(2, book.getClearedRows().intValue());
        assertEquals(0, book.getHeldRows().intValue());
        assertEquals(RESULT_CHECKED, book.getResult().intValue());
        assertEquals("核讫", rc.getResultText());
        // 行次按先后自动排定，册次号钉在每行上
        List<THmfPayRow> stored = service.listCurrentRows("CE-2026-01");
        assertEquals(2, stored.size());
        assertEquals(1, stored.get(0).getRowNo().intValue());
        assertEquals(2, stored.get(1).getRowNo().intValue());
        assertEquals("CE-2026-01", stored.get(0).getBatchNo());
        assertEquals("2026Q1", stored.get(0).getFromSeason());
    }

    @Test
    void multipleBooksSameMonth_neverMix() {
        service.reconcilePayBook("CE-A", java.util.Collections.singletonList(
                row("FH-JIA", "100.00", "2026-01-10")));
        service.reconcilePayBook("CE-B", java.util.Collections.singletonList(
                row("FH-YI", "200.00", "2026-01-11")));
        assertEquals(1, service.listCurrentRows("CE-A").size());
        assertEquals(1, service.listCurrentRows("CE-B").size());
        assertEquals("FH-JIA", service.listCurrentRows("CE-A").get(0).getItemCode());
    }

    // ---- 去向：认户号、累加不覆盖、超额就地挂起 ----

    @Test
    void sameAccountTwoPayments_accumulate_notOverwrite_evenAcrossBooks() {
        // 去年第二件事：第二笔把第一笔盖掉，户面只剩一半。这里必须累加。
        service.reconcilePayBook("CE-1", java.util.Collections.singletonList(
                row("FH-JIA", "8000.00", "2026-01-05")));
        PayBookReceipt rc2 = service.reconcilePayBook("CE-2", java.util.Collections.singletonList(
                row("FH-JIA", "6000.00", "2026-02-05")));
        assertEquals(CLEARED, rc2.getRows().get(0).getStatus().intValue(), "8000+6000=14000，正好缴清，第二笔也销");
        assertEquals(14000, rc2.getRows().get(0).getQty().add(
                rows("CE-1", CLEARED).get(0).getQty()).doubleValue(), 0.001);
    }

    @Test
    void paymentExceedingDue_isHeldInPlace_withReason_otherRowsGoOn() {
        List<THmfPayRow> in = new ArrayList<>();
        in.add(row("FH-JIA", "8000.00", "2026-01-05"));   // 第1行：销
        in.add(row("FH-JIA", "7000.00", "2026-01-09"));   // 第2行：8000+7000>14000，挂起
        in.add(row("FH-YI", "6000.00", "2026-01-10"));    // 第3行：别的户照销不误
        PayBookReceipt rc = service.reconcilePayBook("CE-X", in);

        List<THmfPayRow> judged = rc.getRows();
        assertEquals(1, judged.get(0).getRowNo().intValue());
        assertEquals(CLEARED, judged.get(0).getStatus().intValue());
        assertEquals(2, judged.get(1).getRowNo().intValue(), "挂起行也占着它原来的行次");
        assertEquals(HELD, judged.get(1).getStatus().intValue());
        assertTrue(judged.get(1).getHoldReason().contains("超过应缴"));
        assertTrue(judged.get(1).getHoldReason().contains("FH-JIA"));
        assertEquals(CLEARED, judged.get(2).getStatus().intValue(), "同一册其余各行照走，不退回整册重来");
        assertEquals(3, rc.getTotalRows());
        assertEquals(2, rc.getClearedRows());
        assertEquals(1, rc.getHeldRows());
        // 挂起那笔不进户面：后来同户一笔 6000，户面底数仍是 8000，合 14000 照销
        PayBookReceipt later = service.reconcilePayBook("CE-Y", java.util.Collections.singletonList(
                row("FH-JIA", "6000.00", "2026-02-01")));
        assertEquals(CLEARED, later.getRows().get(0).getStatus().intValue());
    }

    @Test
    void unknownAccount_heldNotCleared_nameAlikeDoesNotCount() {
        PayBookReceipt rc = service.reconcilePayBook("CE-U", java.util.Collections.singletonList(
                row("FH-NOT-EXIST", "100.00", "2026-01-05")));
        assertEquals(HELD, rc.getRows().get(0).getStatus().intValue(), "户号对不上分户底册，名字再像也不销");
        assertTrue(rc.getRows().get(0).getHoldReason().contains("对不上分户底册"));
    }

    // ---- 同号第二遍：只比对，不改账 ----

    @Test
    void sameBatchSecondTime_comparesOnly_noChangeEvenWhenIdentical() {
        List<THmfPayRow> first = new ArrayList<>();
        first.add(row("FH-JIA", "8000.00", "2026-01-05"));
        first.add(row("FH-YI", "14000.00", "2026-01-06"));
        service.reconcilePayBook("CE-DUP", first);
        int rowCountAfterFirst = rows.all().size();

        // 银行换了人手三天里报第二回：一模一样
        List<THmfPayRow> again = new ArrayList<>();
        again.add(row("FH-JIA", "8000.00", "2026-01-05"));
        again.add(row("FH-YI", "14000.00", "2026-01-06"));
        PayBookReceipt rc = service.reconcilePayBook("CE-DUP", again);

        assertEquals(PayBookReceipt.CODE_COMPARED, rc.getCode());
        assertTrue(rc.getDiffs().isEmpty());
        assertEquals(2, rc.getTotalRows(), "头一遍的结果原样回话");
        assertEquals(2, rc.getClearedRows());
        // 去年第一件事：第二回把已销的行拨回未销——这里已销的行必须原样是已销
        assertEquals(2, rows("CE-DUP", CLEARED).size());
        assertEquals(0, rows("CE-DUP", PENDING).size());
        assertEquals(rowCountAfterFirst, rows.all().size(), "不加一行、不改一行");
        assertNotNull(service.selectPayBook("CE-DUP"), "头一遍的册查得到，不会出现回执有、系统无册");
    }

    @Test
    void sameBatchSecondTime_differencesListedRowByRow_stillUntouched() {
        service.reconcilePayBook("CE-DIF", new ArrayList<>(java.util.Arrays.asList(
                row("FH-JIA", "8000.00", "2026-01-05"),
                row("FH-YI", "6000.00", "2026-01-06"))));

        // 第二回：第2行金额改了，还多出第3行
        PayBookReceipt changed = service.reconcilePayBook("CE-DIF", new ArrayList<>(java.util.Arrays.asList(
                row("FH-JIA", "8000.00", "2026-01-05"),
                row("FH-YI", "9999.00", "2026-01-06"),
                row("FH-BING", "10.00", "2026-01-07"))));
        assertEquals(PayBookReceipt.CODE_COMPARED, changed.getCode());
        List<PayRowDiff> diffs = changed.getDiffs();
        assertEquals(2, diffs.size());
        assertEquals(2, diffs.get(0).getRowNo().intValue());
        assertEquals(PayRowDiff.KIND_CHANGED, diffs.get(0).getKind());
        assertEquals(3, diffs.get(1).getRowNo().intValue());
        assertEquals(PayRowDiff.KIND_ADDED, diffs.get(1).getKind());
        // 库里头一遍的第2行金额原样，多的第3行不落库
        THmfPayRow storedRow2 = service.listCurrentRows("CE-DIF").stream()
                .filter(r -> r.getRowNo() == 2).findFirst().get();
        assertEquals(0, new BigDecimal("6000.00").compareTo(storedRow2.getQty()));
        assertEquals(2, service.listCurrentRows("CE-DIF").size());

        // 第二回缺行：第2行没递来，逐行列回
        PayBookReceipt shortOne = service.reconcilePayBook("CE-DIF",
                java.util.Collections.singletonList(row("FH-JIA", "8000.00", "2026-01-05")));
        assertEquals(1, shortOne.getDiffs().size());
        assertEquals(PayRowDiff.KIND_MISSING, shortOne.getDiffs().get(0).getKind());
        assertEquals(2, shortOne.getDiffs().get(0).getRowNo().intValue());
    }

    @Test
    void changingAFiledBook_requiresWithdrawFirst() {
        service.reconcilePayBook("CE-W", java.util.Collections.singletonList(
                row("FH-JIA", "8000.00", "2026-01-05")));
        // 不撤册，怎么报第二遍都不改
        service.reconcilePayBook("CE-W", java.util.Collections.singletonList(
                row("FH-JIA", "14000.00", "2026-01-05")));
        assertEquals(0, new BigDecimal("8000.00").compareTo(
                service.listCurrentRows("CE-W").get(0).getQty()));

        // 先走撤册手续，同号才能重新进门核销
        THmfPayBook withdrawn = service.withdrawPayBook("CE-W", "柜员乙", "银行报数有误");
        assertEquals(1, withdrawn.getStatus().intValue());
        PayBookReceipt fresh = service.reconcilePayBook("CE-W", java.util.Collections.singletonList(
                row("FH-JIA", "14000.00", "2026-01-20")));
        assertEquals(PayBookReceipt.CODE_ACCEPTED, fresh.getCode());
        assertEquals(1, service.listCurrentRows("CE-W").size());
        assertEquals(0, new BigDecimal("14000.00").compareTo(
                service.listCurrentRows("CE-W").get(0).getQty()));
        assertNull(service.withdrawPayBook("CE-404", null, null), "没在册的册撤不了");
    }

    // ---- 兜底：整册超限，一行不留 ----

    @Test
    void overLimitWholeBookReturned_zeroRowKept() {
        List<THmfPayRow> big = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            big.add(row("FH-JIA", "1.00", "2026-01-05"));
        }
        PayBookReceipt rc = service.reconcilePayBook("CE-BIG", big);
        assertEquals(PayBookReceipt.CODE_REJECTED, rc.getCode());
        assertEquals(0, rows.all().size(), "原册退回，一行不留");
        assertEquals(0, books.all().size(), "册头也不留");
        assertNull(service.selectPayBook("CE-BIG"));
    }

    @Test
    void invalidRowWholeBookNotAccepted() {
        List<THmfPayRow> bad = new ArrayList<>();
        bad.add(row("FH-JIA", "8000.00", "2026-01-05"));
        THmfPayRow noDate = row("FH-YI", "100.00", "2026-01-06");
        noDate.setPayDate(null);
        bad.add(noDate);
        assertNull(service.reconcilePayBook("CE-BAD", bad), "缺交存日期判不起来，整册不收");
        assertEquals(0, rows.all().size());
        assertNull(service.reconcilePayBook("  ", new ArrayList<>()), "空册次号不收");
    }

    // ---- 空册：全册没有一行能核，只许写空册 ----

    @Test
    void noRowCleared_receiptSaysEmptyNotChecked() {
        PayBookReceipt rc = service.reconcilePayBook("CE-EMPTY", new ArrayList<>(java.util.Arrays.asList(
                row("FH-NO1", "100.00", "2026-01-05"),
                row("FH-NO2", "200.00", "2026-01-06"))));
        assertEquals(0, rc.getClearedRows());
        assertEquals(2, rc.getHeldRows());
        assertEquals(RESULT_EMPTY, rc.getBook().getResult().intValue());
        assertEquals("空册", rc.getResultText(), "核讫是报成绩的词，空册不配");
        assertTrue(rc.getMessage().contains("空册"));
        // 空册也是正经进门的一册，销+挂仍等于本
        assertTrue(service.auditPayBook("CE-EMPTY").isPass());
    }

    @Test
    void oneRowCleared_receiptSaysChecked() {
        PayBookReceipt rc = service.reconcilePayBook("CE-OK", new ArrayList<>(java.util.Arrays.asList(
                row("FH-JIA", "8000.00", "2026-01-05"),
                row("FH-NO", "1.00", "2026-01-06"))));
        assertEquals(RESULT_CHECKED, rc.getBook().getResult().intValue());
        assertEquals("核讫", rc.getResultText());
    }

    // ---- 本行到账金额：归一到库口 decimal(12,2)，算钱的底子两处同源 ----

    @Test
    void arriveQty_normalizedToDbScale_beforeClearing() {
        // 8000.005 进门四舍五入到 8000.01 再销账，户面累加也只认归一后的数
        PayBookReceipt rc = service.reconcilePayBook("CE-QTY",
                java.util.Collections.singletonList(row("FH-JIA", "8000.005", "2026-01-05")));
        THmfPayRow stored = service.listCurrentRows("CE-QTY").get(0);
        assertEquals(0, new BigDecimal("8000.01").compareTo(stored.getQty()));
        assertEquals(2, stored.getQty().scale());
        assertEquals(CLEARED, stored.getStatus().intValue());
        assertEquals(0, new BigDecimal("8000.01").compareTo(rc.getRows().get(0).getQty()));
    }

    // ---- 挂起连跨两个对账季挪往期；季末含当日 ----

    @Test
    void seasonBoundary_lastDayOfQuarterBelongsToCurrentSeason() {
        THmfPayRow q1Last = row("FH-JIA", "8000.00", "2026-03-31");
        PayBookReceipt rc = service.reconcilePayBook("CE-Q", java.util.Collections.singletonList(q1Last));
        assertEquals("2026Q1", rc.getRows().get(0).getFromSeason(),
                "日期赶上月底（季末）最后一天算本期，别漏算到下一季");

        THmfPayRow q2First = row("FH-JIA", "10.00", "2026-04-01");
        // 8000 已销 + 10 不超额
        PayBookReceipt rc2 = service.reconcilePayBook("CE-Q2", java.util.Collections.singletonList(q2First));
        assertEquals("2026Q2", rc2.getRows().get(0).getFromSeason());
    }

    @Test
    void heldRow_crossingTwoSeasons_movesToArchived_newBookNotTied() {
        // 一笔超额挂起，交存日落在 2026Q1
        service.reconcilePayBook("CE-HOLD", new ArrayList<>(java.util.Arrays.asList(
                row("FH-BING", "5000.00", "2026-01-10"),
                row("FH-BING", "10.00", "2026-01-11"))));
        THmfPayRow held = service.listCurrentRows("CE-HOLD").stream()
                .filter(r -> r.getStatus() == HELD).findFirst().get();
        assertEquals(2, held.getRowNo().intValue(), "挂起行带着它原来排第几");

        // 还在紧接的下一季（Q2）：不挪
        assertEquals(0, service.sweepHeldToArchived(day("2026-06-30")));
        assertEquals(2, service.listCurrentRows("CE-HOLD").size());
        assertEquals(0, service.listArchivedRows("CE-HOLD").size());

        // 连着跨过两个对账季（到 Q3）：挪往期，现行册面不再露，往期翻得到、行次不丢
        assertEquals(1, service.sweepHeldToArchived(day("2026-07-01")));
        assertEquals(1, service.listCurrentRows("CE-HOLD").size(), "现行册面不露它");
        List<THmfPayRow> archived = service.listArchivedRows("CE-HOLD");
        assertEquals(1, archived.size());
        assertEquals(2, archived.get(0).getRowNo().intValue());
        assertEquals(LAYER_ARCHIVED, archived.get(0).getLayer().intValue());
        assertEquals(HELD, archived.get(0).getStatus().intValue(), "只挪层，去向不改");
        // 再扫不重复挪
        assertEquals(0, service.sweepHeldToArchived(day("2026-10-01")));

        // 明年开新册不受它牵住：往期挂起行不进户面，同户新册子照常理账
        card("FH-BING2", new BigDecimal("5000.00"));
        PayBookReceipt fresh = service.reconcilePayBook("CE-NEWYEAR",
                java.util.Collections.singletonList(row("FH-BING2", "5000.00", "2027-01-05")));
        assertEquals(CLEARED, fresh.getRows().get(0).getStatus().intValue());
    }

    // ---- 末了一笔对账：三数同源、接不平列差行、查无册不认 ----

    @Test
    void audit_balancedPasses_clearedPlusHeldEqualsTotal() {
        service.reconcilePayBook("CE-AUD", new ArrayList<>(java.util.Arrays.asList(
                row("FH-JIA", "8000.00", "2026-01-05"),
                row("FH-JIA", "9000.00", "2026-01-06"))));
        PayBookAudit a = service.auditPayBook("CE-AUD");
        assertTrue(a.isPass(), a.getReason());
        assertEquals(2, a.getTotalRows());
        assertEquals(1, a.getClearedRows());
        assertEquals(1, a.getHeldRows());
    }

    @Test
    void audit_receiptSaysCheckedButBookMissing_notRecognized() {
        // 去年第三件事：回执写着核讫，系统里查不到那一册
        PayBookAudit a = service.auditPayBook("CE-GHOST");
        assertFalse(a.isPass());
        assertTrue(a.getReason().contains("查不到"));
    }

    @Test
    void audit_headCountTampered_listsTheBadRows_notRoundNumber() {
        service.reconcilePayBook("CE-TAMP", new ArrayList<>(java.util.Arrays.asList(
                row("FH-JIA", "8000.00", "2026-01-05"),
                row("FH-YI", "6000.00", "2026-01-06"))));
        // 册首那个数被人手拨成 9
        THmfPayBook head = service.selectPayBook("CE-TAMP");
        head.setTotalRows(9);
        books.updateById(head);

        PayBookAudit a = service.auditPayBook("CE-TAMP");
        assertFalse(a.isPass());
        assertFalse(a.getBadRows().isEmpty(), "差在哪几行当场列出来，不许拿成数搪塞");
        assertTrue(a.getReason().contains("1"));
    }

    @Test
    void audit_rowStuckPending_balanceFails() {
        service.reconcilePayBook("CE-PEND", new ArrayList<>(java.util.Arrays.asList(
                row("FH-JIA", "8000.00", "2026-01-05"),
                row("FH-YI", "6000.00", "2026-01-06"))));
        // 有一行被拨回未销：销+挂不再等于本
        THmfPayRow first = service.listCurrentRows("CE-PEND").get(0);
        first.setStatus(PENDING);
        rows.updateById(first);

        PayBookAudit a = service.auditPayBook("CE-PEND");
        assertFalse(a.isPass(), "未销/已销/挂起只能落一个去向，停在未销即对不平");
        assertEquals(1, a.getBadRows().size());
    }
}
