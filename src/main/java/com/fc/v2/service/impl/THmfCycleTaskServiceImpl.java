package com.fc.v2.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfArchMapper;
import com.fc.v2.mapper.auto.THmfCycleLogMapper;
import com.fc.v2.mapper.auto.THmfCycleTaskMapper;
import com.fc.v2.model.auto.THmfArch;
import com.fc.v2.model.auto.THmfCycleLog;
import com.fc.v2.model.auto.THmfCycleTask;
import com.fc.v2.model.custom.CycleSweep;
import com.fc.v2.service.CycleNoticeSender;
import com.fc.v2.service.ITHmfCycleTaskService;

/**
 * 结息催交单 Service业务层处理（scheduling-job 形状：照日子由系统自己走，人只管看结果）。
 *
 * 口径（今年钉死）：
 * 挑选只看一条——该出手那天（止点，含提前期）到没到。{@link #listDue(Date)} 这把尺
 * 查单与 {@link #sweepCycleTasks(Date)} 回写共用，两处不许各自算各自的。止点钉死，
 * 绝不往后挪凑批；能商量的只有“提前几天开口”那一格（amount）。
 * 时段另割一层：平常日子班内 08:00–20:00 才出手，夜里与法定节假日整段压着，
 * 已到期的也等下一轮开口；年终封存那半个月单另走（见 {@link CycleWindow}），
 * 期内同户同日只认头一回落定的结果，后到的进不了账。已办结的不会再被当候办捞进来。
 *
 * 办结落两笔：情形翻成已办结与完成那一刻同脚落，缺一笔不算完。催交发两路——
 * 物业站内消息、业委会联系人手机短信，各送各记一笔，谁也顶不了谁。
 * 没开单先撞墙（底册已迁走／联系人栏一个季度没填）当场判催不动并记缘由，
 * 条目仍翻得到，别处照跑，不因一处卡住掐掉整轮。过了止点那天还没办出去的，
 * 撤回转催不动，撤回留痕可查，但原定日子与真正完成那一刻不被后一笔盖掉。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class THmfCycleTaskServiceImpl implements ITHmfCycleTaskService {

    /** 条目情形 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    /** 事由 */
    private static final int BIZ_INTEREST = 1;
    private static final int BIZ_URGE = 2;

    /** 轮次动作：办结（情形翻转+完成刻，两笔同脚） */
    private static final int ACT_DONE = 1;
    /** 轮次动作：还没开单先撞墙，判催不动 */
    private static final int ACT_BLOCK = 2;
    /** 轮次动作：过了止点日未办，撤回转催不动 */
    private static final int ACT_WITHDRAW = 3;
    /** 轮次动作：封存期同户同日第二条，拒收 */
    private static final int ACT_SEAL_DUP = 4;
    /** 轮次动作：窗口压着，候办等下一轮 */
    private static final int ACT_HELD = 5;
    /** 轮次动作：催交站内送达行 */
    private static final int ACT_SEND_STATION = 10;
    /** 轮次动作：催交短信送达行 */
    private static final int ACT_SEND_SMS = 11;

    /** 送达路 */
    private static final int CHANNEL_STATION = 1;
    private static final int CHANNEL_SMS = 2;
    private static final int DELIVERED = 1;
    private static final int NOT_DELIVERED = 0;

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private static final String OPERATOR = "结息催交定时任务";

    @javax.annotation.Resource
    private THmfCycleTaskMapper hmfCycleTaskMapper;

    @javax.annotation.Resource
    private THmfCycleLogMapper hmfCycleLogMapper;

    @javax.annotation.Resource
    private THmfArchMapper hmfArchMapper;

    /** 催办两路送达口；未接线时两路都按未送达记，不许假装发过。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CycleNoticeSender noticeSender;

    /** 出手历窗（查单/回写共用）。 */
    private final CycleWindow window = new CycleWindow();

    /** 记账时刻：生产取当前；留口子给定点核验/测试。 */
    protected Date nowTime() {
        return new Date();
    }

    /** 登记当年法定节假日（yyyy-MM-dd），测试/放假安排另册填用。 */
    protected void registerHolidays(Set<String> days) {
        this.window.setLegalHolidays(days);
    }

    // ---------------------------------------------------------------- 回查

    @Override
    public THmfCycleTask selectTHmfCycleTaskById(Long id) {
        return this.hmfCycleTaskMapper.selectById(id);
    }

    @Override
    public List<THmfCycleTask> selectCycleTaskList() {
        return this.hmfCycleTaskMapper.selectList(new QueryWrapper<THmfCycleTask>()
                .eq("del_flag", 0).orderByAsc("due_at").orderByAsc("id"));
    }

    @Override
    public List<THmfCycleLog> listTaskLogs(Long taskId) {
        return this.hmfCycleLogMapper.selectList(new QueryWrapper<THmfCycleLog>()
                .eq("task_id", taskId).eq("del_flag", 0)
                .orderByAsc("round_at").orderByAsc("id"));
    }

    // ---------------------------------------------------------------- 查单：唯一一把尺

    /**
     * 本轮名单：未删、候办、止点（含提前期）已到的条目。只看到期没到期，
     * 不管出手窗口——夜里被压着的也在名单里，进本轮后按住。回写照同一名单走。
     */
    @Override
    public List<THmfCycleTask> listDue(Date at) {
        Date now = at == null ? nowTime() : at;
        List<THmfCycleTask> all = this.hmfCycleTaskMapper.selectList(new QueryWrapper<THmfCycleTask>()
                .eq("del_flag", 0)
                .orderByAsc("due_at").orderByAsc("id"));
        java.util.List<THmfCycleTask> due = new java.util.ArrayList<THmfCycleTask>();
        for (THmfCycleTask r : all) {
            if (r.getStatus() != null && r.getStatus() != STATUS_WAIT) {
                // 已办结、已催不动的都不再当“等着办”捞进下一轮。
                continue;
            }
            Date openAt = firstSpeakAt(r);
            if (openAt != null && !now.before(openAt)) {
                due.add(r);
            }
        }
        return due;
    }

    /** 提前几天开口那一格算出来的最早出手时刻；止点本身钉死不动。 */
    private Date firstSpeakAt(THmfCycleTask r) {
        Date due = r.getDueAt();
        if (due == null) {
            return null;
        }
        long leadMs = 0L;
        if (r.getAmount() != null && r.getAmount().signum() > 0) {
            // 用十进制整算，别让 double 把边界那一刻带偏。
            leadMs = r.getAmount().multiply(java.math.BigDecimal.valueOf(ONE_DAY_MS)).longValue();
        }
        return new Date(due.getTime() - leadMs);
    }

    // ---------------------------------------------------------------- 当轮算完再回写

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runOnce(Date at) {
        // 旧签名原样不动：跑一轮，回话本轮办结条数。完整三数走 sweepCycleTasks。
        return sweepCycleTasks(at).getDoneCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CycleSweep sweepCycleTasks(Date at) {
        Date now = at == null ? nowTime() : at;
        // 名单在本轮开头定死：下面回写全照这批，界面补不进一单、也拦不住少挑一单。
        List<THmfCycleTask> picked = listDue(now);
        if (picked.isEmpty()) {
            // 一条该动的都没挑着：正常收尾，不是失败告警。
            return CycleSweep.emptyRound(now);
        }
        CycleSweep sweep = new CycleSweep();
        sweep.setRoundAt(now);
        // 封存期同户同日去重：一天里任务要跑许多回，本轮回话之外还得照档里当天已落定的结果认。
        Set<String> sealedSites = new HashSet<>();
        for (THmfCycleTask task : picked) {
            try {
                processOne(task, now, sweep, sealedSites);
            } catch (RuntimeException ex) {
                // 一条绊住不掐掉整轮：条目原样候办，下一轮再算，不编进三数结局。
                String detail = "本条本轮处理出错已跳过，原样候办：" + ex.getMessage();
                touchRound(task, now);
                addLog(task, now, ACT_HELD, STATUS_WAIT, null, null, detail);
                sweep.addWait(task.getId());
            }
        }
        sweep.setMessage("本轮到期" + picked.size() + "条：等着办" + sweep.getWaitCount()
                + "、已办结" + sweep.getDoneCount() + "、催不动" + sweep.getFailCount());
        return sweep;
    }

    private void processOne(THmfCycleTask task, Date now, CycleSweep sweep, Set<String> sealedSites) {
        String siteNo = task.getSiteNo();

        // 封存期：同户同日只认头一笔落定的结果。先看档里今天该户是否已落定，
        // 再看本轮内存——一天跑多回，第二回也不许把同户第二条办进账。
        if (this.window.inYearEndSeal(now) && siteNo != null) {
            String site = siteNo.trim();
            boolean already = sealedSites.contains(site) || sealedLandedToday(site, now, task.getId());
            if (already) {
                addLog(task, now, ACT_SEAL_DUP, STATUS_WAIT, null, null,
                        "年终封存期同一户同日只认第一次落定的结果，本条后到拒收，前一条原样摆着");
                touchRound(task, now);
                sweep.addWait(task.getId());
                return;
            }
        }

        // 窗口关着（夜里/法定节假日；封存期整日开）：手里就算已到期也先压着。
        if (!this.window.openAt(now)) {
            String reason = this.window.closedReason(now);
            addLog(task, now, ACT_HELD, STATUS_WAIT, null, null, reason);
            touchRound(task, now);
            sweep.addWait(task.getId());
            return;
        }

        // 过了止点那一天还没办出去：撤回转催不动。留撤回笔可查，
        // 头回定的止点与（将来可能有的）完成刻都不被这笔盖掉。
        if (isOverdue(task, now)) {
            String detail = "已过止点日" + CycleWindow.dayKey(task.getDueAt()) + "仍未办出，撤回转催不动";
            addLog(task, now, ACT_WITHDRAW, STATUS_FAIL, null, null, detail);
            task.setStatus(STATUS_FAIL);
            task.setFailAt(now);
            task.setFailReason(detail);
            touchRound(task, now);
            this.hmfCycleTaskMapper.updateById(task);
            markSealedSite(task, now, sealedSites);
            sweep.addFail(task.getId());
            return;
        }

        // 还没开单就先撞墙：底册已迁走，或联系人栏一个季度没填。当场判催不动，缘由入行。
        THmfArch arch = archOf(siteNo);
        String wall = wallReason(arch, now);
        if (wall != null) {
            addLog(task, now, ACT_BLOCK, STATUS_FAIL, null, null, wall);
            task.setStatus(STATUS_FAIL);
            task.setFailAt(now);
            task.setFailReason(wall);
            touchRound(task, now);
            this.hmfCycleTaskMapper.updateById(task);
            markSealedSite(task, now, sealedSites);
            sweep.addFail(task.getId());
            return;
        }

        // 正经出手。
        if (task.getBizType() != null && task.getBizType() == BIZ_URGE) {
            dispatchUrge(task, arch, now, sweep, sealedSites);
        } else {
            finishInterest(task, now, sweep, sealedSites);
        }
    }

    /** 封存期内本户已有一回落定（办结/判催不动），本轮集合记一户，后面同户的拒收。 */
    private void markSealedSite(THmfCycleTask task, Date now, Set<String> sealedSites) {
        if (this.window.inYearEndSeal(now) && task.getSiteNo() != null) {
            sealedSites.add(task.getSiteNo().trim());
        }
    }

    /**
     * 档里今天该户是否已另有一条落定结果（办结/撞墙判死/逾期撤回）。
     * 一天跑多回，跨回次也只认头一笔；送达行、按住行不算落定。
     */
    private boolean sealedLandedToday(String site, Date now, Long selfTaskId) {
        String today = CycleWindow.dayKey(now);
        for (THmfCycleLog l : this.hmfCycleLogMapper.selectList(new QueryWrapper<THmfCycleLog>()
                .eq("del_flag", 0))) {
            if (l.getTaskId() != null && selfTaskId != null && selfTaskId.equals(l.getTaskId())) {
                continue;
            }
            int action = l.getAction() == null ? 0 : l.getAction();
            boolean terminal = action == ACT_DONE || action == ACT_BLOCK || action == ACT_WITHDRAW;
            if (terminal && site.equals(trimToNull(l.getSiteNo()))
                    && l.getLogTime() != null && today.equals(CycleWindow.dayKey(l.getLogTime()))) {
                return true;
            }
        }
        return false;
    }

    /** 结息出手：一笔办结（情形翻转与完成刻同脚，缺一笔不算完）。 */
    private void finishInterest(THmfCycleTask task, Date now, CycleSweep sweep, Set<String> sealedSites) {
        addLog(task, now, ACT_DONE, STATUS_DONE, null, null, "结息出手，办结");
        task.setStatus(STATUS_DONE);
        task.setFinishAt(now);
        touchRound(task, now);
        this.hmfCycleTaskMapper.updateById(task);
        markSealedSite(task, now, sealedSites);
        sweep.addDone(task.getId());
    }

    /**
     * 催交出手：站内、短信两路各送各记一笔，谁也顶不了谁。
     * 两路都有送达回执才算办完；任一路没送达，条目仍候办，下一轮再催，
     * 每轮两路各自再送、各自再记一笔，两条线永不并账。
     */
    private void dispatchUrge(THmfCycleTask task, THmfArch arch, Date now, CycleSweep sweep,
                              Set<String> sealedSites) {
        String text = urgeText(task);
        String mobile = arch == null ? null : trimToNull(arch.getContactMobile());

        boolean station = deliverStation(task, now, text);
        boolean sms = deliverSms(task, now, mobile, text);

        touchRound(task, now);
        if (station && sms) {
            addLog(task, now, ACT_DONE, STATUS_DONE, null, null,
                    "物业站内、业委会联系人手机两路均送达，办结");
            task.setStatus(STATUS_DONE);
            task.setFinishAt(now);
            this.hmfCycleTaskMapper.updateById(task);
            markSealedSite(task, now, sealedSites);
            sweep.addDone(task.getId());
        } else {
            this.hmfCycleTaskMapper.updateById(task);
            sweep.addWait(task.getId());
        }
    }

    private boolean deliverStation(THmfCycleTask task, Date now, String text) {
        boolean ack = false;
        String detail;
        if (this.noticeSender == null) {
            detail = "物业站内消息：送达口未接，按未送达记";
        } else {
            try {
                ack = this.noticeSender.sendStationMessage(task.getSiteNo(), text);
            } catch (RuntimeException ex) {
                ack = false;
            }
            detail = "物业站内消息：" + (ack ? "已送达" : "未送达");
        }
        addLog(task, now, ACT_SEND_STATION, null, CHANNEL_STATION,
                ack ? DELIVERED : NOT_DELIVERED, detail);
        return ack;
    }

    private boolean deliverSms(THmfCycleTask task, Date now, String mobile, String text) {
        boolean ack = false;
        String detail;
        if (mobile == null) {
            detail = "业委会联系人手机短信：联系人手机号缺失，按未送达记";
        } else if (this.noticeSender == null) {
            detail = "业委会联系人手机短信：送达口未接，按未送达记";
        } else {
            try {
                ack = this.noticeSender.sendSms(mobile, text);
            } catch (RuntimeException ex) {
                ack = false;
            }
            detail = "业委会联系人手机短信（" + mobile + "）：" + (ack ? "已送达" : "未送达");
        }
        addLog(task, now, ACT_SEND_SMS, null, CHANNEL_SMS,
                ack ? DELIVERED : NOT_DELIVERED, detail);
        return ack;
    }

    private String urgeText(THmfCycleTask task) {
        String tpl = trimToNull(task.getTplCode());
        String content = trimToNull(task.getContent());
        return "按事由文本[" + (tpl == null ? "默认" : tpl) + "]催交："
                + (content == null ? task.getItemNo() : content);
    }

    // ---------------------------------------------------------------- 撞墙认定

    private THmfArch archOf(String siteNo) {
        String site = trimToNull(siteNo);
        if (site == null) {
            return null;
        }
        List<THmfArch> rows = this.hmfArchMapper.selectList(new QueryWrapper<THmfArch>()
                .eq("site_no", site).eq("del_flag", 0));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** null = 不撞墙；否则跟着记进行里的缘由。 */
    private String wallReason(THmfArch arch, Date now) {
        if (arch == null) {
            return "该户分户底册查无在册记录（已从底册迁走），当场判催不动";
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            return "该户已从分户底册迁走，当场判催不动";
        }
        Date filled = arch.getContactFillAt();
        if (filled == null || filled.before(quarterAgo(now))) {
            return "业委会联系人栏一个季度都没填过（失联），当场判催不动";
        }
        return null;
    }

    /** 止点那一日过完才算逾期：只比 GMT+8 的日子，同日夜里仍只是压着，不撤回。 */
    private boolean isOverdue(THmfCycleTask task, Date now) {
        Date due = task.getDueAt();
        if (due == null) {
            return false;
        }
        return CycleWindow.dayKey(now).compareTo(CycleWindow.dayKey(due)) > 0;
    }

    private static Date quarterAgo(Date now) {
        Calendar c = Calendar.getInstance(CycleWindow.ZONE);
        c.setTime(now);
        c.add(Calendar.MONTH, -3);
        return c.getTime();
    }

    // ---------------------------------------------------------------- 点档：三数同源重数

    @Override
    public CycleSweep replayRound(Date roundAt) {
        Date round = roundAt;
        if (round == null) {
            round = latestRoundAt();
        }
        if (round == null) {
            return CycleSweep.emptyRound(null);
        }
        List<THmfCycleLog> rows = this.hmfCycleLogMapper.selectList(new QueryWrapper<THmfCycleLog>()
                .eq("round_at", round).eq("del_flag", 0)
                .orderByAsc("id"));
        // 一遍点完三个数：分两回去取即算错。结局行（1/2/3/4/5）进三桶，送达行（10/11）不进。
        CycleSweep sweep = new CycleSweep();
        sweep.setRoundAt(round);
        Map<Long, int[]> seat = new HashMap<>();
        for (THmfCycleLog l : rows) {
            int action = l.getAction() == null ? 0 : l.getAction();
            Long tid = l.getTaskId();
            if (action == ACT_DONE) {
                seatOnce(seat, tid, 1);
            } else if (action == ACT_BLOCK || action == ACT_WITHDRAW) {
                seatOnce(seat, tid, 2);
            } else if (action == ACT_HELD || action == ACT_SEAL_DUP) {
                seatOnce(seat, tid, 0);
            }
        }
        for (Map.Entry<Long, int[]> e : seat.entrySet()) {
            int bucket = e.getValue()[0];
            if (bucket == 1) {
                sweep.addDone(e.getKey());
            } else if (bucket == 2) {
                sweep.addFail(e.getKey());
            } else {
                sweep.addWait(e.getKey());
            }
        }
        sweep.setMessage("第" + CycleWindow.dayKey(round) + "轮：等着办" + sweep.getWaitCount()
                + "、已办结" + sweep.getDoneCount() + "、催不动" + sweep.getFailCount());
        return sweep;
    }

    /** 同一条目同轮多条结局行只坐一个桶：终局（办结/催不动）压过按住。 */
    private void seatOnce(Map<Long, int[]> seat, Long tid, int bucket) {
        int[] cur = seat.get(tid);
        if (cur == null) {
            seat.put(tid, new int[] {bucket});
        } else if (bucket > cur[0]) {
            cur[0] = bucket;
        }
    }

    @Override
    public Date latestRoundAt() {
        List<THmfCycleLog> rows = this.hmfCycleLogMapper.selectList(new QueryWrapper<THmfCycleLog>()
                .eq("del_flag", 0).orderByDesc("round_at").orderByDesc("id"));
        for (THmfCycleLog l : rows) {
            if (l.getRoundAt() != null) {
                return l.getRoundAt();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 私有帮手

    private void touchRound(THmfCycleTask task, Date now) {
        task.setLastRoundAt(now);
    }

    private void addLog(THmfCycleTask task, Date now, int action, Integer statusAfter,
                        Integer channel, Integer delivered, String detail) {
        THmfCycleLog l = new THmfCycleLog();
        l.setTaskId(task.getId());
        l.setSiteNo(task.getSiteNo());
        l.setBizType(task.getBizType());
        l.setRoundAt(now);
        l.setAction(action);
        l.setStatusAfter(statusAfter);
        l.setChannel(channel);
        l.setDelivered(delivered);
        l.setLogTime(now);
        l.setDetail(detail);
        l.setDelFlag(0);
        l.setCreateBy(OPERATOR);
        l.setCreateTime(now);
        this.hmfCycleLogMapper.insert(l);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
