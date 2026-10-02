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
 * 分户底册对象 t_hmf_arch
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_hmf_arch")
@ApiModel(value = "THmfArch", description = "分户底册")
public class THmfArch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 分户底册代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "分户底册代号")
    private String siteNo;

    /** 小区（项目）全称 */
    @TableField("site_name")
    @ApiModelProperty(value = "小区（项目）全称")
    private String siteName;

    /** 项目类别 */
    @TableField("site_type")
    @ApiModelProperty(value = "项目类别")
    private String siteType;

    /** 落在哪一级辖区(市—区—街道) */
    @TableField("road_name")
    @ApiModelProperty(value = "落在哪一级辖区(市—区—街道)")
    private String roadName;

    /** 户建面低界(平方米) */
    @TableField("th1_max")
    @ApiModelProperty(value = "户建面低界(平方米)")
    private BigDecimal th1Max;

    /** 中界(平方米) */
    @TableField("th2_max")
    @ApiModelProperty(value = "中界(平方米)")
    private BigDecimal th2Max;

    /** 高界(平方米) */
    @TableField("th3_max")
    @ApiModelProperty(value = "高界(平方米)")
    private BigDecimal th3Max;

    /** 底册情形 0在册 1已迁出 */
    @TableField("status")
    @ApiModelProperty(value = "底册情形 0在册 1已迁出")
    private Integer status;

    /** 业委会联系人手机号（催交走短信那一路） */
    @TableField("contact_mobile")
    @ApiModelProperty(value = "业委会联系人手机号")
    private String contactMobile;

    /** 联系人栏最近填报时刻（一个季度没填过即当失联） */
    @TableField("contact_fill_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "联系人栏最近填报时刻")
    private Date contactFillAt;

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

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteType() {
        return siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public BigDecimal getTh1Max() {
        return th1Max;
    }

    public void setTh1Max(BigDecimal th1Max) {
        this.th1Max = th1Max;
    }

    public BigDecimal getTh2Max() {
        return th2Max;
    }

    public void setTh2Max(BigDecimal th2Max) {
        this.th2Max = th2Max;
    }

    public BigDecimal getTh3Max() {
        return th3Max;
    }

    public void setTh3Max(BigDecimal th3Max) {
        this.th3Max = th3Max;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContactMobile() {
        return contactMobile;
    }

    public void setContactMobile(String contactMobile) {
        this.contactMobile = contactMobile;
    }

    public Date getContactFillAt() {
        return contactFillAt;
    }

    public void setContactFillAt(Date contactFillAt) {
        this.contactFillAt = contactFillAt;
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
