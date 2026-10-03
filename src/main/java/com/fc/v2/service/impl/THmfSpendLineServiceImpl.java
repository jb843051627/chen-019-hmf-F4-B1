package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfSpendLineMapper;
import com.fc.v2.model.auto.THmfSpendLine;
import com.fc.v2.service.ITHmfSpendLineService;

/**
 * 支出审定额线 Service业务层处理（rule-eval 形状）
 *
 * “某时刻可用”只有一处口径（见 {@link #availableRows(Date)}）：未删、现行（status=0）、
 * 已到启用之日（eff_start 含当日）且未到交棒之日（eff_end 不含当日）；启用/交棒日为空
 * 分别当作“早就启用”“永不交棒”。列表、计数、单条可用性、单规则定位全照这一处数，
 * 不许各写各的。可用线统一按优先级降序、ruleCode 降序、启用之日降序、主键降序排定，
 * evaluateTop 与同号多版定位都取排完序的头一条。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class THmfSpendLineServiceImpl implements ITHmfSpendLineService {

    /** 线的情形：现行 */
    private static final int STATUS_ACTIVE = 0;

    /** 优先级降序；并列按 ruleCode 降序，再并列按启用之日降序、主键降序（确定性） */
    private static final Comparator<THmfSpendLine> AVAILABLE_ORDER = new Comparator<THmfSpendLine>() {
        @Override
        public int compare(THmfSpendLine a, THmfSpendLine b) {
            int byPriority = cmpInt(b.getPriority(), a.getPriority());
            if (byPriority != 0) {
                return byPriority;
            }
            int byCode = cmpStr(b.getRuleCode(), a.getRuleCode());
            if (byCode != 0) {
                return byCode;
            }
            int byStart = cmpDate(b.getEffStart(), a.getEffStart());
            if (byStart != 0) {
                return byStart;
            }
            return cmpLong(b.getId(), a.getId());
        }
    };

    @javax.annotation.Resource
    private THmfSpendLineMapper hmfSpendLineMapper;

    @Override
    public THmfSpendLine selectTHmfSpendLineById(Long id) {
        return this.hmfSpendLineMapper.selectById(id);
    }

    @Override
    public List<THmfSpendLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<THmfSpendLine>();
        }
        return availableRows(at);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        THmfSpendLine rule = findRule(ruleCode, at);
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<THmfSpendLine> avail = availableRows(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        THmfSpendLine r = this.hmfSpendLineMapper.selectById(id);
        // 与列表/定位同处一把尺：删掉的、停用的、没到启用日的、已交棒的都不算可用。
        return r != null && isAvailable(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return availableRows(at).size();
    }

    /**
     * 唯一一处“某时刻可用”口径：未删、现行、已启用（含当日）、未交棒（交棒日不含）。
     * 一次取齐照统一次序排定，列表/计数/定位/单条可用性都从这一份往下裁。
     */
    private List<THmfSpendLine> availableRows(Date at) {
        List<THmfSpendLine> rows = this.hmfSpendLineMapper.selectList(new QueryWrapper<THmfSpendLine>()
                .eq("del_flag", 0));
        java.util.List<THmfSpendLine> avail = new java.util.ArrayList<THmfSpendLine>();
        for (THmfSpendLine r : rows) {
            if (isAvailable(r, at)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, AVAILABLE_ORDER);
        return avail;
    }

    /** 单条可用性判定：与 {@link #availableRows(Date)} 同一把尺。 */
    private boolean isAvailable(THmfSpendLine r, Date at) {
        if (r == null || r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_ACTIVE) {
            return false;
        }
        // 启用之日含当日：恰好落在启用那一刻便可用。
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        // 交棒之日不含：到交棒那一刻这条线已不说话。
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    /** 同号多版：在 at 时刻可用的版本里取统一次序的头一条（优先级高者、再新启用者）。 */
    private THmfSpendLine findRule(String ruleCode, Date at) {
        for (THmfSpendLine r : availableRows(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                return r;
            }
        }
        return null;
    }

    /**
     * 分档：等于上限取高一档。阈值没维护到的档不参与，三档阈值皆缺返回 0（调用方按不可用处理）。
     */
    private int levelOf(THmfSpendLine rule, BigDecimal input) {
        if (rule == null) {
            return 0;
        }
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        if (rule.getTh1Max() == null && rule.getTh2Max() == null && rule.getTh3Max() == null) {
            return 0;
        }
        return 4;
    }

    private static int cmpInt(Integer a, Integer b) {
        return (a == null ? 0 : a.intValue()) - (b == null ? 0 : b.intValue());
    }

    private static int cmpLong(Long a, Long b) {
        return Long.compare(a == null ? 0L : a.longValue(), b == null ? 0L : b.longValue());
    }

    private static int cmpStr(String a, String b) {
        return (a == null ? "" : a).compareTo(b == null ? "" : b);
    }

    private static int cmpDate(Date a, Date b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }
}
