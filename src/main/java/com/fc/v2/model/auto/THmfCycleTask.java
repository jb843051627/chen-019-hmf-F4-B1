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
 * 结息催交单对象 t_hmf_cycle_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_hmf_cycle_task")
@ApiModel(value = "THmfCycleTask", description = "结息催交单")
public class THmfCycleTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 结息催交单号 */
    @TableField("item_no")
    @ApiModelProperty(value = "结息催交单号")
    private String itemNo;

    /** 对哪一户分户底册（按底册代号挂） */
    @TableField("site_no")
    @ApiModelProperty(value = "对哪一户分户底册")
    private String siteNo;

    /** 事由 1结息 2催交 */
    @TableField("biz_type")
    @ApiModelProperty(value = "事由 1结息 2催交")
    private Integer bizType;

    /** 应该出手那一天的止点时刻（钉死，不许为凑一批往后挪） */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "应该出手那一天的止点时刻")
    private Date dueAt;

    /** 可提前几日开口（提前期，天数） */
    @TableField("amount")
    @ApiModelProperty(value = "可提前几日开口")
    private BigDecimal amount;

    /** 按哪一份事由文本去办 */
    @TableField("tpl_code")
    @ApiModelProperty(value = "按哪一份事由文本去办")
    private String tplCode;

    /** 事由与被催分户底册记要 */
    @TableField("content")
    @ApiModelProperty(value = "事由与被催分户底册记要")
    private String content;

    /** 条目情形 0候办 1已办结 2催不动 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0候办 1已办结 2催不动")
    private Integer status;

    /** 完成那一刻（与情形翻转同脚落，缺一笔不算完） */
    @TableField("finish_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "完成那一刻")
    private Date finishAt;

    /** 判催不动那一刻（撞墙/逾期撤回） */
    @TableField("fail_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "判催不动那一刻")
    private Date failAt;

    /** 为什么催不动（迁出/失联/逾期撤回，跟着记进行里） */
    @TableField("fail_reason")
    @ApiModelProperty(value = "为什么催不动")
    private String failReason;

    /** 最近一次进轮次的扫描时刻 */
    @TableField("last_round_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近一次进轮次的扫描时刻")
    private Date lastRoundAt;

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

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
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

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTplCode() {
        return tplCode;
    }

    public void setTplCode(String tplCode) {
        this.tplCode = tplCode;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getFinishAt() {
        return finishAt;
    }

    public void setFinishAt(Date finishAt) {
        this.finishAt = finishAt;
    }

    public Date getFailAt() {
        return failAt;
    }

    public void setFailAt(Date failAt) {
        this.failAt = failAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Date getLastRoundAt() {
        return lastRoundAt;
    }

    public void setLastRoundAt(Date lastRoundAt) {
        this.lastRoundAt = lastRoundAt;
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
