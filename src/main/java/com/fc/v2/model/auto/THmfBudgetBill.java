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
 * 预算审定拨付核签单对象 t_hmf_budget_bill
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_hmf_budget_bill")
@ApiModel(value = "THmfBudgetBill", description = "预算审定拨付核签单")
public class THmfBudgetBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 预算审定拨付单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "预算审定拨付单号")
    private String billNo;

    /** 当前所在口次 0..2（业委会核/事务所核/中心核） */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在口次 0..2（业委会核/事务所核/中心核）")
    private Integer nodeNo;

    /** 本口签法镜像 0任一人 1两名点齐（算法按口次定死，此列只照当前口写出） */
    @TableField("sign_mode")
    @ApiModelProperty(value = "本口签法镜像 0任一人 1两名点齐（算法按口次定死，此列只照当前口写出）")
    private Integer signMode;

    /** 本口应落名数（当前口镜像，业委会2/事务所1/中心1） */
    @TableField("need_count")
    @ApiModelProperty(value = "本口应落名数（当前口镜像，业委会2/事务所1/中心1）")
    private Integer needCount;

    /** 本口已落名数（只按署名流水逐笔勾出，不以人手记数为准） */
    @TableField("sign_count")
    @ApiModelProperty(value = "本口已落名数（只按署名流水逐笔勾出，不以人手记数为准）")
    private Integer signCount;

    /** 当前口签认拨次（打回上一档时另起一拨，旧拨留存） */
    @TableField("cur_round")
    @ApiModelProperty(value = "当前口签认拨次（打回上一档时另起一拨，旧拨留存）")
    private Integer curRound;

    /** 报批情形 0在核 1已核讫 2已打回 */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已核讫 2已打回")
    private Integer status;

    /** 立单经手人（起单到归档不换，重录亦不改） */
    @TableField("maker")
    @ApiModelProperty(value = "立单经手人（起单到归档不换，重录亦不改）")
    private String maker;

    /** 拨付额（立单时一次取自预算审定额，库存与屏显同源，不许后手填改） */
    @TableField("pay_amt")
    @ApiModelProperty(value = "拨付额（立单时一次取自预算审定额，库存与屏显同源，不许后手填改）")
    private BigDecimal payAmt;

    /** 所属小区底册代号（同年另起新单，不并入旧单） */
    @TableField("site_no")
    @ApiModelProperty(value = "所属小区底册代号（同年另起新单，不并入旧单）")
    private String siteNo;

    /** 本单所属年度 */
    @TableField("bill_year")
    @ApiModelProperty(value = "本单所属年度")
    private Integer billYear;

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

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getCurRound() {
        return curRound;
    }

    public void setCurRound(Integer curRound) {
        this.curRound = curRound;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMaker() {
        return maker;
    }

    public void setMaker(String maker) {
        this.maker = maker;
    }

    public BigDecimal getPayAmt() {
        return payAmt;
    }

    public void setPayAmt(BigDecimal payAmt) {
        this.payAmt = payAmt;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getBillYear() {
        return billYear;
    }

    public void setBillYear(Integer billYear) {
        this.billYear = billYear;
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
