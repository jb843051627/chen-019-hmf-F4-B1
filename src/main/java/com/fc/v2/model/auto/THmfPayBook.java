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
 * 银行交存汇总册册头对象 t_hmf_pay_book
 *
 * 一份册一个册次号，进门就定死。册首三数（本册行数/已销行数/挂起行数）与逐行销出来的数
 * 出自服务层同一回统计：一趟核销判定办完才落册头与各行，页面自己点数不算数。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_hmf_pay_book")
@ApiModel(value = "THmfPayBook", description = "银行交存汇总册册头")
public class THmfPayBook implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 册次号（进门定死，一个月来几册各是各的，库里在册册次号唯一） */
    @TableField("batch_no")
    @ApiModelProperty(value = "册次号")
    private String batchNo;

    /** 本册行数（册首数，与逐行点出来的同源同回写出） */
    @TableField("total_rows")
    @ApiModelProperty(value = "本册行数")
    private Integer totalRows;

    /** 已销行数（册首数） */
    @TableField("cleared_rows")
    @ApiModelProperty(value = "已销行数")
    private Integer clearedRows;

    /** 挂起行数（册首数） */
    @TableField("held_rows")
    @ApiModelProperty(value = "挂起行数")
    private Integer heldRows;

    /** 核销结果 1核讫 2空册（全册没有一行能核只能写空册，核讫是报成绩的词） */
    @TableField("result")
    @ApiModelProperty(value = "核销结果 1核讫 2空册")
    private Integer result;

    /** 册情形 0在册 1已撤（要改先走撤册手续，同号第二遍只比对不得改） */
    @TableField("status")
    @ApiModelProperty(value = "册情形 0在册 1已撤")
    private Integer status;

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

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getClearedRows() {
        return clearedRows;
    }

    public void setClearedRows(Integer clearedRows) {
        this.clearedRows = clearedRows;
    }

    public Integer getHeldRows() {
        return heldRows;
    }

    public void setHeldRows(Integer heldRows) {
        this.heldRows = heldRows;
    }

    public Integer getResult() {
        return result;
    }

    public void setResult(Integer result) {
        this.result = result;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
