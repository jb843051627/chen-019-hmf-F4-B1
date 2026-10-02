package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.THmfCycleLog;
import com.fc.v2.model.auto.THmfCycleTask;
import com.fc.v2.model.custom.CycleSweep;

/**
 * 结息催交单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITHmfCycleTaskService {

    /** 按主键回查条目 */
    THmfCycleTask selectTHmfCycleTaskById(Long id);

    /** 台账翻查：条目只翻不改（催不动的也翻得到）。 */
    List<THmfCycleTask> selectCycleTaskList();

    /** 某条目逐笔出手痕迹（撤回、两路送达都在此点得出来）。 */
    List<THmfCycleLog> listTaskLogs(Long taskId);

    /**
     * 本轮名单（查单这把尺）：候办、未删、止点（含提前期）已到的条目。
     * 名单只看到期没到期，与执行窗口无关——窗口压着的也在名单里，只是本轮按住。
     * 回写（sweepCycleTasks）挑的必须是同一批，两处共用这一把尺。
     */
    List<THmfCycleTask> listDue(Date at);

    /**
     * 执行一次（旧签名原样不动）：跑完本轮，回话本轮**已办结**条数。
     * 完整三数与逐条清单走 {@link #sweepCycleTasks(Date)}。
     */
    int runOnce(Date at);

    /**
     * 换的挑单写法：当轮算完再回写，一轮只此一笔回话。
     * 挑哪些单、办成或按住、逾期转催，全凭本方法本轮给的结果；界面补不进也拦不住。
     */
    CycleSweep sweepCycleTasks(Date at);

    /**
     * 照轮次痕迹从档里把某一轮的三数重数出来（点档与看屏同一次口径）。
     * roundAt 取最近一轮（无则 null，回话空轮）。
     */
    CycleSweep replayRound(Date roundAt);

    /** 档里最近一轮的扫描时刻（无痕迹回 null）。 */
    Date latestRoundAt();
}
