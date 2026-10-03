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
 * 《分户账立户单口径》逐条核对：
 * 列表/详情同一把尺（未删都露、条件照收、不另塞状态分页）、
 * 底册头只认在册未删且只取一遍（迁出/删过不认、缺阈值不搅断）、
 * 改底册/改面积档次跟着走、户号冻结不抖底下挂着的账、
 * 删除只翻标记不抹张、录入—退出—重新进入同号结果可重现。
 */
class AcctCardLinkageTest {

    private THmfAcctCardServiceImpl service;
    private FakeAcctCardMapper cards;
    private FakeArchMapper archs;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfAcctCardServiceImpl();
        this.cards = new FakeAcctCardMapper();
        this.archs = new FakeArchMapper();
        inject("baseMapper", cards);
        inject("hmfArchMapper", archs);
        arch("JZ-A", 0, 0, "90", "144", "300");
        arch("JZ-B", 1, 0, "90", "144", "300");
        THmfArch gone = arch("JZ-C", 0, 0, "90", "144", "300");
        gone.setDelFlag(1);
        archs.updateById(gone);
    }

    private void inject(String name, Object value) throws Exception {
        Class<?> c = THmfAcctCardServiceImpl.class;
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

    private THmfArch arch(String siteNo, int status, int del, String th1, String th2, String th3) {
        THmfArch a = new THmfArch();
        a.setSiteNo(siteNo);
        a.setStatus(status);
        a.setDelFlag(del);
        a.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        a.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        a.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        archs.insert(a);
        return a;
    }

    private int archIdOf(String siteNo) {
        return archs.all().stream().filter(a -> siteNo.equals(a.getSiteNo()))
                .findFirst().get().getId().intValue();
    }

    private THmfAcctCard card(String billNo, String siteNo, String qty) {
        THmfAcctCard c = new THmfAcctCard();
        c.setBillNo(billNo);
        c.setSiteId(archIdOf(siteNo));
        c.setQty(new BigDecimal(qty));
        return c;
    }

    // ---- 立户：底册尺度一处、阈值缺值不搅断 ----

    @Test
    void insert_activeArch_ok_andGradeFollowsThresholds() {
        THmfAcctCard c = card("FH-1", "JZ-A", "90"); // 等于上限进一层
        assertEquals(1, service.insertTHmfAcctCard(c));
        THmfAcctCard saved = service.selectTHmfAcctCardById(c.getId());
        assertNotNull(saved);
        assertEquals("JZ-A", saved.getSiteNo());
        assertEquals(1, saved.getGradeLevel().intValue(), "等于 90 计入一层");
        assertEquals(0, saved.getStatus().intValue(), "新单落待核对");
        assertEquals(0, saved.getDelFlag().intValue());
        assertEquals("FH-1", saved.getCreateBy());
    }

    @Test
    void insert_migratedOrDeletedArch_rejected() {
        assertEquals(0, service.insertTHmfAcctCard(card("FH-2", "JZ-B", "50")), "已迁出底册挂不上");
        assertEquals(0, service.insertTHmfAcctCard(card("FH-3", "JZ-C", "50")), "删过的底册挂不上");
        THmfAcctCard nowhere = card("FH-4", "JZ-A", "50");
        nowhere.setSiteId(999999);
        assertEquals(0, service.insertTHmfAcctCard(nowhere), "查无底册挂不上");
    }

    @Test
    void insert_blankBillNoOrDuplicate_rejected() {
        THmfAcctCard c = card("FH-1", "JZ-A", "50");
        assertEquals(1, service.insertTHmfAcctCard(c));
        assertEquals(0, service.insertTHmfAcctCard(card("FH-1", "JZ-A", "60")), "同号不许再立");
        THmfAcctCard blank = card("  ", "JZ-A", "50");
        assertEquals(0, service.insertTHmfAcctCard(blank));
        assertEquals(1, cards.all().size());
    }

    @Test
    void insert_nullThresholds_doesNotBlowUp_gradeZero() {
        THmfArch loose = arch("JZ-D", 0, 0, null, null, null);
        THmfAcctCard c = new THmfAcctCard();
        c.setBillNo("FH-9");
        c.setSiteId(loose.getId().intValue());
        c.setQty(new BigDecimal("120"));
        assertEquals(1, service.insertTHmfAcctCard(c), "缺阈值的底册也立得了单，不把整趟搅断");
        assertEquals(0, service.selectTHmfAcctCardById(c.getId()).getGradeLevel().intValue());
    }

    // ---- 列表与详情：同一把尺 ----

    @Test
    void listAndDetail_sameRuler_respectsCondition_showsAllStatuses() {
        THmfAcctCard pending = card("FH-1", "JZ-A", "50");
        service.insertTHmfAcctCard(pending);
        THmfAcctCard checked = card("FH-2", "JZ-A", "50");
        checked.setStatus(1);
        service.insertTHmfAcctCard(checked);

        List<THmfAcctCard> all = service.selectTHmfAcctCardList(new QueryWrapper<THmfAcctCard>());
        assertEquals(2, all.size(), "未删的各状态都在册面上，列表不另塞 status=0");

        List<THmfAcctCard> onlyChecked = service.selectTHmfAcctCardList(
                new QueryWrapper<THmfAcctCard>().eq("status", 1));
        assertEquals(1, onlyChecked.size());
        assertEquals("FH-2", onlyChecked.get(0).getBillNo(), "递来的条件照收，不被自己另算的盖掉");

        assertNotNull(service.selectTHmfAcctCardById(pending.getId()), "详情与列表同尺，在册的翻得到");
    }

    // ---- 底册一改，挂着的单怎么跟着走 ----

    @Test
    void update_qtyChanged_regrades_siteNoSynced_billNoFrozen() {
        THmfAcctCard c = card("FH-1", "JZ-A", "50");
        service.insertTHmfAcctCard(c);

        THmfAcctCard grow = new THmfAcctCard();
        grow.setId(c.getId());
        grow.setQty(new BigDecimal("120"));
        assertEquals(1, service.updateTHmfAcctCard(grow));
        THmfAcctCard saved = service.selectTHmfAcctCardById(c.getId());
        assertEquals(2, saved.getGradeLevel().intValue(), "面积动了，档次照原底册三线重折");
        assertEquals("JZ-A", saved.getSiteNo());

        // 改挂别的在册底册：底册代号跟着同步
        THmfAcctCard reattach = new THmfAcctCard();
        reattach.setId(c.getId());
        THmfArch jzE = arch("JZ-E", 0, 0, "90", "144", "300");
        reattach.setSiteId(jzE.getId().intValue());
        assertEquals(1, service.updateTHmfAcctCard(reattach));
        assertEquals("JZ-E", service.selectTHmfAcctCardById(c.getId()).getSiteNo());

        // 户号是下游挂接的根：改单时夹带别的号，冻住不认
        THmfAcctCard hijack = new THmfAcctCard();
        hijack.setId(c.getId());
        hijack.setBillNo("FH-HIJACK");
        assertEquals(1, service.updateTHmfAcctCard(hijack));
        assertEquals("FH-1", service.selectTHmfAcctCardById(c.getId()).getBillNo(),
                "立户单号冻结，不让底下挂着的销账行抖成孤儿");

        // 改挂到已迁出/删过的底册：整单不动
        THmfAcctCard toGone = new THmfAcctCard();
        toGone.setId(c.getId());
        toGone.setSiteId(archIdOf("JZ-B"));
        assertEquals(0, service.updateTHmfAcctCard(toGone));
        assertEquals("JZ-E", service.selectTHmfAcctCardById(c.getId()).getSiteNo(), "认不上的底册不改挂");
    }

    @Test
    void update_missingCard_returnsZero() {
        THmfAcctCard ghost = new THmfAcctCard();
        ghost.setId(404L);
        assertEquals(0, service.updateTHmfAcctCard(ghost));
        assertNull(service.selectTHmfAcctCardById(404L));
    }

    // ---- 删除：只翻标记，张不抹 ----

    @Test
    void delete_isLogical_sheetRemains_detailAndListHide() {
        THmfAcctCard c = card("FH-1", "JZ-A", "50");
        service.insertTHmfAcctCard(c);
        int before = cards.all().size();

        assertEquals(1, service.deleteTHmfAcctCardById(c.getId()));
        assertEquals(before, cards.all().size(), "物理行还在，不是一把抹掉");
        assertEquals(1, cards.all().get(0).getDelFlag().intValue());
        assertNull(service.selectTHmfAcctCardById(c.getId()), "删过的详情不当在册回");
        assertEquals(0, service.selectTHmfAcctCardList(new QueryWrapper<THmfAcctCard>()).size());
        // 再删一遍不多记数
        assertEquals(0, service.deleteTHmfAcctCardById(c.getId()));

        // 批量删走同一道逻辑删
        THmfAcctCard c2 = card("FH-2", "JZ-A", "50");
        service.insertTHmfAcctCard(c2);
        assertEquals(1, service.deleteTHmfAcctCardByIds(String.valueOf(c2.getId())));
        assertEquals(2, cards.all().stream().filter(x -> x.getDelFlag() == 1).count());
    }

    // ---- 录入、退出、重新进入：同号同底子，结果可重现 ----

    @Test
    void reenterAfterExit_sameBillNo_reproducesSameShape() {
        THmfAcctCard first = card("FH-1", "JZ-A", "144");
        service.insertTHmfAcctCard(first);
        String siteNo = first.getSiteNo();
        int grade = first.getGradeLevel();
        service.deleteTHmfAcctCardById(first.getId());

        THmfAcctCard second = card("FH-1", "JZ-A", "144");
        assertEquals(1, service.insertTHmfAcctCard(second), "退出后同号可重新立户");
        THmfAcctCard saved = service.selectTHmfAcctCardById(second.getId());
        assertEquals("FH-1", saved.getBillNo());
        assertEquals(siteNo, saved.getSiteNo());
        assertEquals(grade, saved.getGradeLevel(), "同一底子再走一遍，档次与头一回一致");
        assertEquals(0, saved.getStatus().intValue());
        assertTrue(!first.getId().equals(saved.getId()), "是新立的一张，不是把旧张翻出来");
    }
}
