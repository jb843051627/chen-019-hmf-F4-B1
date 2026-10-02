package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfAcctCardMapper;
import com.fc.v2.mapper.auto.THmfPayBookMapper;
import com.fc.v2.mapper.auto.THmfPayRowMapper;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfPayBook;
import com.fc.v2.model.auto.THmfPayRow;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.model.custom.PayRowDiff;
import com.fc.v2.service.ITHmfPayRowService;

/**
 * 银行交存汇总册核销行 Service业务层处理（batch-process 形状：整批提交）
 *
 * 今年钉死的册子核销口径集中在 {@link #reconcilePayBook} 这一趟：
 * 一份册一个册次号，进门就定死，这一册的行不混进那一册；每行按先后自占册内行次，
 * 挂起行翻出来仍带它原来排第几。每行只有一个去向：已销(1)、挂起(2)（未销0只是判定前的暂态）——
 * 销账只认户号（名字像不算对上），同一户号几笔在户面上累加不覆盖；
 * 累到超过本户应缴数的那一行就地挂起、写明缘由，同册其余各行照走不误，不整册退回重来。
 * 册号第二遍进来只拿行比库里已有的行，比完就收，去向不改、行不加不改；要改先走撤册手续。
 * 行数超中心定的上限，原册退回、一行不留；全册没有一行能核，回执只许写“空册”。
 * 挂起连跨两个对账季无去向，挪往期层只供翻，现行册面不再露；交存日落在哪季算哪季（季末含当日）。
 * 册面总行数、这趟已销行数、挂起行数出自同一回统计，册首与各行一次落库；接不平当场列差行。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class THmfPayRowServiceImpl implements ITHmfPayRowService {

    // ---------------------------------------------------------------- 旧批法（整批提交，进门口径与册子核销对齐）

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    // ---------------------------------------------------------------- 册子核销口径常量

    /** 中心定下的一册行数上限（进册先按整册看，超了原册退回、一行不留） */
    private static final int MAX_ROWS_PER_BOOK = 500;

    /** 行去向：未销（判定前暂态，落库的行不可能停在这） */
    private static final int ROW_PENDING = 0;
    /** 行去向：已销账 */
    private static final int ROW_CLEARED = 1;
    /** 行去向：挂起 */
    private static final int ROW_HELD = 2;

    /** 所在层：现行册面 */
    private static final int LAYER_CURRENT = 0;
    /** 所在层：往期（只供翻，不牵住新册） */
    private static final int LAYER_ARCHIVED = 1;

    /** 回执结果：核讫（报成绩的词，至少有一行核掉才配） */
    private static final int RESULT_CHECKED = 1;
    /** 回执结果：空册（全册没有一行能核） */
    private static final int RESULT_EMPTY = 2;

    /** 册情形：在册 */
    private static final int BOOK_ACTIVE = 0;
    /** 册情形：已撤（要改先走这道手续） */
    private static final int BOOK_WITHDRAWN = 1;

    private static final String TEXT_CHECKED = "核讫";
    private static final String TEXT_EMPTY = "空册";

    @javax.annotation.Resource
    private THmfPayRowMapper hmfPayRowMapper;

    @javax.annotation.Resource
    private THmfPayBookMapper hmfPayBookMapper;

    @javax.annotation.Resource
    private THmfAcctCardMapper hmfAcctCardMapper;

    // ---------------------------------------------------------------- 旧批法（原样保留）

    @Override
    public THmfPayRow selectTHmfPayRowById(Long id) {
        return this.hmfPayRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<THmfPayRow> rows) {
        // 空批先判空：不许伸手摸 rows.get(0)，摸着就是越界。
        if (rows == null || rows.isEmpty() || isBlank(batchNo)) {
            return 0;
        }
        // 两处规矩同源：册次号只认进门入参，行里夹带的号一概不看。
        String no = batchNo.trim();

        // 同一批重发只认头一遍：该批已有落库的行（好行或失败明细），照头一遍的成功数回话，
        // 不重插、不另起一拨。并发两趟叠着进门，落后那趟在这道门口原样退出，数据不翻倍。
        if (this.hmfPayRowMapper.selectCount(new QueryWrapper<THmfPayRow>()
                .eq("batch_no", no).eq("del_flag", 0)) > 0) {
            return this.hmfPayRowMapper.selectCount(new QueryWrapper<THmfPayRow>()
                    .eq("batch_no", no).eq("status", STATUS_OK).eq("del_flag", 0));
        }

        // 行数超中心定的上限：整批不入库，-1 回话（与册法“原册退回、一行不留”同一处裁）。
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        // 同一趟判完才落库：逐行校验、同批去重在这一趟里一起点清，不边判边插——
        // 回滚时库里不留半截，并发叠上来时，两趟数出的结果对得拢。
        List<THmfPayRow> valid = new ArrayList<>();
        List<THmfPayRow> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int idx = 0;
        for (THmfPayRow in : rows) {
            idx++; // 原册行号：在原册排第几就是几，坏行也好行也好，都照这个号说话
            BigDecimal qty = PayRowAmounts.normalize(in == null ? null : in.getQty());
            if (in == null || isBlank(in.getItemCode()) || qty == null
                    || qty.compareTo(BigDecimal.ZERO) <= 0) {
                THmfPayRow bad = copyForStore(in, no);
                bad.setRowNo(idx); // 失败明细保留原行号，不许只数坏行重新编号
                bad.setStatus(STATUS_FAIL);
                errors.add(bad);
                continue;
            }
            String acct = in.getItemCode().trim();
            // 同批里同一个明细码进了两条：第二条与头一条对不拢，就地记入失败，不连累别行。
            if (!seen.add(acct)) {
                THmfPayRow dup = copyForStore(in, no);
                dup.setRowNo(idx);
                dup.setStatus(STATUS_FAIL);
                dup.setHoldReason("同批明细码" + acct + "重复进门，本行照非法行记回");
                errors.add(dup);
                continue;
            }
            valid.add(copyForStore(in, no));
        }

        // 一行非法只退它自己一行：合规行照单落库，不再整批退回、连累合规行。
        for (THmfPayRow bad : errors) {
            bad.setCreateTime(new Date());
            this.hmfPayRowMapper.insert(bad);
        }
        for (THmfPayRow r : valid) {
            r.setStatus(STATUS_OK);
            r.setLayer(LAYER_CURRENT);
            r.setDelFlag(0);
            r.setCreateTime(new Date());
            this.hmfPayRowMapper.insert(r);
        }
        return valid.size();
    }

    /** 旧批法：照入参誊一行落库用的行；册次号只认进门的号，到账金额先归一到库口精度。 */
    private THmfPayRow copyForStore(THmfPayRow in, String batchNo) {
        THmfPayRow r = new THmfPayRow();
        r.setBatchNo(batchNo);
        if (in != null) {
            r.setItemCode(in.getItemCode() == null ? null : in.getItemCode().trim());
            r.setQty(PayRowAmounts.normalize(in.getQty()));
            r.setPayDate(in.getPayDate());
            r.setRemark(in.getRemark());
        }
        r.setDelFlag(0);
        return r;
    }

    @Override
    public List<THmfPayRow> listErrors(String batchNo) {
        return this.hmfPayRowMapper.selectList(new QueryWrapper<THmfPayRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL)
                .orderByAsc("row_no"));
    }

    // ---------------------------------------------------------------- 册子核销一趟

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayBookReceipt reconcilePayBook(String batchNo, List<THmfPayRow> rows) {
        if (isBlank(batchNo) || rows == null) {
            return null;
        }
        String no = batchNo.trim();

        // 册次号进门定死：在册的同号册只做比对，去向不改、行不加不改。
        THmfPayBook existing = activeBook(no);
        if (existing != null) {
            return compareOnly(existing, rows);
        }

        // 进册先按整册看：超了中心定的上限，原册退回、一行不留（册头也不留）。
        if (rows.size() > MAX_ROWS_PER_BOOK) {
            PayBookReceipt reject = new PayBookReceipt();
            reject.setCode(PayBookReceipt.CODE_REJECTED);
            reject.setBatchNo(no);
            reject.setMessage("本册" + rows.size() + "行，超过一册" + MAX_ROWS_PER_BOOK
                    + "行的上限，原册退回，一行不留");
            return reject;
        }

        // 每一行的去向在服务层这一趟里全部判定完，才入库；页面塞不进多余一行也抽不走一行。
        List<THmfPayRow> judged = new ArrayList<>();
        int seq = 0;
        for (THmfPayRow in : rows) {
            // 户号空、金额不正、交存日期没到的行连判定都无从判起，整册不收（并非超限，不写一行）。
            // 到账金额是算钱的底子，进门先在同一处归一到库口精度，再判再记，与旧批法/下游同源。
            BigDecimal qty = PayRowAmounts.normalize(in == null ? null : in.getQty());
            if (in == null || isBlank(in.getItemCode()) || qty == null
                    || qty.compareTo(BigDecimal.ZERO) <= 0 || in.getPayDate() == null) {
                return null;
            }
            THmfPayRow r = new THmfPayRow();
            r.setBatchNo(no);
            r.setItemCode(in.getItemCode().trim());
            r.setQty(qty);
            r.setPayDate(in.getPayDate());
            r.setRemark(in.getRemark());
            judged.add(r);
        }

        // 本户应缴数照分户底册认：销账认户号，名字像不算对上。
        // 同一立户单有多版时，认版次序最新那版的应缴额。
        Map<String, THmfAcctCard> cardByAcct = new HashMap<>();
        for (THmfAcctCard c : this.hmfAcctCardMapper.selectList(new QueryWrapper<THmfAcctCard>()
                .eq("del_flag", 0))) {
            if (isBlank(c.getBillNo())) {
                continue;
            }
            String acct = c.getBillNo().trim();
            THmfAcctCard old = cardByAcct.get(acct);
            if (old == null || versionOf(c) > versionOf(old)) {
                cardByAcct.put(acct, c);
            }
        }

        // 户面已收：在册各册已销账的同户号行在此前各册的合计（已撤册的行不算、挂起行不算）。
        Map<String, BigDecimal> paidSoFar = householdPaidTotals();

        // 同一回统计：本册总数与逐行销/挂在这一趟里一起点出，不分两回读。
        int totalRows = 0;
        int clearedRows = 0;
        int heldRows = 0;
        for (THmfPayRow r : judged) {
            seq++;
            totalRows++;
            r.setRowNo(seq);                       // 进册自动占行次，按先后排定，往后不改
            r.setFromSeason(seasonOf(r.getPayDate())); // 交存日落在哪季就算哪季，进门钉死
            r.setLayer(LAYER_CURRENT);
            r.setDelFlag(0);
            r.setStatus(ROW_PENDING);

            String acct = r.getItemCode();
            THmfAcctCard card = cardByAcct.get(acct);
            if (card == null || card.getFineAmt() == null) {
                // 户号对不上分户底册：不能销，又不能退回整册连累别行——就地挂起写明。
                r.setStatus(ROW_HELD);
                r.setHoldReason("户号" + acct + "对不上分户底册，销账只认户号，本行挂起候核");
                heldRows++;
                continue;
            }
            BigDecimal due = card.getFineAmt();
            BigDecimal before = paidSoFar.getOrDefault(acct, BigDecimal.ZERO);
            BigDecimal after = before.add(r.getQty());
            if (after.compareTo(due) > 0) {
                // 累到超过这户应缴数：超的就是这一行，就地挂起，户面不把这笔加上。
                r.setStatus(ROW_HELD);
                r.setHoldReason("户号" + acct + "户面已收" + before.toPlainString()
                        + "，本笔" + r.getQty().toPlainString() + "，累计" + after.toPlainString()
                        + "超过应缴" + due.toPlainString() + "，本行就地挂起");
                heldRows++;
            } else {
                // 户面上累加，不覆盖：头一笔八千、第二笔六千，户面记一万四。
                r.setStatus(ROW_CLEARED);
                clearedRows++;
                paidSoFar.put(acct, after);
            }
        }

        // 全册没有一行能核：回执那一栏写“空册”，不许写核讫。
        int result = clearedRows == 0 ? RESULT_EMPTY : RESULT_CHECKED;

        // 判定办完才入库：先落册头（三数照这一趟的统计写），再落各行，一次落定。
        THmfPayBook book = new THmfPayBook();
        book.setBatchNo(no);
        book.setTotalRows(totalRows);
        book.setClearedRows(clearedRows);
        book.setHeldRows(heldRows);
        book.setResult(result);
        book.setStatus(BOOK_ACTIVE);
        book.setDelFlag(0);
        book.setCreateTime(new Date());
        this.hmfPayBookMapper.insert(book);

        for (THmfPayRow r : judged) {
            r.setBookId(book.getId());
            r.setCreateTime(new Date());
            this.hmfPayRowMapper.insert(r);
        }

        PayBookReceipt receipt = new PayBookReceipt();
        receipt.setCode(PayBookReceipt.CODE_ACCEPTED);
        receipt.setBatchNo(no);
        receipt.setBook(book);
        receipt.setRows(judged);
        fillCounts(receipt, book);
        receipt.setMessage(clearedRows == 0
                ? "全册没有一行能核，回执记空册（不记核讫）"
                : "本册已核销，销掉" + clearedRows + "行、挂起" + heldRows + "行，回执记核讫");
        return receipt;
    }

    /**
     * 同号第二遍：拿行去比库里已有的行，比完就收。去向不改、行不加不改，
     * 头一遍的结果原样回话；确实要改，先走撤册手续。
     */
    private PayBookReceipt compareOnly(THmfPayBook book, List<THmfPayRow> incoming) {
        List<THmfPayRow> stored = rowsOfBook(book.getId(), null);
        Map<Integer, THmfPayRow> storeByRowNo = new LinkedHashMap<>();
        for (THmfPayRow s : stored) {
            storeByRowNo.put(s.getRowNo(), s);
        }
        // 第二遍的行也按先后占位次，位次即册内行次，拿位次对库里的行。
        Map<Integer, THmfPayRow> inByRowNo = new LinkedHashMap<>();
        int pos = 0;
        for (THmfPayRow in : incoming) {
            pos++;
            inByRowNo.put(pos, in);
        }

        List<PayRowDiff> diffs = new ArrayList<>();
        for (Map.Entry<Integer, THmfPayRow> e : inByRowNo.entrySet()) {
            int rowNo = e.getKey();
            THmfPayRow in = e.getValue();
            THmfPayRow store = storeByRowNo.get(rowNo);
            if (store == null) {
                diffs.add(diff(rowNo, PayRowDiff.KIND_ADDED, in, null,
                        "第" + rowNo + "行第二遍多出来，头一遍库里没有这一行"));
                continue;
            }
            String inAcct = in.getItemCode() == null ? null : in.getItemCode().trim();
            boolean acctChanged = !eqString(inAcct, store.getItemCode());
            boolean qtyChanged = in.getQty() == null || store.getQty() == null
                    || in.getQty().compareTo(store.getQty()) != 0;
            boolean dateChanged = !sameDay(in.getPayDate(), store.getPayDate());
            if (acctChanged || qtyChanged || dateChanged) {
                StringBuilder why = new StringBuilder("第" + rowNo + "行与头一遍对不上：");
                if (acctChanged) {
                    why.append("户号").append(inAcct).append("/").append(store.getItemCode()).append(" ");
                }
                if (qtyChanged) {
                    why.append("金额").append(in.getQty()).append("/").append(store.getQty()).append(" ");
                }
                if (dateChanged) {
                    why.append("交存日期").append(in.getPayDate()).append("/").append(store.getPayDate());
                }
                diffs.add(diff(rowNo, PayRowDiff.KIND_CHANGED, in, store, why.toString().trim()));
            }
        }
        for (Integer rowNo : storeByRowNo.keySet()) {
            if (!inByRowNo.containsKey(rowNo)) {
                diffs.add(diff(rowNo, PayRowDiff.KIND_MISSING, null, storeByRowNo.get(rowNo),
                        "第" + rowNo + "行头一遍库里有，第二遍缺了这一行"));
            }
        }

        PayBookReceipt receipt = new PayBookReceipt();
        receipt.setCode(PayBookReceipt.CODE_COMPARED);
        receipt.setBatchNo(book.getBatchNo());
        receipt.setBook(book);
        receipt.setRows(stored);
        receipt.setDiffs(diffs);
        fillCounts(receipt, book);
        receipt.setMessage(diffs.isEmpty()
                ? "同号第二遍，逐行比对一致：去向不改、行不加不改，头一遍结果原样留着；要改先走撤册手续"
                : "同号第二遍比对出" + diffs.size() + "处差异，已逐行列明；账上仍照头一遍，不改不加");
        return receipt;
    }

    // ---------------------------------------------------------------- 末了一笔对账

    @Override
    public PayBookAudit auditPayBook(String batchNo) {
        PayBookAudit audit = new PayBookAudit();
        if (isBlank(batchNo)) {
            audit.setPass(false);
            audit.setReason("册次号为空，无从对账");
            return audit;
        }
        THmfPayBook book = activeBook(batchNo.trim());
        audit.setBook(book);
        if (book == null) {
            // 回执写着核讫、系统里却查不到那一册：当场不认。
            audit.setPass(false);
            audit.setReason("系统里查不到在册的" + batchNo.trim() + "这一册，回执不能算数");
            return audit;
        }

        // 册首数与逐行销出来的数在这同一回计算里点：不分开读第二回。
        List<THmfPayRow> rows = rowsOfBook(book.getId(), null);
        int total = rows.size();
        int cleared = 0;
        int held = 0;
        List<THmfPayRow> illegal = new ArrayList<>();
        for (THmfPayRow r : rows) {
            if (r.getStatus() != null && r.getStatus() == ROW_CLEARED) {
                cleared++;
            } else if (r.getStatus() != null && r.getStatus() == ROW_HELD) {
                held++;
            } else {
                illegal.add(r);
            }
        }
        audit.setTotalRows(total);
        audit.setClearedRows(cleared);
        audit.setHeldRows(held);

        boolean identity = cleared + held == total && illegal.isEmpty();
        boolean headMatch = eqInt(book.getTotalRows(), total)
                && eqInt(book.getClearedRows(), cleared)
                && eqInt(book.getHeldRows(), held);
        if (identity && headMatch) {
            audit.setPass(true);
            audit.setReason("册首" + total + "行与逐行销" + cleared + "挂" + held
                    + "同一回点齐，销+挂=本册行数，对得上");
            return audit;
        }

        // 接不平：差在哪几行当场列出来，不许拿一个成数搪塞。
        // 停在未销等非法去向的行就是差行（销/挂计数的漏子也出在它身上），不顺带冤枉好行；
        // 没有非法行而册首某数仍对不上（行数被拨、计数被改），再点一行作差。
        List<THmfPayRow> bad = new ArrayList<>(illegal);
        if (!eqInt(book.getTotalRows(), total)) {
            for (THmfPayRow r : rows) {
                if (!bad.contains(r)) {
                    bad.add(r);
                }
            }
        }
        if (illegal.isEmpty()) {
            if (!eqInt(book.getClearedRows(), cleared)) {
                for (THmfPayRow r : rows) {
                    if (r.getStatus() != null && r.getStatus() == ROW_CLEARED && !bad.contains(r)) {
                        bad.add(r);
                        break;
                    }
                }
            }
            if (!eqInt(book.getHeldRows(), held)) {
                for (THmfPayRow r : rows) {
                    if (r.getStatus() != null && r.getStatus() == ROW_HELD && !bad.contains(r)) {
                        bad.add(r);
                        break;
                    }
                }
            }
        }
        audit.setBadRows(bad);
        audit.setPass(false);
        audit.setReason("册首数（" + book.getTotalRows() + "/" + book.getClearedRows() + "/"
                + book.getHeldRows() + "）与逐行点出来的（" + total + "/" + cleared + "/" + held
                + "）接不平，差在列出的这几行（册内行次："
                + rowNosOf(bad) + "）");
        return audit;
    }

    // ---------------------------------------------------------------- 挂起跨两季挪往期

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sweepHeldToArchived(Date now) {
        Date at = now == null ? new Date() : now;
        int nowIdx = seasonIndex(seasonOf(at));
        Set<Long> activeBookIds = activeBookIds();
        int moved = 0;
        // 现行册面上的挂起行逐行看它进门钉死的那一季（已撤册的行不扫）。
        for (THmfPayRow r : this.hmfPayRowMapper.selectList(new QueryWrapper<THmfPayRow>()
                .eq("status", ROW_HELD).eq("layer", LAYER_CURRENT).eq("del_flag", 0))) {
            if (r.getBookId() == null || !activeBookIds.contains(r.getBookId())) {
                continue;
            }
            String season = !isBlank(r.getFromSeason()) ? r.getFromSeason()
                    : (r.getPayDate() == null ? null : seasonOf(r.getPayDate()));
            if (season == null) {
                continue;
            }
            // 连着跨过两个对账季还没有去向：挪往期。去向仍是挂起，册首三数不动。
            if (nowIdx - seasonIndex(season) >= 2) {
                r.setLayer(LAYER_ARCHIVED);
                r.setUpdateTime(new Date());
                this.hmfPayRowMapper.updateById(r);
                moved++;
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------- 撤册

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfPayBook withdrawPayBook(String batchNo, String operator, String reason) {
        if (isBlank(batchNo)) {
            return null;
        }
        THmfPayBook book = activeBook(batchNo.trim());
        if (book == null) {
            return null;
        }
        // 撤册是另开的一道手续：册撤下、行留存，撤后同号方可重新进门核销。
        book.setStatus(BOOK_WITHDRAWN);
        book.setUpdateBy(isBlank(operator) ? null : operator.trim());
        book.setUpdateTime(new Date());
        String line = "撤册（经办：" + (isBlank(operator) ? "未署名" : operator.trim())
                + "）：" + (isBlank(reason) ? "改册重报" : reason.trim());
        book.setRemark(isBlank(book.getRemark()) ? line : book.getRemark() + "\n" + line);
        this.hmfPayBookMapper.updateById(book);
        return book;
    }

    // ---------------------------------------------------------------- 翻册面

    @Override
    public THmfPayBook selectPayBook(String batchNo) {
        return isBlank(batchNo) ? null : activeBook(batchNo.trim());
    }

    @Override
    public List<THmfPayRow> listCurrentRows(String batchNo) {
        THmfPayBook book = isBlank(batchNo) ? null : activeBook(batchNo.trim());
        // 现行册面不再露已挪往期的挂起行。
        return book == null ? new ArrayList<>() : rowsOfBook(book.getId(), LAYER_CURRENT);
    }

    @Override
    public List<THmfPayRow> listArchivedRows(String batchNo) {
        THmfPayBook book = isBlank(batchNo) ? null : activeBook(batchNo.trim());
        // 往期只放着给人翻；已撤的册不在翻查之列。
        return book == null ? new ArrayList<>() : rowsOfBook(book.getId(), LAYER_ARCHIVED);
    }

    // ---------------------------------------------------------------- 私有帮手

    private THmfPayBook activeBook(String batchNo) {
        List<THmfPayBook> books = this.hmfPayBookMapper.selectList(new QueryWrapper<THmfPayBook>()
                .eq("batch_no", batchNo).eq("status", BOOK_ACTIVE).eq("del_flag", 0));
        return books.isEmpty() ? null : books.get(0);
    }

    private Set<Long> activeBookIds() {
        return HouseholdPaid.activeBookIds(this.hmfPayBookMapper);
    }

    /** 某在册册的各行（layer 给 null 则两层都取），按册内行次排，挂起行带着原来排第几。 */
    private List<THmfPayRow> rowsOfBook(Long bookId, Integer layer) {
        QueryWrapper<THmfPayRow> w = new QueryWrapper<THmfPayRow>()
                .eq("book_id", bookId).eq("del_flag", 0);
        if (layer != null) {
            w.eq("layer", layer.intValue());
        }
        return this.hmfPayRowMapper.selectList(w.orderByAsc("row_no"));
    }

    /**
     * 点各在册册里已销账行按户号的合计：户面累加的底数。规矩合在 {@link HouseholdPaid} 一处，
     * 与下游使用申请点户面余额同源——已撤册、挂起行、往期层的挂起行都不进户面；
     * 旧批法无册头的行照旧计。
     */
    private Map<String, BigDecimal> householdPaidTotals() {
        return HouseholdPaid.totalsByAccount(this.hmfPayRowMapper, this.hmfPayBookMapper);
    }

    private void fillCounts(PayBookReceipt receipt, THmfPayBook book) {
        // 回执三数与册首同出一处：直接照这一趟落册头的数回话。
        receipt.setTotalRows(book.getTotalRows() == null ? 0 : book.getTotalRows().intValue());
        receipt.setClearedRows(book.getClearedRows() == null ? 0 : book.getClearedRows().intValue());
        receipt.setHeldRows(book.getHeldRows() == null ? 0 : book.getHeldRows().intValue());
        receipt.setResultText(book.getResult() != null && book.getResult() == RESULT_EMPTY
                ? TEXT_EMPTY : TEXT_CHECKED);
    }

    private PayRowDiff diff(int rowNo, int kind, THmfPayRow in, THmfPayRow store, String detail) {
        PayRowDiff d = new PayRowDiff();
        d.setRowNo(rowNo);
        d.setKind(kind);
        d.setDetail(detail);
        if (in != null) {
            d.setInItemCode(in.getItemCode());
            d.setInQty(in.getQty());
            d.setInPayDate(in.getPayDate());
        }
        if (store != null) {
            d.setStoreItemCode(store.getItemCode());
            d.setStoreQty(store.getQty());
            d.setStorePayDate(store.getPayDate());
        }
        return d;
    }

    private String rowNosOf(List<THmfPayRow> rows) {
        StringBuilder sb = new StringBuilder();
        for (THmfPayRow r : rows) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(r.getRowNo());
        }
        return sb.toString();
    }

    private static int versionOf(THmfAcctCard c) {
        return c.getNodeNo() == null ? 0 : c.getNodeNo().intValue();
    }

    private static boolean eqInt(Integer a, int b) {
        return a != null && a.intValue() == b;
    }

    private static boolean eqString(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean sameDay(Date a, Date b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        Calendar ca = Calendar.getInstance();
        ca.setTime(a);
        Calendar cb = Calendar.getInstance();
        cb.setTime(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH)
                && ca.get(Calendar.DAY_OF_MONTH) == cb.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 交存日落在哪一对账季就算哪一季：自然季度（1-3/4-6/7-9/10-12）。
     * 季末（3/31、6/30、9/30、12/31）当天含在本季，不漏算到下一季。
     */
    static String seasonOf(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        int year = c.get(Calendar.YEAR);
        int quarter = c.get(Calendar.MONTH) / 3 + 1;
        return year + "Q" + quarter;
    }

    /** 对账季序：连跨两个对账季照序差认（2026Q1→2026Q3 序差为 2）。 */
    static int seasonIndex(String season) {
        int q = season.charAt(season.length() - 1) - '0';
        int year = Integer.parseInt(season.substring(0, season.length() - 2));
        return year * 4 + (q - 1);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
