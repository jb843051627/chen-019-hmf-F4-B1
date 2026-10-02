package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.custom.UseFlowAdvance;
import com.fc.v2.model.custom.UseFlowAudit;
import com.fc.v2.model.custom.UseFlowVerdict;

/**
 * 维修资金使用申请单 Service接口（四档链：待发起→立项→公示→施工→结算）
 *
 * 段次认定只发生在 {@link #pushUseFlow(UseFlowAdvance)} 这一个推进入口里；
 * 前端递上来的段号只当意图看待。旧的读段次方法签名与给回一概不动。
 *
 * @author fuce
 * @date 2026-09-30
 */
public interface ITHmfUseFlowService {

    /** 按主键查询单据（旧读法，参数与给回不动；单上 stage 仅镜像，权威段次听推进入口回话） */
    THmfUseFlow selectTHmfUseFlowById(Long id);

    /** 列表查询（流转台账，旧读法不动） */
    List<THmfUseFlow> selectTHmfUseFlowList(QueryWrapper<THmfUseFlow> queryWrapper);

    /**
     * 起单进立项档：工程地点、预算金额、施工单位名称、申请来源四样齐才起得来；
     * 同一申请单号下前一张还在办或已中止（作废）时，起不了第二张。被拒返回 null。
     *
     * @param siteNo 所属小区底册代号（户面余额按底册点：已销账交存合计减同底册已列支）
     */
    THmfUseFlow openFlow(String bizNo, String siteNo, String projectPlace,
                         java.math.BigDecimal budgetAmt, String builderName, String applySource,
                         String operator, String remark);

    /**
     * 唯一推进入口（往前/往后都走这里）：
     * 段次由系统按已过口痕迹重放数出来，屏上带出的段号只当意图；回话以本入口为准。
     * 往前只进紧邻下一档且门槛要齐；往后一次收一档，旧记沉底、新拨重攒。
     * 作废续不动、同档第二遍不另起一行、余额不足整张账不动，都由本入口回话。
     */
    UseFlowVerdict pushUseFlow(UseFlowAdvance advance);

    /** 公示天数自己走完的单，由定时来路在同一推进算法上往前扫一档（只扫公示档）。返回扫动的张数。 */
    int sweepDuePublicity(java.util.Date now);

    /** 作废：字改不得、张删不得，只盖作废印并留痕；被拒返回 null。 */
    THmfUseFlow voidUseFlow(Long id, String operator, String remark);

    /**
     * 走完那趟另有一笔账：从结算倒回立项一档档捋。
     * 多出一条记载、缺一档痕迹、事后补进去的、列支对不上，看到任一处即判这趟没走通。
     * 核验照现行痕迹的样式回话，不另立新张。
     */
    UseFlowAudit auditUseFlow(Long id);
}
