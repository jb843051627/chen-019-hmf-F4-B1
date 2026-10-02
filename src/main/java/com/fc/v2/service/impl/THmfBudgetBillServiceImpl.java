package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfBudgetBillMapper;
import com.fc.v2.mapper.auto.THmfBudgetSignMapper;
import com.fc.v2.model.auto.THmfBudgetBill;
import com.fc.v2.model.auto.THmfBudgetSign;
import com.fc.v2.service.ITHmfBudgetBillService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 预算审定拨付核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 口径（《拨付核签口径提示》）：
 * 三档挨着往前——0业委会（两笔凑齐）、1房管事务所（一笔即可）、2中心（一笔即可，落笔封单）；
 * 前一档没满，后一档落了名也按没签处理。齐不齐只认署名流水逐笔勾出的数，
 * 屏幕数、经手人自记数都只作参照。哪一档退回只挪回紧挨的上一档，旧名一个不抹，
 * 以新拨次（cur_round+1）从遭退档接着往上添，新旧两拨分存。封单后谁都不能再添。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class THmfBudgetBillServiceImpl implements ITHmfBudgetBillService {

    /** 口次：业委会核 */
    private static final int NODE_COMMITTEE = 0;
    /** 口次：房管事务所核 */
    private static final int NODE_FIRM = 1;
    /** 口次：中心核（末档，认下即封单） */
    private static final int NODE_CENTER = 2;

    /** 签法：任一笔即可（事务所、中心） */
    private static final int MODE_OR = 0;
    /** 签法：两笔点齐（业委会） */
    private static final int MODE_AND = 1;

    /** 报批情形：在核（含候签、打回后重走） */
    private static final int STATUS_RUNNING = 0;
    /** 报批情形：已核讫（中心认下，封单） */
    private static final int STATUS_PASS = 1;
    /** 报批情形：已打回（单据正落在上一档候重签） */
    private static final int STATUS_VETO = 2;

    /** 署名动作：签认 */
    private static final int ACTION_SIGN = 0;
    /** 署名动作：打回 */
    private static final int ACTION_REJECT = 1;

    @javax.annotation.Resource
    private THmfBudgetBillMapper hmfBudgetBillMapper;

    @javax.annotation.Resource
    private THmfBudgetSignMapper hmfBudgetSignMapper;

    @Override
    public THmfBudgetBill selectTHmfBudgetBillById(Long id) {
        return this.hmfBudgetBillMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfBudgetBill openBill(String billNo, String maker, String siteNo, Integer billYear,
                                   BigDecimal approvedPayAmt, String remark) {
        if (isBlank(billNo) || isBlank(maker) || isBlank(siteNo) || billYear == null || approvedPayAmt == null) {
            return null;
        }
        // 单号不许重
        if (this.hmfBudgetBillMapper.selectCount(new QueryWrapper<THmfBudgetBill>()
                .eq("bill_no", billNo).eq("del_flag", 0)) > 0) {
            return null;
        }
        // 同一小区同一年再来一张：另起独立新单（签名一律按本单 bill_id 挂，绝不并进旧单）。
        THmfBudgetBill r = new THmfBudgetBill();
        r.setBillNo(billNo);
        r.setMaker(maker);
        r.setSiteNo(siteNo);
        r.setBillYear(billYear);
        // 拨付额立单时一次取自预算审定额，库存即此值，屏显取此值，往后无手填入口。
        r.setPayAmt(approvedPayAmt);
        r.setRemark(remark);
        r.setNodeNo(NODE_COMMITTEE);
        r.setCurRound(1);
        r.setSignCount(0);
        mirrorNodeRule(r, NODE_COMMITTEE);
        r.setStatus(STATUS_RUNNING);
        r.setDelFlag(0);
        this.hmfBudgetBillMapper.insert(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfBudgetBill approve(Long id, String approver, String comment) {
        THmfBudgetBill r = this.hmfBudgetBillMapper.selectById(id);
        if (r == null || isBlank(approver)) {
            return null;
        }
        // 中心认下后封单，谁都不能再添名；已打回在候重签的单可以继续签。
        if (STATUS_PASS == (r.getStatus() == null ? STATUS_RUNNING : r.getStatus())) {
            return null;
        }
        int node = r.getNodeNo() == null ? NODE_COMMITTEE : r.getNodeNo();
        int round = r.getCurRound() == null ? 1 : r.getCurRound();

        // 顺序挨着往前：后一档落笔前，先前每一档都得在某一拨真正签齐过。
        // 中心越过事务所直接盖章、事务所越过业委会，一样不认。
        if (!priorStagesSigned(id, node)) {
            return null;
        }

        // 同一拨里同一人不许替两笔；两笔得是两笔署名。
        if (this.hmfBudgetSignMapper.selectCount(new QueryWrapper<THmfBudgetSign>()
                .eq("bill_id", id).eq("node_no", node).eq("round_no", round)
                .eq("signer", approver.trim()).eq("action", ACTION_SIGN).eq("del_flag", 0)) > 0) {
            return null;
        }

        THmfBudgetSign sign = newSignRow(id, node, round, approver.trim(), ACTION_SIGN, comment);
        this.hmfBudgetSignMapper.insert(sign);

        // 齐不齐只照署名流水逐笔勾，绝不认人手记数。
        int landed = countSigns(id, node, round);
        r.setSignCount(landed);
        mirrorNodeRule(r, node);
        r.setStatus(STATUS_RUNNING);

        if (landed >= needOf(node)) {
            if (node == NODE_CENTER) {
                // 中心这一档认下即封住
                r.setStatus(STATUS_PASS);
            } else {
                // 挨着往前挪一档；新档本拨还没名，候签。
                int next = node + 1;
                r.setNodeNo(next);
                mirrorNodeRule(r, next);
                r.setSignCount(countSigns(id, next, round));
            }
        }
        this.hmfBudgetBillMapper.updateById(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfBudgetBill reject(Long id, String approver, String comment) {
        THmfBudgetBill r = this.hmfBudgetBillMapper.selectById(id);
        if (r == null || isBlank(approver)) {
            return null;
        }
        if (STATUS_PASS == (r.getStatus() == null ? STATUS_RUNNING : r.getStatus())) {
            return null;
        }
        int node = r.getNodeNo() == null ? NODE_COMMITTEE : r.getNodeNo();
        // 业委会已是头一档，再退无处可退；退回只退一个手。
        if (node <= NODE_COMMITTEE) {
            return null;
        }
        int round = r.getCurRound() == null ? 1 : r.getCurRound();

        // 退回本身也逐笔留痕：谁、在哪一档、第几拨、几时退的。
        THmfBudgetSign rejectRow = newSignRow(id, node, round, approver.trim(), ACTION_REJECT, comment);
        this.hmfBudgetSignMapper.insert(rejectRow);

        int back = node - 1;
        // 新旧两拨分开放：拨次+1，先前各档旧名原样留存，一个不抹。
        int newRound = round + 1;
        r.setCurRound(newRound);
        r.setNodeNo(back);
        mirrorNodeRule(r, back);
        r.setSignCount(countSigns(id, back, newRound));
        r.setStatus(STATUS_VETO);
        this.hmfBudgetBillMapper.updateById(r);
        return r;
    }

    @Override
    public THmfBudgetBill rollback(Long id, String comment) {
        // 后一格跟着事务走：谁实际操作就续谁的名。
        return reject(id, ShiroUtils.getLoginName(), comment);
    }

    @Override
    public List<THmfBudgetSign> listSignTrail(Long id) {
        if (id == null || this.hmfBudgetBillMapper.selectById(id) == null) {
            return null;
        }
        // 复查从中心倒着点回业委会；同口旧拨在前、新拨在后，时刻定先后。
        return this.hmfBudgetSignMapper.selectList(new QueryWrapper<THmfBudgetSign>()
                .eq("bill_id", id).eq("del_flag", 0)
                .orderByDesc("node_no").orderByAsc("round_no")
                .orderByAsc("sign_time").orderByAsc("id"));
    }

    /** 照署名流水点当前拨次本口的签认笔数。 */
    private int countSigns(Long billId, int node, int round) {
        Integer c = this.hmfBudgetSignMapper.selectCount(new QueryWrapper<THmfBudgetSign>()
                .eq("bill_id", billId).eq("node_no", node).eq("round_no", round)
                .eq("action", ACTION_SIGN).eq("del_flag", 0));
        return c == null ? 0 : c;
    }

    /**
     * 先前各档须曾在某一拨真正签齐（同拨两笔各有其人）。
     * 照署名流水点，不认单据上的手数；任何前档没满，本笔按没签处理。
     */
    private boolean priorStagesSigned(Long billId, int node) {
        List<THmfBudgetSign> all = this.hmfBudgetSignMapper.selectList(new QueryWrapper<THmfBudgetSign>()
                .eq("bill_id", billId).eq("action", ACTION_SIGN).eq("del_flag", 0));
        Map<Integer, Map<Integer, Set<String>>> byNodeRound = new HashMap<>();
        for (THmfBudgetSign s : all) {
            if (s.getNodeNo() == null || s.getRoundNo() == null) {
                continue;
            }
            byNodeRound
                    .computeIfAbsent(s.getNodeNo(), k -> new HashMap<>())
                    .computeIfAbsent(s.getRoundNo(), k -> new HashSet<>())
                    .add(s.getSigner());
        }
        for (int n = NODE_COMMITTEE; n < node; n++) {
            Map<Integer, Set<String>> rounds = byNodeRound.get(n);
            if (rounds == null) {
                return false;
            }
            final int need = needOf(n);
            boolean ok = rounds.values().stream().anyMatch(names -> names.size() >= need);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 把本口定死的签法与应落名数写到单子上（窗口四处字之镜像）。 */
    private void mirrorNodeRule(THmfBudgetBill r, int node) {
        r.setSignMode(node == NODE_COMMITTEE ? MODE_AND : MODE_OR);
        r.setNeedCount(needOf(node));
    }

    /** 三档应落名数定死：业委会两笔凑齐，事务所一笔，中心一笔。 */
    private int needOf(int node) {
        return node == NODE_COMMITTEE ? 2 : 1;
    }

    private THmfBudgetSign newSignRow(Long billId, int node, int round, String signer, int action, String remark) {
        THmfBudgetSign s = new THmfBudgetSign();
        s.setBillId(billId);
        s.setNodeNo(node);
        s.setRoundNo(round);
        s.setSigner(signer);
        s.setAction(action);
        s.setSignTime(new Date());
        s.setDelFlag(0);
        s.setRemark(remark);
        return s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
