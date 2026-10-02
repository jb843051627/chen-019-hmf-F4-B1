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
import java.util.Date;

/**
 * 结息催交单轮次出手痕迹对象 t_hmf_cycle_log
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_hmf_cycle_log")
@ApiModel(value = "THmfCycleLog", description = "结息催交单轮次出手痕迹")
public class THmfCycleLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属结息催交单 */
    @TableField("task_id")
    @ApiModelProperty(value = "所属结息催交单")
    private Long taskId;

    /** 所属分户底册代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "所属分户底册代号")
    private String siteNo;

    /** 事由 1结息 2催交 */
    @TableField("biz_type")
    @ApiModelProperty(value = "事由 1结息 2催交")
    private Integer bizType;

    /** 本轮扫描时刻（同一次执行的行取同一值） */
    @TableField("round_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本轮扫描时刻")
    private Date roundAt;

    /** 本笔动作 1办结 2撞墙判催不动 3逾期撤回转催不动 4封存撞车拒收 5窗口压着候办 10站内送达 11短信送达 */
    @TableField("action")
    @ApiModelProperty(value = "本笔动作")
    private Integer action;

    /** 本笔落定后条目情形（结局行用） */
    @TableField("status_after")
    @ApiModelProperty(value = "本笔落定后条目情形")
    private Integer statusAfter;

    /** 送达路 1物业站内消息 2业委会联系人手机短信（仅送达行） */
    @TableField("channel")
    @ApiModelProperty(value = "送达路 1站内 2短信")
    private Integer channel;

    /** 该路是否送达 1送达 0未送达（仅送达行） */
    @TableField("delivered")
    @ApiModelProperty(value = "该路是否送达")
    private Integer delivered;

    /** 落笔时刻（即落库时刻） */
    @TableField("log_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔时刻")
    private Date logTime;

    /** 明细（撞墙缘由、撤回凭据、撞车说明等） */
    @TableField("detail")
    @ApiModelProperty(value = "明细")
    private String detail;

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getBizType() {
        return bizType;
    }

    public void setBizType(Integer bizType) {
        this.bizType = bizType;
    }

    public Date getRoundAt() {
        return roundAt;
    }

    public void setRoundAt(Date roundAt) {
        this.roundAt = roundAt;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public Integer getStatusAfter() {
        return statusAfter;
    }

    public void setStatusAfter(Integer statusAfter) {
        this.statusAfter = statusAfter;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public Integer getDelivered() {
        return delivered;
    }

    public void setDelivered(Integer delivered) {
        this.delivered = delivered;
    }

    public Date getLogTime() {
        return logTime;
    }

    public void setLogTime(Date logTime) {
        this.logTime = logTime;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
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
