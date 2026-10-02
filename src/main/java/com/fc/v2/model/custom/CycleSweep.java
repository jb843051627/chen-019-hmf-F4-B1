package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 定时任务本轮扫描的回话：一轮只此一笔。
 *
 * 等着办/已办结/催不动三个数与三个 id 清单同一次算出：挑单、办、按住都在
 * 服务层这一轮里走完后一起回话，页面补不进也拦不住；分两回去取即算错。
 * 三个数都得能从轮次痕迹（t_hmf_cycle_log，round_at 取本轮）按 id 逐条点回来。
 *
 * 一条该动的都没挑着时 empty=true，回话“本轮没有到期条目”——这是正常收尾，
 * 不是任务出了岔子，不许当失败告警。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleSweep implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本轮扫描时刻：本轮所有痕迹行取同一值，页面三数照它点档 */
    private Date roundAt;

    /** 等着办：本轮够上日子但被窗口压着、或封存撞车被拒收，条目仍候办 */
    private int waitCount;
    /** 已办结：本轮落定办结的条数 */
    private int doneCount;
    /** 催不动：本轮撞墙判死或逾期撤回的条数 */
    private int failCount;

    private final List<Long> waitIds = new ArrayList<>();
    private final List<Long> doneIds = new ArrayList<>();
    private final List<Long> failIds = new ArrayList<>();

    /** 一条到期的都没挑着（区别于挑着了却全被压着） */
    private boolean empty;

    /** 给看屏人的回话 */
    private String message;

    public static CycleSweep emptyRound(Date roundAt) {
        CycleSweep s = new CycleSweep();
        s.roundAt = roundAt;
        s.empty = true;
        s.message = "本轮没有到期条目";
        return s;
    }

    public void addWait(Long id) {
        this.waitIds.add(id);
        this.waitCount = this.waitIds.size();
    }

    public void addDone(Long id) {
        this.doneIds.add(id);
        this.doneCount = this.doneIds.size();
    }

    public void addFail(Long id) {
        this.failIds.add(id);
        this.failCount = this.failIds.size();
    }

    public Date getRoundAt() {
        return roundAt;
    }

    public void setRoundAt(Date roundAt) {
        this.roundAt = roundAt;
    }

    public int getWaitCount() {
        return waitCount;
    }

    public void setWaitCount(int waitCount) {
        this.waitCount = waitCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(int doneCount) {
        this.doneCount = doneCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<Long> getWaitIds() {
        return waitIds;
    }

    public List<Long> getDoneIds() {
        return doneIds;
    }

    public List<Long> getFailIds() {
        return failIds;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
