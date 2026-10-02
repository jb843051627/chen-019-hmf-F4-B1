package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfPayRow;
import com.fc.v2.service.impl.THmfPayRowServiceImpl;

/**
 * 旧批法（submitBatch）进门口径与册子核销对齐逐条核对：
 * 空批判空不越界、超上限 -1 整批不入库、同批重发不翻倍、同批同明细码去重、
 * 一行非法只退它自己合规行照收、失败明细保留原行号（不是坏行序号）、
 * 册次号只认入参、失败明细按行号升序、到账金额归一到库口精度。
 */
class SubmitBatchTest {

    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    private THmfPayRowServiceImpl service;
    private FakePayRowMapper rows;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfPayRowServiceImpl();
        this.rows = new FakePayRowMapper();
        inject("hmfPayRowMapper", rows);
        inject("hmfPayBookMapper", new FakePayBookMapper());
        inject("hmfAcctCardMapper", new FakeAcctCardMapper());
    }

    private void inject(String name, Object value) throws Exception {
        Field f = THmfPayRowServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private THmfPayRow row(String itemCode, String qty) {
        THmfPayRow r = new THmfPayRow();
        r.setItemCode(itemCode);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    private List<THmfPayRow> ofBatch(String batchNo, List<THmfPayRow> rows) {
        for (THmfPayRow r : rows) {
            r.setBatchNo(batchNo);
        }
        return rows;
    }

    private List<THmfPayRow> stored(String batchNo, Integer status) {
        List<THmfPayRow> out = new ArrayList<>();
        for (THmfPayRow r : rows.all()) {
            if (batchNo.equals(r.getBatchNo()) && (status == null || status.equals(r.getStatus()))) {
                out.add(r);
            }
        }
        return out;
    }

    // ---- 空批判空：不越界 ----

    @Test
    void emptyBatch_returnsZero_noThrow() {
        assertEquals(0, service.submitBatch("B-0", new ArrayList<>()));
        assertEquals(0, service.submitBatch("B-0", null));
        assertEquals(0, service.submitBatch("  ", new ArrayList<>(java.util.Arrays.asList(row("A", "1")))));
        assertEquals(0, rows.all().size());
    }

    // ---- 超上限：整批不入库 ----

    @Test
    void overLimit_returnsMinus1_nothingStored() {
        List<THmfPayRow> big = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            big.add(row("A" + i, "1.00"));
        }
        assertEquals(-1, service.submitBatch("B-BIG", big));
        assertEquals(0, rows.all().size(), "原批退回，一行不留");
    }

    // ---- 同批重发：不翻倍 ----

    @Test
    void sameBatchResent_notDoubled_returnsFirstCount() {
        List<THmfPayRow> first = new ArrayList<>();
        first.add(row("A", "100.00"));
        first.add(row("B", "200.00"));
        assertEquals(2, service.submitBatch("B-DUP", ofBatch("夹带的别号", first)));
        int afterFirst = rows.all().size();

        // 同一批重发一遍：数据不得翻倍，回话仍是头一遍成功数
        List<THmfPayRow> again = new ArrayList<>();
        again.add(row("A", "100.00"));
        again.add(row("B", "200.00"));
        assertEquals(2, service.submitBatch("B-DUP", again));
        assertEquals(afterFirst, rows.all().size(), "重发不重插");
        assertEquals(2, stored("B-DUP", STATUS_OK).size());
    }

    // ---- 同批同明细码两条：第二条记失败，不连累别行 ----

    @Test
    void duplicateItemCodeInSameBatch_secondRowFails_only() {
        List<THmfPayRow> in = new ArrayList<>();
        in.add(row("A", "100.00"));
        in.add(row("A", "200.00")); // 同批里同一个明细码又进一条
        in.add(row("B", "300.00"));
        assertEquals(2, service.submitBatch("B-DUPITEM", in), "头一条与别户照收，重复条退回");

        List<THmfPayRow> ok = stored("B-DUPITEM", STATUS_OK);
        assertEquals(2, ok.size());
        assertEquals("A", ok.get(0).getItemCode());
        assertEquals("B", ok.get(1).getItemCode());
        List<THmfPayRow> bad = service.listErrors("B-DUPITEM");
        assertEquals(1, bad.size());
        assertEquals(2, bad.get(0).getRowNo().intValue(), "重复条保留它在原册的行号");
        assertTrue(bad.get(0).getHoldReason().contains("重复"));
    }

    // ---- 一行非法只退自己：合规行照单全收 ----

    @Test
    void oneIllegalRow_onlyThatRowRejected_validRowsStillStored() {
        List<THmfPayRow> in = new ArrayList<>();
        in.add(row("A", "100.00"));
        in.add(row(" ", "50.00"));       // 第2行：户号空
        in.add(row("C", "0"));           // 第3行：金额不正
        in.add(row("D", null));          // 第4行：金额空
        in.add(row("E", "70.00"));       // 第5行：合规
        int ok = service.submitBatch("B-MIX", in);
        assertEquals(2, ok, "合规的两行照收，不再整批退回");

        List<THmfPayRow> stored = stored("B-MIX", STATUS_OK);
        assertEquals(2, stored.size());
        assertEquals("A", stored.get(0).getItemCode());
        assertEquals("E", stored.get(1).getItemCode());

        List<THmfPayRow> errors = service.listErrors("B-MIX");
        assertEquals(3, errors.size());
        // 行号是原行号，不是坏行里重新数的序号
        assertEquals(2, errors.get(0).getRowNo().intValue());
        assertEquals(3, errors.get(1).getRowNo().intValue());
        assertEquals(4, errors.get(2).getRowNo().intValue(), "失败明细按行号升序");
    }

    // ---- 册次号只认入参：行里夹带的号不看 ----

    @Test
    void batchNoTakenFromArgument_notFromRows() {
        List<THmfPayRow> in = new ArrayList<>();
        THmfPayRow r = row("A", "100.00");
        r.setBatchNo("夹带的别号");
        in.add(r);
        service.submitBatch("B-REAL", in);
        assertEquals(1, stored("B-REAL", STATUS_OK).size());
        assertEquals(0, stored("夹带的别号", null).size(), "行里夹带的册次号一概不看");
    }

    // ---- 到账金额：归一到库口 decimal(12,2) ----

    @Test
    void qtyNormalizedToDbScale() {
        List<THmfPayRow> in = new ArrayList<>();
        in.add(row("A", "100.005"));  // 四舍五入到分
        service.submitBatch("B-QTY", in);
        THmfPayRow stored = stored("B-QTY", STATUS_OK).get(0);
        assertEquals(0, new BigDecimal("100.01").compareTo(stored.getQty()));
        assertEquals(2, stored.getQty().scale());
    }

    // ---- 落库行带现行层与删除标记，供下游同一处算法认得 ----

    @Test
    void acceptedRows_markedCurrentLayer() {
        service.submitBatch("B-LAYER", new ArrayList<>(java.util.Arrays.asList(
                row("A", "100.00"), row("B", "200.00"))));
        for (THmfPayRow r : stored("B-LAYER", STATUS_OK)) {
            assertNotNull(r.getLayer());
            assertEquals(0, r.getLayer().intValue(), "旧批法行在现行册面层，下游户面才认");
            assertEquals(0, r.getDelFlag().intValue());
        }
    }
}
