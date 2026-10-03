package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * 口径（今年钉死）：
 * 可用名单只认——未删、现行(0)、at 落在启用之日（含）与交棒之日（不含）之间；
 * 没启用的不提早办，交了棒、停了用的不再被翻出来当可用的办。名单定序钉死
 * （让线顺位降序、代号降序、主键升序），第几条说话处处同一个序，
 * 重进重出回话一个样，不靠库回话先后的运气。
 * 单规则定位、可用判定、可用条数与名单同一把尺，不各算各的。
 * 折档走 {@link BandScale} 唯一一处：压着线（等于上限）计入上一层；
 * 缺线的条目不搅断整轮——缺的那根不卡，三线全缺判不出来回 0。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class THmfSpendLineServiceImpl implements ITHmfSpendLineService {

    /** 线的情形：现行 */
    private static final int STATUS_CURRENT = 0;

    /** 名单定序：让线顺位降序、代号降序、主键升序（回话先后不再靠运气）。 */
    private static final Comparator<THmfSpendLine> AVAILABLE_ORDER = new Comparator<THmfSpendLine>() {
        @Override
        public int compare(THmfSpendLine a, THmfSpendLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int c = cb.compareTo(ca);
            if (c != 0) {
                return c;
            }
            return Long.compare(idOf(a), idOf(b));
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
        List<THmfSpendLine> out = new ArrayList<>();
        if (at == null) {
            return out;
        }
        // 时段在内存里照 Date 本身比：JDBC 的 serverTimezone 与本机时区不对称，
        // 把时刻绑进 SQL 会整体偏移，边界（启用当日/交棒当日）会随机错档（实测踩到）；
        // 规则表行数有限，全量取出再滤，换边界判定处处一致。
        for (THmfSpendLine r : this.hmfSpendLineMapper.selectList(
                new QueryWrapper<THmfSpendLine>().eq("del_flag", 0))) {
            if (effectiveAt(r, at)) {
                out.add(r);
            }
        }
        out.sort(AVAILABLE_ORDER);
        return out;
    }

    /** 此刻这条线顶不顶用：现行、启用之日（含）已到、交棒之日（不含）未过。 */
    private boolean effectiveAt(THmfSpendLine r, Date at) {
        if (r.getStatus() == null || r.getStatus() != STATUS_CURRENT) {
            return false;
        }
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null
                || input.signum() < 0) {
            return 0;
        }
        THmfSpendLine rule = findRule(ruleCode.trim(), at);
        // 无生效版本：回 0，不许伸手摸空引用把整轮搅断。
        return rule == null ? 0 : bandOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null || input.signum() < 0) {
            return 0;
        }
        List<THmfSpendLine> avail = listAvailable(at);
        // 没有条目可处置：回 0，不许抛错；有则顺位最高那条说话。
        return avail.isEmpty() ? 0 : bandOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        THmfSpendLine r = this.hmfSpendLineMapper.selectById(id);
        // 与名单同一把尺：未删、现行、在窗。
        return r != null && r.getDelFlag() != null && r.getDelFlag() == 0 && effectiveAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /** 单规则定位：与名单同一把尺；同代号多版在窗时，启用之日晚的算数（并列主键大的算数）。 */
    private THmfSpendLine findRule(String ruleCode, Date at) {
        THmfSpendLine best = null;
        for (THmfSpendLine r : listAvailable(at)) {
            String code = r.getRuleCode() == null ? null : r.getRuleCode().trim();
            if (!ruleCode.equals(code)) {
                continue;
            }
            if (best == null || newer(r, best)) {
                best = r;
            }
        }
        return best;
    }

    /** a 是否比 b 更该说话：启用之日晚的算数；都没写启用之日或并列时主键大的算数（确定性）。 */
    private static boolean newer(THmfSpendLine a, THmfSpendLine b) {
        Date ea = a.getEffStart();
        Date eb = b.getEffStart();
        if (ea == null && eb == null) {
            return idOf(a) > idOf(b);
        }
        if (ea == null) {
            return false;
        }
        if (eb == null) {
            return true;
        }
        int c = ea.compareTo(eb);
        return c != 0 ? c > 0 : idOf(a) > idOf(b);
    }

    /** 折档唯一出处：{@link BandScale}，压着线计入上一层，缺线不搅断。 */
    private static int bandOf(THmfSpendLine rule, BigDecimal input) {
        return BandScale.bandOf(input, rule.getTh1Max(), rule.getTh2Max(), rule.getTh3Max());
    }

    private static long idOf(THmfSpendLine r) {
        return r.getId() == null ? 0L : r.getId();
    }
}
