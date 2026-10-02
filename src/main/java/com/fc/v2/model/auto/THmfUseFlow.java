package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 维修资金使用申请单对象 t_hmf_use_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_hmf_use_flow")
@ApiModel(value = "THmfUseFlow", description = "维修资金使用申请单")
public class THmfUseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 使用申请单号 */
    @TableField("biz_no")
    @ApiModelProperty(value = "使用申请单号")
    private String bizNo;

    /** 所属小区底册代号（户面余额按底册点） */
    @TableField("site_no")
    @ApiModelProperty(value = "所属小区底册代号")
    private String siteNo;

    /** 当前段次 0..3（立项/公示/施工/结算）；只作镜像，权威段次由系统按过口痕迹重放得出 */
    @TableField("stage")
    @ApiModelProperty(value = "当前段次 0..3（立项/公示/施工/结算）")
    private Integer stage;

    /** 申领会落 0未起 1在办 2已作废 3已办结（走到结算档列讫即办结一趟） */
    @TableField("status")
    @ApiModelProperty(value = "申领会落 0未起 1在办 2已作废 3已办结")
    private Integer status;

    /** 当前拨次（退回上一档时旧拨沉底，重走另起一拨） */
    @TableField("cur_round")
    @ApiModelProperty(value = "当前拨次（退回重走添新拨，旧拨沉底）")
    private Integer curRound;

    /** 门槛·工程地点（立项四样之一，缺则立项起不来） */
    @TableField("project_place")
    @ApiModelProperty(value = "门槛·工程地点（立项四样之一）")
    private String projectPlace;

    /** 门槛·预算金额（立项四样之一；结算列支照此数划走） */
    @TableField("budget_amt")
    @ApiModelProperty(value = "门槛·预算金额（立项四样之一；列支照此数划走）")
    private BigDecimal budgetAmt;

    /** 门槛·施工单位名称（立项四样之一） */
    @TableField("builder_name")
    @ApiModelProperty(value = "门槛·施工单位名称（立项四样之一）")
    private String builderName;

    /** 门槛·申请来源（立项四样之一：物业报的 / 业委会报的） */
    @TableField("apply_source")
    @ApiModelProperty(value = "门槛·申请来源（立项四样之一）")
    private String applySource;

    /** 门槛·当地定的公示天数（进公示档落定，照此刻起算） */
    @TableField("public_days")
    @ApiModelProperty(value = "门槛·公示天数")
    private Integer publicDays;

    /** 公示起算时刻（本拨进公示档落笔时刻，重走重新起算） */
    @TableField("public_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "公示起算时刻")
    private Date publicStart;

    /** 门槛·合同要件（施工档挂齐，结算门才认） */
    @TableField("contract_no")
    @ApiModelProperty(value = "门槛·合同要件")
    private String contractNo;

    /** 门槛·验收记载（结算档要看） */
    @TableField("accept_record")
    @ApiModelProperty(value = "门槛·验收记载")
    private String acceptRecord;

    /** 门槛·监理那一名（结算档要到） */
    @TableField("supervisor")
    @ApiModelProperty(value = "门槛·监理签名")
    private String supervisor;

    /** 已列支金额（结算过门列讫落地，半途不动） */
    @TableField("pay_amt")
    @ApiModelProperty(value = "已列支金额（结算过门划走）")
    private BigDecimal payAmt;

    /** 列支落地时刻 */
    @TableField("pay_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "列支落地时刻")
    private Date payTime;

    /** 经办记事 */
    @TableField("content")
    @ApiModelProperty(value = "经办记事")
    private String content;

    /** 最近一次过口动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次过口动作")
    private String lastAction;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCurRound() {
        return curRound;
    }

    public void setCurRound(Integer curRound) {
        this.curRound = curRound;
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

    public Date getPublicStart() {
        return publicStart;
    }

    public void setPublicStart(Date publicStart) {
        this.publicStart = publicStart;
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

    public BigDecimal getPayAmt() {
        return payAmt;
    }

    public void setPayAmt(BigDecimal payAmt) {
        this.payAmt = payAmt;
    }

    public Date getPayTime() {
        return payTime;
    }

    public void setPayTime(Date payTime) {
        this.payTime = payTime;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
