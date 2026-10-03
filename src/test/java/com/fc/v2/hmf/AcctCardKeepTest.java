package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfArch;
import com.fc.v2.service.impl.THmfAcctCardServiceImpl;

/**
 * 《分户立户单口径》逐条核对：
 * 明细页与详情同一把尺（未删即见，不暗加门槛、不私开分页）、立户挂底册代号照底册写、
 * 面积应缴归一到库口精度、压线计入上一层、底册缺线不搅断立户、条目动过档次重折、
 * 换挂底册代号跟着走、单号撞了不收、删除只销记不抹张。
 */
class AcctCardKeepTest {

    private THmfAcctCardServiceImpl service;
    private FakeAcctCardMapper cards;
    private FakeArchMapper archs;

    private int archOk;
    private int archGone;
    private int archNoLine;
    private int archTight;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfAcctCardServiceImpl();
        this.cards = new FakeAcctCardMapper();
        this.archs = new FakeArchMapper();
        inject("baseMapper", cards);
        inject("hmfArchMapper", archs);
        this.archOk = arch("JZ00", "90", "144", "300", 0);
        this.archGone = arch("JZ01", "90", "144", "300", 1);
        this.archNoLine = arch("JZ02", null, null, null, 0);
        this.archTight = arch("JZ03", "50", "100", "200", 0);
    }

    private void inject(String name, Object value) throws Exception {
        Class<?> c = service.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(service, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private int arch(String siteNo, String th1, String th2, String th3, int status) {
        THmfArch a = new THmfArch();
        a.setSiteNo(siteNo);
        a.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        a.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        a.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        a.setStatus(status);
        a.setDelFlag(0);
        archs.insert(a);
        return a.getId().intValue();
    }

    private THmfAcctCard card(String billNo, int siteId, String qty, String fineAmt, int status) {
        THmfAcctCard c = new THmfAcctCard();
        c.setBillNo(billNo);
        c.setSiteId(siteId);
        c.setQty(qty == null ? null : new BigDecimal(qty));
        c.setFineAmt(fineAmt == null ? null : new BigDecimal(fineAmt));
        c.setStatus(status);
        return c;
    }

    private THmfAcctCard stored(String billNo) {
        return cards.all().stream()
                .filter(c -> billNo.equals(c.getBillNo()))
                .findFirst().orElse(null);
    }

    // ---- 明细页与详情同一把尺 ----

    @Test
    void listAndDetail_shareSameRule_noHiddenGate_noPrivatePaging() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        assertEquals(1, service.insertTHmfAcctCard(card("FH-2", archOk, "90", "1000", 1)));
        assertEquals(1, service.insertTHmfAcctCard(card("FH-3", archOk, "100", "1000", 2)));

        // 未删即见：待核对/已核对/已冻住都翻得到，不再暗加"待核对"门槛
        List<THmfAcctCard> all = service.selectTHmfAcctCardList(new QueryWrapper<THmfAcctCard>());
        assertEquals(3, all.size());
        // 详情那头同一把尺：冻住的也查得到
        assertNotNull(service.selectTHmfAcctCardById(stored("FH-3").getId()));
        // 调用方递来的条件照走
        List<THmfAcctCard> one = service.selectTHmfAcctCardList(
                new QueryWrapper<THmfAcctCard>().eq("bill_no", "FH-2"));
        assertEquals(1, one.size());
        assertEquals("FH-2", one.get(0).getBillNo());
    }

    // ---- 立户：挂底册、代号照底册写、单号不重 ----

    @Test
    void insert_hangsOnArch_copiesSiteNo_rejectsGoneOrMissingArch() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        assertEquals("JZ00", stored("FH-1").getSiteNo(), "代号照底册写，不许各写各的");
        assertEquals(0, service.insertTHmfAcctCard(card("FH-2", archGone, "80", "1000", 0)), "底册已迁出挂不上");
        assertEquals(0, service.insertTHmfAcctCard(card("FH-3", 99, "80", "1000", 0)), "查无底册挂不上");
        assertEquals(1, cards.all().size());
    }

    @Test
    void insert_duplicateBillNoRejected() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        assertEquals(0, service.insertTHmfAcctCard(card("FH-1", archOk, "90", "2000", 0)));
        assertEquals(1, cards.all().size());
    }

    // ---- 存量数据进门：两处一个尺度 ----

    @Test
    void insert_normalizesAmountsToDbScale() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "100.005", "8000.005", 0)));
        THmfAcctCard c = stored("FH-1");
        assertEquals(0, new BigDecimal("100.01").compareTo(c.getQty()));
        assertEquals(2, c.getQty().scale());
        assertEquals(0, new BigDecimal("8000.01").compareTo(c.getFineAmt()));
        assertEquals(2, c.getFineAmt().scale());
    }

    // ---- 折档：压线计入上一层；底册缺线不搅断 ----

    @Test
    void insert_gradeBand_equalToLimitCountsUpperTier() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-89", archOk, "89.99", "1", 0)));
        assertEquals(1, stored("FH-89").getGradeLevel().intValue());
        assertEquals(1, service.insertTHmfAcctCard(card("FH-90", archOk, "90", "1", 0)));
        assertEquals(2, stored("FH-90").getGradeLevel().intValue(), "压着线计入上一层");
        assertEquals(1, service.insertTHmfAcctCard(card("FH-144", archOk, "144", "1", 0)));
        assertEquals(3, stored("FH-144").getGradeLevel().intValue());
        assertEquals(1, service.insertTHmfAcctCard(card("FH-300", archOk, "300", "1", 0)));
        assertEquals(4, stored("FH-300").getGradeLevel().intValue());
    }

    @Test
    void insert_archMissingThresholds_gradeBlankButInsertGoesOn() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-NL", archNoLine, "100", "1000", 0)),
                "底册缺线不许搅断立户");
        assertNull(stored("FH-NL").getGradeLevel(), "判不出来档次留空");
    }

    // ---- 条目动过：档次重折、换挂底册代号跟着走 ----

    @Test
    void update_recomputesGrade_notLeavingStaleOne() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        assertEquals(1, stored("FH-1").getGradeLevel().intValue());

        THmfAcctCard edit = new THmfAcctCard();
        edit.setId(stored("FH-1").getId());
        edit.setQty(new BigDecimal("200"));
        assertEquals(1, service.updateTHmfAcctCard(edit));
        assertEquals(3, stored("FH-1").getGradeLevel().intValue(), "面积动过，档次跟着重折，不留旧档");
        assertEquals(0, new BigDecimal("200.00").compareTo(stored("FH-1").getQty()));
    }

    @Test
    void update_rehangArch_siteNoFollows_andGradeRefoldsByNewArch() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "200", "1000", 0)));
        assertEquals(3, stored("FH-1").getGradeLevel().intValue());

        THmfAcctCard edit = new THmfAcctCard();
        edit.setId(stored("FH-1").getId());
        edit.setSiteId(archTight);
        assertEquals(1, service.updateTHmfAcctCard(edit));
        THmfAcctCard after = stored("FH-1");
        assertEquals("JZ03", after.getSiteNo(), "换挂底册，代号跟着新底册走，不许一头改一头照旧");
        assertEquals(4, after.getGradeLevel().intValue(), "照新底册的三线重折");

        // 换挂到迁出/查无的底册：不收
        THmfAcctCard bad = new THmfAcctCard();
        bad.setId(after.getId());
        bad.setSiteId(archGone);
        assertEquals(0, service.updateTHmfAcctCard(bad));
        bad.setSiteId(99);
        assertEquals(0, service.updateTHmfAcctCard(bad));
    }

    @Test
    void update_duplicateBillNoRejected_andMissingCardReturnsZero() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        assertEquals(1, service.insertTHmfAcctCard(card("FH-2", archOk, "90", "1000", 0)));
        THmfAcctCard edit = new THmfAcctCard();
        edit.setId(stored("FH-2").getId());
        edit.setBillNo("FH-1");
        assertEquals(0, service.updateTHmfAcctCard(edit), "单号撞上别户不收");
        THmfAcctCard ghost = new THmfAcctCard();
        ghost.setId(999999L);
        assertEquals(0, service.updateTHmfAcctCard(ghost));
    }

    // ---- 删除：只销记不抹张 ----

    @Test
    void delete_marksOnly_recordStillThere_butOutOfListAndDetail() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        Long id = stored("FH-1").getId();

        assertEquals(1, service.deleteTHmfAcctCardById(id));
        assertNull(service.selectTHmfAcctCardById(id), "销记后详情翻不到");
        assertTrue(service.selectTHmfAcctCardList(new QueryWrapper<THmfAcctCard>()).isEmpty(),
                "销记后明细页翻不到");
        assertEquals(1, cards.all().size(), "张还在，只销记不抹张");
        assertEquals(1, cards.all().get(0).getDelFlag().intValue());
        assertEquals(0, service.deleteTHmfAcctCardById(id), "销过的再销不算数");
    }

    @Test
    void deleteBatch_marksEach_andSkipsBlanks() {
        assertEquals(1, service.insertTHmfAcctCard(card("FH-1", archOk, "80", "1000", 0)));
        assertEquals(1, service.insertTHmfAcctCard(card("FH-2", archOk, "90", "1000", 0)));
        Long id1 = stored("FH-1").getId();
        Long id2 = stored("FH-2").getId();
        assertEquals(2, service.deleteTHmfAcctCardByIds(id1 + "," + id2));
        assertEquals(2, cards.all().size());
        assertEquals(0, service.deleteTHmfAcctCardByIds(" "));
    }
}
