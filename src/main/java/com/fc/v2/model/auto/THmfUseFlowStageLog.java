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
 * 维修资金使用申请单过口痕迹对象 t_hmf_use_stage_log
 * 一次过口一行，只添不删不抹；退回收档时旧行原样沉底，重走以 round_no 另起一拨。
 * 当前在第几段、这一趟走没走通，都只照本表逐笔重放得出，不照单面上的段次字。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_hmf_use_stage_log")
@ApiModel(value = "THmfUseFlowStageLog", description = "维修资金使用申请单过口痕迹")
public class THmfUseFlowStageLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属使用申请单 */
    @TableField("flow_id")
    @ApiModelProperty(value = "所属使用申请单")
    private Long flowId;

    /** 落在哪一档 0立项 1公示 2施工 3结算（过口行记的是进入的那一档；退回行记的是从哪档退） */
    @TableField("stage_no")
    @ApiModelProperty(value = "落在哪一档 0立项 1公示 2施工 3结算")
    private Integer stageNo;

    /** 该档第几拨（退回重走添新拨，旧拨沉底留存） */
    @TableField("round_no")
    @ApiModelProperty(value = "该档第几拨（退回重走添新拨，旧拨沉底留存）")
    private Integer roundNo;

    /** 本行动作 0过口（往前进入紧邻下一档）1退回（收回一档）2列支（结算过门时划款）3作废 */
    @TableField("action")
    @ApiModelProperty(value = "本笔动作 0过口 1退回 2列支 3作废")
    private Integer action;

    /** 实际经办（操作）人，逐笔实记，不许笼统记成固定一人 */
    @TableField("operator")
    @ApiModelProperty(value = "实际经办（操作）人")
    private String operator;

    /** 本行落笔时刻（也是公示天数照算、事后补录照查的唯一凭据） */
    @TableField("log_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本行落笔时刻")
    private Date logTime;

    /** 列支动作划走的金额（仅 action=2 有值） */
    @TableField("pay_amt")
    @ApiModelProperty(value = "列支划走金额（仅列支行有值）")
    private BigDecimal payAmt;

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

    /** 备注（门槛材料、退回事由等随笔记事） */
    @TableField("remark")
    @ApiModelProperty(value = "备注（门槛材料、退回事由等）")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Integer getStageNo() {
        return stageNo;
    }

    public void setStageNo(Integer stageNo) {
        this.stageNo = stageNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Date getLogTime() {
        return logTime;
    }

    public void setLogTime(Date logTime) {
        this.logTime = logTime;
    }

    public BigDecimal getPayAmt() {
        return payAmt;
    }

    public void setPayAmt(BigDecimal payAmt) {
        this.payAmt = payAmt;
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
