package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 使用申请单推进意图：前端递上来的材料与意向段次。
 * 意向段次只当意图看，认定只发生在服务层推进入口；材料不齐门槛不过。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class UseFlowAdvance implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 想推进的申请单 */
    private Long id;

    /** 动作：1往前（缺省）；-1往后（一次收一档）。动作只这两种，不靠段号猜方向 */
    private Integer action;

    /** 意向段次（前端屏上带出的段号，只作意图，不作凭据；与系统算出的段次不齐时听系统的） */
    private Integer expectStage;

    /** 实际经办人；不带则由入口按当前登录人续名 */
    private String operator;

    /** 门槛：工程地点（立项四样之一） */
    private String projectPlace;

    /** 门槛：预算金额（立项四样之一；也是结算列支划走的数） */
    private BigDecimal budgetAmt;

    /** 门槛：施工单位名称（立项四样之一） */
    private String builderName;

    /** 门槛：申请来源（立项四样之一，物业报的 / 业委会报的） */
    private String applySource;

    /** 门槛：当地定的公示天数（进公示档时落定，照此刻起算） */
    private Integer publicDays;

    /** 门槛：合同要件（合同编号等挂齐凭据；施工过门要看） */
    private String contractNo;

    /** 门槛：验收记载（结算过门要看） */
    private String acceptRecord;

    /** 门槛：监理那一名（结算过门要看） */
    private String supervisor;

    /** 记账时刻，缺省取当前时刻（测试可定点） */
    private Date now;

    /** 随笔记事 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public Integer getExpectStage() {
        return expectStage;
    }

    public void setExpectStage(Integer expectStage) {
        this.expectStage = expectStage;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getProjectPlace() {
        return projectPlace;
    }

    public void setProjectPlace(String projectPlace) {
        this.projectPlace = projectPlace;
    }

    public BigDecimal getBudgetAmt() {
        return budgetAmt;
    }

    public void setBudgetAmt(BigDecimal budgetAmt) {
        this.budgetAmt = budgetAmt;
    }

    public String getBuilderName() {
        return builderName;
    }

    public void setBuilderName(String builderName) {
        this.builderName = builderName;
    }

    public String getApplySource() {
        return applySource;
    }

    public void setApplySource(String applySource) {
        this.applySource = applySource;
    }

    public Integer getPublicDays() {
        return publicDays;
    }

    public void setPublicDays(Integer publicDays) {
        this.publicDays = publicDays;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public String getAcceptRecord() {
        return acceptRecord;
    }

    public void setAcceptRecord(String acceptRecord) {
        this.acceptRecord = acceptRecord;
    }

    public String getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(String supervisor) {
        this.supervisor = supervisor;
    }

    public Date getNow() {
        return now;
    }

    public void setNow(Date now) {
        this.now = now;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
