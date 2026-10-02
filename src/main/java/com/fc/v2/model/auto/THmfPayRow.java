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
 * 银行交存汇总册核销行对象 t_hmf_pay_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_hmf_pay_row")
@ApiModel(value = "THmfPayRow", description = "银行交存汇总册核销行")
public class THmfPayRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 银行交存汇总册次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "银行交存汇总册次号")
    private String batchNo;

    /** 所属在册册头（同号撤册后重进门另起册头，新旧各行以册头分清，不混） */
    @TableField("book_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所属在册册头")
    private Long bookId;

    /** 原册行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原册行次")
    private Integer rowNo;

    /** 被对上的分户账户号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被对上的分户账户号")
    private String itemCode;

    /** 本行到帐金额 */
    @TableField("qty")
    @ApiModelProperty(value = "本行到帐金额")
    private BigDecimal qty;

    /** 交存日期（落在哪一对账季就算哪一季；月底最后一天含当日算本期） */
    @TableField("pay_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "交存日期")
    private Date payDate;

    /** 所属对账季（交存日期落在哪季就算哪季，季内自始至终不改） */
    @TableField("from_season")
    @ApiModelProperty(value = "所属对账季")
    private String fromSeason;

    /** 行落地情形 0待销 1已销账 2挂起 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0待销 1已销账 2挂起")
    private Integer status;

    /** 所在层 0现行册面 1往期（挂起连跨两个对账季无去向则挪此层，只供翻查） */
    @TableField("layer")
    @ApiModelProperty(value = "所在层 0现行 1往期")
    private Integer layer;

    /** 挂起缘由（哪一户累到超过应缴、超在何处，就地写明） */
    @TableField("hold_reason")
    @ApiModelProperty(value = "挂起缘由")
    private String holdReason;

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

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public Date getPayDate() {
        return payDate;
    }

    public void setPayDate(Date payDate) {
        this.payDate = payDate;
    }

    public String getFromSeason() {
        return fromSeason;
    }

    public void setFromSeason(String fromSeason) {
        this.fromSeason = fromSeason;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getLayer() {
        return layer;
    }

    public void setLayer(Integer layer) {
        this.layer = layer;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public void setHoldReason(String holdReason) {
        this.holdReason = holdReason;
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
