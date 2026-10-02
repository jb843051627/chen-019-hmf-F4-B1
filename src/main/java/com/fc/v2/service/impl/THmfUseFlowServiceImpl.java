package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfAcctCardMapper;
import com.fc.v2.mapper.auto.THmfPayRowMapper;
import com.fc.v2.mapper.auto.THmfUseFlowMapper;
import com.fc.v2.mapper.auto.THmfUseFlowStageLogMapper;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfPayRow;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.auto.THmfUseFlowStageLog;
import com.fc.v2.model.custom.UseFlowAdvance;
import com.fc.v2.model.custom.UseFlowAudit;
import com.fc.v2.model.custom.UseFlowVerdict;
import com.fc.v2.service.ITHmfUseFlowService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 维修资金使用申请单 Service业务层处理（四档链：待发起→立项→公示→施工→结算）
 *
 * 口径（今年钉死的四档链）：
 * 一档一档挨着走：立项(0)四样齐才起得来——工程地点、预算金额、施工单位名称、申请来源；
 * 公示(1)天数照当地定数，从进档落笔起算，天数没走完施工门开着也不许进；
 * 施工(2)看合同要件挂没挂齐；结算(3)看验收记载与监理那一名到没到，且户面余额够列支。
 * 进结算即列支落地、钱从户面余额划走、本趟办结；余额不够停在结算门外，整张账不动。
 *
 * 认定只在 {@link #pushUseFlow} 这一个入口发生：当前段次由系统照过口痕迹重放数出，
 * 纸面（单上镜像、前端意图段号）与算出来的不一时，以算出来的为准。
 * 按公示天数自推与柜员手点两条来路并入同一推进算法，谁先满足谁落，第二遍不另起一行。
 * 往后一次收一档，旧记沉底、另起一拨重攒；作废冻字冻张。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Service
public class THmfUseFlowServiceImpl implements ITHmfUseFlowService {

    /** 档：立项（头一档） */
    private static final int STAGE_INIT = 0;
    /** 档：公示 */
    private static final int STAGE_PUBLIC = 1;
    /** 档：施工 */
    private static final int STAGE_BUILD = 2;
    /** 档：结算（走到即办结） */
    private static final int STAGE_SETTLE = 3;
    /** 尚未进头一档 */
    private static final int STAGE_NONE = -1;

    /** 会落：未起 */
    private static final int STATUS_IDLE = 0;
    /** 会落：在办（含退回候重走的中止态） */
    private static final int STATUS_RUNNING = 1;
    /** 会落：已作废（字改不得、张删不得） */
    private static final int STATUS_VOID = 2;
    /** 会落：已办结（结算列讫，一趟走完） */
    private static final int STATUS_DONE = 3;

    /** 痕迹动作：过口（往前进入紧邻下一档） */
    private static final int ACT_PASS = 0;
    /** 痕迹动作：退回（收回一档） */
    private static final int ACT_BACK = 1;
    /** 痕迹动作：列支（进结算时划款） */
    private static final int ACT_PAY = 2;
    /** 痕迹动作：作废 */
    private static final int ACT_VOID = 3;

    /** 落库时刻晚于落笔此刻多少即认作事后补录（正常两行同一时刻写出） */
    private static final long BACKFILL_TOLERANCE_MS = 60_000L;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    /** 系统自推来路的署名（与柜员手点并到同一推进算法，只在经办人处分辨来路） */
    private static final String OPERATOR_SWEEP = "系统按公示天数推进";

    /** 记账时刻：生产取当前时刻；留一道口子给定点核验/测试。 */
    protected Date nowTime() {
        return new Date();
    }

    @javax.annotation.Resource
    private THmfUseFlowMapper hmfUseFlowMapper;

    @javax.annotation.Resource
    private THmfUseFlowStageLogMapper hmfUseFlowStageLogMapper;

    @javax.annotation.Resource
    private THmfAcctCardMapper hmfAcctCardMapper;

    @javax.annotation.Resource
    private THmfPayRowMapper hmfPayRowMapper;

    @javax.annotation.Resource
    private com.fc.v2.mapper.auto.THmfPayBookMapper hmfPayBookMapper;

    // ---------------------------------------------------------------- 旧读法不动

    @Override
    public THmfUseFlow selectTHmfUseFlowById(Long id) {
        return this.hmfUseFlowMapper.selectById(id);
    }

    @Override
    public List<THmfUseFlow> selectTHmfUseFlowList(QueryWrapper<THmfUseFlow> queryWrapper) {
        return this.hmfUseFlowMapper.selectList(queryWrapper);
    }

    // ---------------------------------------------------------------- 起单进立项

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow openFlow(String bizNo, String siteNo, String projectPlace, BigDecimal budgetAmt,
                                String builderName, String applySource, String operator, String remark) {
        if (isBlank(bizNo) || isBlank(siteNo) || isBlank(projectPlace) || isBlank(builderName)
                || isBlank(applySource)
                || budgetAmt == null || budgetAmt.compareTo(BigDecimal.ZERO) <= 0) {
            // 立项四样缺一样，立项起不来（底册代号也得有，户面余额才有点处）。
            return null;
        }
        // 一份申请底下，前一张还在办、退回候重走（中止）或已作废时，都起不了第二张；
        // 只有前一张照痕迹真正走到结算办结，才许另起新单。认定听痕迹，不听单上印的会落。
        List<THmfUseFlow> same = this.hmfUseFlowMapper.selectList(new QueryWrapper<THmfUseFlow>()
                .eq("biz_no", bizNo.trim()).eq("del_flag", 0));
        for (THmfUseFlow old : same) {
            Replay prior = replay(old.getId());
            if (prior.voided || prior.cur < STAGE_SETTLE) {
                return null;
            }
        }

        Date now = nowTime();
        String op = isBlank(operator) ? ShiroUtils.getLoginName() : operator.trim();

        THmfUseFlow r = new THmfUseFlow();
        r.setBizNo(bizNo.trim());
        r.setSiteNo(siteNo.trim());
        r.setProjectPlace(projectPlace.trim());
        r.setBudgetAmt(budgetAmt);
        r.setBuilderName(builderName.trim());
        r.setApplySource(applySource.trim());
        r.setStage(STAGE_INIT);
        r.setCurRound(1);
        r.setStatus(STATUS_RUNNING);
        r.setDelFlag(0);
        r.setLastAction("进立项");
        r.setContent("立项四样齐，起单进立项档（经办：" + op + "）");
        r.setRemark(remark);
        this.hmfUseFlowMapper.insert(r);

        addLog(r.getId(), STAGE_INIT, 1, ACT_PASS, op, now, null, "立项四样齐："
                + projectPlace.trim() + "／预算" + budgetAmt.toPlainString()
                + "／" + builderName.trim() + "／" + applySource.trim());
        return r;
    }

    // ---------------------------------------------------------------- 唯一推进入口

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UseFlowVerdict pushUseFlow(UseFlowAdvance advance) {
        if (advance == null || advance.getId() == null) {
            return null;
        }
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(advance.getId());
        if (r == null) {
            return null;
        }
        Date now = advance.getNow() == null ? nowTime() : advance.getNow();
        String op = isBlank(advance.getOperator()) ? ShiroUtils.getLoginName() : advance.getOperator().trim();

        // 权威会落只照已过痕迹重放数出；单上写的、屏上带的都只作参照。
        Replay nowState = replay(advance.getId());
        int cur = nowState.cur;
        int round = nowState.round;

        // 作废的单：字改不得、张删不得，记事也续不动（单上印与痕迹任一为废都认）。
        if (isVoid(r) || nowState.voided) {
            mirror(r, Math.max(cur, STAGE_INIT));
            return new UseFlowVerdict(Math.max(cur, STAGE_INIT), UseFlowVerdict.CODE_VOID,
                    "该单已作废，不能再续记事、不能再过口", r);
        }
        // 走到结算列讫即办结（权威段次在结算），无下一檔可收。
        if (cur >= STAGE_SETTLE) {
            mirror(r, STAGE_SETTLE);
            return new UseFlowVerdict(STAGE_SETTLE, UseFlowVerdict.CODE_STAY,
                    "已走到结算档列讫，本趟办结，无下一檔可收", r);
        }

        // 动作只有两种：往前、往后。方向由动作明说，不靠屏上段号猜——
        // 两条来路抢同一脚时，落后那个带旧段号也不会被误当成退回。
        boolean backward = advance.getAction() != null && advance.getAction() < 0;
        Integer expect = advance.getExpectStage();
        if (backward) {
            if (expect != null && expect < cur - 1) {
                return new UseFlowVerdict(cur, UseFlowVerdict.CODE_STAY,
                        "往后只收紧邻的上一档，一次一档", mirror(r, cur));
            }
            if (expect != null && expect >= cur) {
                return new UseFlowVerdict(cur, UseFlowVerdict.CODE_STAY,
                        "退回段号与当前档不符，以系统回话的段次为准", mirror(r, cur));
            }
            return doBackward(r, cur, round, op, now, advance.getRemark());
        }

        if (expect != null && expect == cur) {
            // 同一档报第二遍：账上还是头一遍那句话，不另起一行。
            return new UseFlowVerdict(cur, UseFlowVerdict.CODE_DUP,
                    "本档头一遍已记上，第二遍不另起一行", mirror(r, cur));
        }
        if (expect != null && expect > cur + 1) {
            // 想越档往前：只进紧邻的下一档。
            return new UseFlowVerdict(cur, UseFlowVerdict.CODE_STAY,
                    "往前只进紧邻的下一档，不许越档", mirror(r, cur));
        }
        if (expect != null && expect < cur) {
            // 屏上段号落后（另一来路已先推过）：以系统回话为准，不另起一行。
            return new UseFlowVerdict(cur, UseFlowVerdict.CODE_STAY,
                    "该档已走过，段次以系统回话为准", mirror(r, cur));
        }
        return doForward(r, cur, round, op, now, advance);
    }

    /**
     * 往前一条：公示天数自推与柜员手点都走这里。门槛不过停在原档，
     * 不添任何痕迹，整张账不动。
     */
    private UseFlowVerdict doForward(THmfUseFlow r, int cur, int round, String op,
                                     Date now, UseFlowAdvance advance) {
        Long id = r.getId();
        switch (cur) {
            case STAGE_INIT: {
                // 立项 → 公示：守当地定的公示天数，天数定不下来公示档不接。
                Integer days = advance.getPublicDays();
                if (days == null || days <= 0) {
                    return stay(r, cur, "公示天数未定，公示档不收");
                }
                r.setPublicDays(days);
                r.setPublicStart(now);
                addLog(id, STAGE_PUBLIC, round, ACT_PASS, op, now, null,
                        "进公示档，公示" + days + "天，自此刻起算");
                move(r, STAGE_PUBLIC, "进公示（公示" + days + "天）",
                        "公示天数落定，自" + now + "起算（经办：" + op + "）");
                return moved(r, STAGE_PUBLIC, "已进公示档，照" + days + "天公示期起算");
            }
            case STAGE_PUBLIC: {
                // 公示 → 施工：公示天数没走完，材料递得再齐、施工那扇门开着也不能进。
                Integer days = r.getPublicDays();
                Date start = r.getPublicStart();
                if (days == null || start == null) {
                    return stay(r, cur, "公示起算缺失，施工门不开");
                }
                long elapsed = now.getTime() - start.getTime();
                if (elapsed < days * ONE_DAY_MS) {
                    long remain = days - elapsed / ONE_DAY_MS;
                    return stay(r, cur, "公示天数没走完，还差" + remain + "天，施工门不开");
                }
                addLog(id, STAGE_BUILD, round, ACT_PASS, op, now, null,
                        "公示" + days + "天走完，进施工档");
                move(r, STAGE_BUILD, "进施工", "公示期满，进施工档（经办：" + op + "）");
                return moved(r, STAGE_BUILD, "公示期满，已进施工档");
            }
            case STAGE_BUILD: {
                // 施工 → 结算：合同要件先挂齐；验收记载、监理那一名到没到；户面余额够不够列支。
                if (isBlank(advance.getContractNo()) && isBlank(r.getContractNo())) {
                    return stay(r, cur, "合同要件没挂齐，结算门不开");
                }
                if (isBlank(advance.getAcceptRecord())) {
                    return stay(r, cur, "验收记载没到，结算门不开");
                }
                if (isBlank(advance.getSupervisor())) {
                    return stay(r, cur, "监理那一名没到，结算门不开");
                }
                BigDecimal budget = r.getBudgetAmt();
                // 余额不由前端递（那等于又开一个把手）：服务层照底册自己点
                // 已销账交存合计减去同底册已列支。
                BigDecimal balance = accountBalanceOf(r.getSiteNo());
                if (budget == null || balance == null || balance.compareTo(budget) < 0) {
                    // 余额不够：停在结算门外，整张账不动（合同/验收也不登记、不添行）。
                    return stay(r, cur, "户面余额"
                            + (balance == null ? "点不出来" : balance.toPlainString())
                            + "，不够列支" + (budget == null ? "" : budget.toPlainString())
                            + "，停在结算门外");
                }
                if (!isBlank(advance.getContractNo())) {
                    r.setContractNo(advance.getContractNo().trim());
                }
                r.setAcceptRecord(advance.getAcceptRecord().trim());
                r.setSupervisor(advance.getSupervisor().trim());

                // 进结算与列支是同一脚：痕迹两条（过口、列支）同一时刻落，钱划走，办结。
                addLog(id, STAGE_SETTLE, round, ACT_PASS, op, now, null,
                        "合同、验收、监理齐，余额足，进结算档");
                addLog(id, STAGE_SETTLE, round, ACT_PAY, op, now, budget,
                        "列支落地，自户面余额划走" + budget.toPlainString());
                r.setPayAmt(budget);
                r.setPayTime(now);
                r.setStage(STAGE_SETTLE);
                r.setStatus(STATUS_DONE);
                r.setLastAction("进结算列支办结");
                appendContent(r, "进结算列讫" + budget.toPlainString() + "，本趟办结（经办：" + op + "）");
                this.hmfUseFlowMapper.updateById(r);
                return new UseFlowVerdict(STAGE_SETTLE, UseFlowVerdict.CODE_MOVED,
                        "已进结算档列讫" + budget.toPlainString() + "，本趟办结", r);
            }
            default:
                return stay(r, cur, "无下一檔可收");
        }
    }

    /** 往后一条：一次收一档，收回来那档原先记的事沉底，另起一拨从这一档重新攒。 */
    private UseFlowVerdict doBackward(THmfUseFlow r, int cur, int round, String op, Date now, String remark) {
        if (cur <= STAGE_INIT) {
            return stay(r, cur, "立项是头一档，再退无处可退");
        }
        Long id = r.getId();
        int back = cur - 1;
        int newRound = round + 1;

        // 退回本身逐笔留痕：从当前档收回，旧拨各档的事原样沉底不抹。
        addLog(id, cur, round, ACT_BACK, op, now, null,
                isBlank(remark) ? "收回上一档重走" : remark.trim());

        // 收回来那档原先记的事沉下去：离开档及以后本拨攒的件随旧拨沉底，
        // 重走时从落回的那一档重新攒；落回公示档的，公示天数照当地定数，起算重新来。
        sinkOnBack(r, cur, back, now);
        r.setCurRound(newRound);
        r.setStage(back);
        r.setStatus(STATUS_RUNNING);
        r.setLastAction("退回" + stageName(back) + "档");
        appendContent(r, "从" + stageName(cur) + "档收回" + stageName(back)
                + "档，旧记沉底，第" + newRound + "拨重攒（经办：" + op + "）");
        this.hmfUseFlowMapper.updateById(r);
        return new UseFlowVerdict(back, UseFlowVerdict.CODE_MOVED,
                "已收回" + stageName(back) + "档，旧记沉底，按新拨重走", r);
    }

    // ---------------------------------------------------------------- 公示天数自推来路

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sweepDuePublicity(Date now) {
        Date at = now == null ? nowTime() : now;
        // 不照单上会落镜像挑单，把在册单都取出来，按痕迹重放认它此刻在不在公示档。
        List<THmfUseFlow> actives = this.hmfUseFlowMapper.selectList(new QueryWrapper<THmfUseFlow>()
                .eq("del_flag", 0));
        int moved = 0;
        for (THmfUseFlow r : actives) {
            Replay state = replay(r.getId());
            if (state.cur != STAGE_PUBLIC || state.voided) {
                continue;
            }
            Integer days = r.getPublicDays();
            Date start = r.getPublicStart();
            if (days == null || start == null) {
                continue;
            }
            if (at.getTime() - start.getTime() < days * ONE_DAY_MS) {
                continue;
            }
            // 天数走完：与柜员手点同一个推进算法、同一脚过门；柜员若已先点过，
            // 重放出来的段次已不在公示档，这里自然扫不到，不会落第二行。
            UseFlowAdvance auto = new UseFlowAdvance();
            auto.setId(r.getId());
            auto.setNow(at);
            auto.setOperator(OPERATOR_SWEEP);
            UseFlowVerdict v = doForward(this.hmfUseFlowMapper.selectById(r.getId()),
                    state.cur, state.round, OPERATOR_SWEEP, at, auto);
            if (v != null && v.getCode() == UseFlowVerdict.CODE_MOVED) {
                moved++;
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------- 作废

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow voidUseFlow(Long id, String operator, String remark) {
        THmfUseFlow r = id == null ? null : this.hmfUseFlowMapper.selectById(id);
        if (r == null || isVoid(r)) {
            return null;
        }
        // 走到结算列讫即办结，钱已划走，不许再盖作废印（段次听痕迹）。
        Replay state = replay(id);
        if (state.voided) {
            return null;
        }
        if (state.cur >= STAGE_SETTLE) {
            return null;
        }
        String op = isBlank(operator) ? ShiroUtils.getLoginName() : operator.trim();
        Date now = nowTime();
        addLog(id, Math.max(state.cur, STAGE_INIT), state.round, ACT_VOID, op, now, null,
                isBlank(remark) ? "单据作废" : remark.trim());
        // 只盖作废印：字不改、张不删，此后记事续不动、过口走不动。
        r.setStatus(STATUS_VOID);
        r.setLastAction("作废");
        appendContent(r, "单据作废封存，字改不得、张删不得（经办：" + op + "）");
        this.hmfUseFlowMapper.updateById(r);
        return r;
    }

    // ---------------------------------------------------------------- 倒查那笔账

    @Override
    public UseFlowAudit auditUseFlow(Long id) {
        UseFlowAudit audit = new UseFlowAudit();
        THmfUseFlow r = id == null ? null : this.hmfUseFlowMapper.selectById(id);
        if (r == null) {
            audit.setPass(false);
            audit.setReason("查无此单");
            return audit;
        }

        List<THmfUseFlowStageLog> logs = logsOf(id);
        Replay state = replay(id, logs);

        // 现行有效一趟：每档只取当前拨次真正进来的那一条（旧拨行已随退回沉底，另见流水），
        // 加上列支（或作废）行；倒序回话。
        List<THmfUseFlowStageLog> effective = new ArrayList<>();
        for (int st = STAGE_SETTLE; st >= STAGE_INIT; st--) {
            THmfUseFlowStageLog row = state.enteredRow.get(st);
            if (row != null) {
                effective.add(row);
            }
            // 列支紧跟结算档过口行（同一刻两脚，倒查时并排捋）
            if (st == STAGE_SETTLE && state.payRow != null) {
                effective.add(state.payRow);
            }
        }
        if (state.voidRow != null) {
            effective.add(state.voidRow);
        }
        audit.setTrail(effective);

        List<THmfUseFlowStageLog> bad = new ArrayList<>(state.badRows);
        if (state.voided) {
            return fail(audit, bad, "该单已作废，这趟不算走通");
        }
        // 事后补进去的：落笔时刻与落库时刻差得太开，任一行见着即不通。
        for (THmfUseFlowStageLog l : logs) {
            if (isBackfilled(l)) {
                bad.add(l);
                return fail(audit, bad, "有事后补录的痕迹（" + stageName(l.getStageNo())
                        + "档，" + l.getLogTime() + "），这趟没走通");
            }
        }
        if (state.cur != STAGE_SETTLE) {
            return fail(audit, bad, state.cur < STAGE_INIT
                    ? "缺立项起头的痕迹，这趟没走通"
                    : "缺" + stageName(state.cur + 1) + "档的过口痕迹，这趟没走通");
        }
        THmfUseFlowStageLog pay = state.payRow;
        if (pay == null) {
            return fail(audit, bad, "走到结算却无列支落地记载，这趟没走通");
        }
        if (r.getBudgetAmt() == null || pay.getPayAmt() == null
                || pay.getPayAmt().compareTo(r.getBudgetAmt()) != 0) {
            bad.add(pay);
            return fail(audit, bad, "列支金额与预算金额对不上，这趟没走通");
        }
        if (!bad.isEmpty()) {
            return fail(audit, bad, "多出记载或缺档痕迹，这趟没走通");
        }
        audit.setPass(true);
        audit.setReason("四档挨着走过，每档当前拨次各一条过口痕迹，列支"
                + pay.getPayAmt().toPlainString() + "与预算咬合，这趟走通");
        return audit;
    }

    // ---------------------------------------------------------------- 痕迹重放（段次唯一算法）

    /**
     * 照过口痕迹时间序重放：过口进紧邻下一档、退回收一档并另起一拨。
     * 当前段次、当前拨次、作废态、各档当前拨次进入行、列支行、坏行都只从这笔账算出来；
     * 旧拨行（退回后沉底的过口行）原样留存但不计数，属合规沉底，不算多出的记载。
     */
    private Replay replay(Long flowId) {
        return replay(flowId, logsOf(flowId));
    }

    private Replay replay(Long flowId, List<THmfUseFlowStageLog> logs) {
        Replay s = new Replay();
        s.cur = STAGE_NONE;
        s.round = 1;
        for (THmfUseFlowStageLog l : logs) {
            int action = l.getAction() == null ? ACT_PASS : l.getAction();
            int stage = l.getStageNo() == null ? STAGE_NONE : l.getStageNo();
            int rowRound = l.getRoundNo() == null ? 1 : l.getRoundNo();
            switch (action) {
                case ACT_VOID:
                    s.voided = true;
                    s.voidRow = l;
                    return s;
                case ACT_PASS:
                    if (rowRound == s.round && stage == s.cur + 1 && stage >= STAGE_INIT
                            && stage <= STAGE_SETTLE) {
                        s.cur = stage;
                        s.enteredRow.put(stage, l);
                    } else if (rowRound == s.round && stage == s.cur && s.cur >= STAGE_INIT) {
                        // 同拨同档出现第二条过口：多出一条记载。
                        s.badRows.add(l);
                    }
                    // 其余为退回后沉底的旧拨行，原样留存但不计入现行段次（合规）。
                    break;
                case ACT_BACK:
                    if (rowRound == s.round && stage == s.cur && s.cur > STAGE_INIT) {
                        s.cur--;
                        s.round++;
                    } else {
                        s.badRows.add(l);
                    }
                    break;
                case ACT_PAY:
                    if (s.cur == STAGE_SETTLE && rowRound == s.round) {
                        if (s.payRow != null) {
                            s.badRows.add(l);
                        } else {
                            s.payRow = l;
                        }
                    } else {
                        s.badRows.add(l);
                    }
                    break;
                default:
                    s.badRows.add(l);
            }
        }
        return s;
    }

    private static class Replay {
        int cur;
        int round;
        boolean voided;
        THmfUseFlowStageLog voidRow;
        THmfUseFlowStageLog payRow;
        final Map<Integer, THmfUseFlowStageLog> enteredRow = new HashMap<>();
        final List<THmfUseFlowStageLog> badRows = new ArrayList<>();
    }

    // ---------------------------------------------------------------- 户面余额

    /**
     * 点某底册此刻户面可用余额：经分户账立户单挂到该底册的已销账交存行合计，
     * 减去同底册各单已列支合计。列支前点，钱不够结算门不开。
     */
    private BigDecimal accountBalanceOf(String siteNo) {
        if (isBlank(siteNo)) {
            return null;
        }
        List<THmfAcctCard> cards = this.hmfAcctCardMapper.selectList(new QueryWrapper<THmfAcctCard>()
                .eq("site_no", siteNo.trim()).eq("del_flag", 0));
        java.util.Set<String> accounts = new java.util.HashSet<>();
        for (THmfAcctCard c : cards) {
            if (!isBlank(c.getBillNo())) {
                accounts.add(c.getBillNo().trim());
            }
        }
        BigDecimal deposited = BigDecimal.ZERO;
        if (!accounts.isEmpty()) {
            // 户面认哪些行与册子核销的户面累加同源（规矩合在 HouseholdPaid 一处）：
            // 只认已销账、现行册面层、未删的行；已撤册的行随册作废，不计户面；
            // 旧批法无册头（book_id 为空）的行照旧计。
            java.util.Set<Long> activeBookIds =
                    HouseholdPaid.activeBookIds(this.hmfPayBookMapper);
            for (THmfPayRow row : HouseholdPaid.clearedCurrentRows(this.hmfPayRowMapper)) {
                if (!HouseholdPaid.countsTowardHousehold(row, activeBookIds)) {
                    continue;
                }
                if (row.getItemCode() != null && accounts.contains(row.getItemCode().trim())
                        && row.getQty() != null) {
                    // 到账金额是算钱的底子：只认归一到库口精度后的数，与进门处同源。
                    deposited = deposited.add(PayRowAmounts.normalize(row.getQty()));
                }
            }
        }
        BigDecimal spent = BigDecimal.ZERO;
        List<THmfUseFlow> sameSite = this.hmfUseFlowMapper.selectList(new QueryWrapper<THmfUseFlow>()
                .eq("site_no", siteNo.trim()).eq("del_flag", 0));
        for (THmfUseFlow f : sameSite) {
            for (THmfUseFlowStageLog l : logsOf(f.getId())) {
                if (l.getAction() != null && l.getAction() == ACT_PAY && l.getPayAmt() != null) {
                    spent = spent.add(l.getPayAmt());
                }
            }
        }
        return deposited.subtract(spent);
    }

    // ---------------------------------------------------------------- 私有帮手

    private List<THmfUseFlowStageLog> logsOf(Long flowId) {
        return this.hmfUseFlowStageLogMapper.selectList(new QueryWrapper<THmfUseFlowStageLog>()
                .eq("flow_id", flowId).eq("del_flag", 0)
                .orderByAsc("log_time").orderByAsc("id"));
    }

    private void addLog(Long flowId, int stage, int round, int action, String operator,
                        Date time, BigDecimal payAmt, String remark) {
        THmfUseFlowStageLog l = new THmfUseFlowStageLog();
        l.setFlowId(flowId);
        l.setStageNo(stage);
        l.setRoundNo(round);
        l.setAction(action);
        l.setOperator(operator);
        l.setLogTime(time);
        // 正常一笔的落笔时刻即落库时刻；事后补录者两刻岔开，倒查据此认出。
        l.setCreateTime(time);
        l.setPayAmt(payAmt);
        l.setDelFlag(0);
        l.setRemark(remark);
        this.hmfUseFlowStageLogMapper.insert(l);
    }

    private void move(THmfUseFlow r, int stage, String lastAction, String contentLine) {
        r.setStage(stage);
        r.setStatus(STATUS_RUNNING);
        r.setLastAction(lastAction);
        appendContent(r, contentLine);
        this.hmfUseFlowMapper.updateById(r);
    }

    private UseFlowVerdict stay(THmfUseFlow r, int authoritativeStage, String message) {
        // 门槛不过：不添痕迹、不改段次，镜像段次校正为系统算出的权威值后回话。
        return new UseFlowVerdict(authoritativeStage, UseFlowVerdict.CODE_STAY, message, mirror(r, authoritativeStage));
    }

    private UseFlowVerdict moved(THmfUseFlow r, int authoritativeStage, String message) {
        return new UseFlowVerdict(authoritativeStage, UseFlowVerdict.CODE_MOVED, message, r);
    }

    /** 屏上带出的段号/单上镜像与系统算出来的不齐时，以算出来的为准：校正镜像。 */
    private THmfUseFlow mirror(THmfUseFlow r, int authoritativeStage) {
        if (r.getStage() == null || r.getStage() != authoritativeStage) {
            r.setStage(authoritativeStage);
            this.hmfUseFlowMapper.updateById(r);
        }
        return r;
    }

    /**
     * 退回沉底：离开档及以后本拨攒下的门槛件随旧拨沉底，重走重新攒；
     * 落回公示档则保留当地定的公示天数、起算时刻拨回退回此刻（公示从头再走）；
     * 落回立项档连公示天数也沉掉，重走到公示时重新落定。
     */
    private void sinkOnBack(THmfUseFlow r, int cur, int back, Date now) {
        if (cur >= STAGE_BUILD) {
            r.setContractNo(null);
        }
        if (cur >= STAGE_SETTLE) {
            r.setAcceptRecord(null);
            r.setSupervisor(null);
            r.setPayAmt(null);
            r.setPayTime(null);
        }
        if (back == STAGE_PUBLIC) {
            r.setPublicStart(now);
        } else if (back == STAGE_INIT) {
            r.setPublicDays(null);
            r.setPublicStart(null);
        }
    }

    private void appendContent(THmfUseFlow r, String line) {
        String old = r.getContent();
        r.setContent(isBlank(old) ? line : old + "\n" + line);
    }

    private boolean isVoid(THmfUseFlow r) {
        return r.getStatus() != null && r.getStatus() == STATUS_VOID;
    }

    private boolean isBackfilled(THmfUseFlowStageLog l) {
        return l.getLogTime() != null && l.getCreateTime() != null
                && l.getCreateTime().getTime() - l.getLogTime().getTime() > BACKFILL_TOLERANCE_MS;
    }

    private UseFlowAudit fail(UseFlowAudit audit, List<THmfUseFlowStageLog> bad, String reason) {
        audit.setPass(false);
        audit.setBadRows(bad);
        audit.setReason(reason);
        return audit;
    }

    private String stageName(int stage) {
        switch (stage) {
            case STAGE_INIT: return "立项";
            case STAGE_PUBLIC: return "公示";
            case STAGE_BUILD: return "施工";
            case STAGE_SETTLE: return "结算";
            default: return "待发起";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
