package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.List;

import com.fc.v2.model.auto.THmfBudgetBill;
import com.fc.v2.model.auto.THmfBudgetSign;

/**
 * 预算审定拨付核签单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITHmfBudgetBillService {

    /** 按主键回查单据 */
    THmfBudgetBill selectTHmfBudgetBillById(Long id);

    /**
     * 立单：立单经手人一次定死；拨付额取自预算审定额一次入库，屏显与库存同源；
     * 同一小区同一年度只许另起新单，不许并入旧单。立项被拒返回 null。
     */
    THmfBudgetBill openBill(String billNo, String maker, String siteNo, Integer billYear,
                            BigDecimal approvedPayAmt, String remark);

    /** 签认一票：返回更新后的单据；越档、封单后、同人本拨重复签等被拒返回 null */
    THmfBudgetBill approve(Long id, String approver, String comment);

    /** 当前一档打回：只挪回紧挨的上一档，旧名一个不抹；被拒返回 null */
    THmfBudgetBill reject(Long id, String approver, String comment);

    /** 退回上一环节：以当前登录操作人名义打回，其余同 reject；被拒返回 null */
    THmfBudgetBill rollback(Long id, String comment);

    /**
     * 复查名单：从中心倒着点回业委会，一道一道列全（含打回行与新旧各拨），
     * 每笔带口次、拨次、署名、时刻，与当日窗口签认名单逐笔咬合。
     */
    List<THmfBudgetSign> listSignTrail(Long id);
}
