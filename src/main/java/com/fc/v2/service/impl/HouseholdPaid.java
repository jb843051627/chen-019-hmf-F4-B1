package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfPayBookMapper;
import com.fc.v2.mapper.auto.THmfPayRowMapper;
import com.fc.v2.model.auto.THmfPayBook;
import com.fc.v2.model.auto.THmfPayRow;

/**
 * 户面已收的唯一算法——上一段（册子核销时户面累加的底数）与下一段（使用申请进结算时点户面余额）
 * 必须照同一处规矩数：只认已销账(1)、现行册面层(0)、未删的行；挂起行、挪往期的挂起行不算，
 * 已撤册的行随册作废（book_id 不在在册册头里）不算，旧批法没册头的行照旧算。
 *
 * 两处规矩合在这一处，谁再改口径只动这里，省得上下段各算各的、接不平。
 *
 * @author fuce
 * @date 2026-10-02
 */
final class HouseholdPaid {

    /** 行去向：已销账 */
    static final int ROW_CLEARED = 1;
    /** 所在层：现行册面 */
    static final int LAYER_CURRENT = 0;
    /** 册情形：在册 */
    static final int BOOK_ACTIVE = 0;

    private HouseholdPaid() {
    }

    /** 此刻在册册头 id 全在这（已撤的册不在内，其行随册作废）。 */
    static Set<Long> activeBookIds(THmfPayBookMapper bookMapper) {
        Set<Long> ids = new HashSet<>();
        if (bookMapper == null) {
            return ids;
        }
        for (THmfPayBook b : bookMapper.selectList(new QueryWrapper<THmfPayBook>()
                .eq("status", BOOK_ACTIVE).eq("del_flag", 0))) {
            ids.add(b.getId());
        }
        return ids;
    }

    /** 进户面的行：已销账、现行层、未删。是否随在册册再由调用方照 {@link #countsTowardHousehold} 裁。 */
    static List<THmfPayRow> clearedCurrentRows(THmfPayRowMapper rowMapper) {
        return rowMapper.selectList(new QueryWrapper<THmfPayRow>()
                .eq("status", ROW_CLEARED).eq("layer", LAYER_CURRENT).eq("del_flag", 0));
    }

    /** 有在册册头的行须册头在册；旧批法无册头（book_id 为空）的行照旧进户面。 */
    static boolean countsTowardHousehold(THmfPayRow r, Set<Long> activeBookIds) {
        return r.getBookId() == null || activeBookIds.contains(r.getBookId());
    }

    /** 按户号点户面已收合计（册子核销户面累加的底数）。 */
    static Map<String, BigDecimal> totalsByAccount(THmfPayRowMapper rowMapper,
                                                   THmfPayBookMapper bookMapper) {
        Set<Long> activeBookIds = activeBookIds(bookMapper);
        Map<String, BigDecimal> totals = new HashMap<>();
        for (THmfPayRow r : clearedCurrentRows(rowMapper)) {
            if (!countsTowardHousehold(r, activeBookIds)
                    || r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null) {
                continue;
            }
            totals.merge(r.getItemCode().trim(), PayRowAmounts.normalize(r.getQty()), BigDecimal::add);
        }
        return totals;
    }
}
