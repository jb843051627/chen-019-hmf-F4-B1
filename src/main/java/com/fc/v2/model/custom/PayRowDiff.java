package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fc.v2.model.auto.THmfPayRow;

/**
 * 同号第二遍比对时，对不上的那一行（册内行次定位，不拿成数搪塞）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class PayRowDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 册里有、库里没有（第二遍多出来的行） */
    public static final int KIND_ADDED = 1;
    /** 库里有、册里没有（第二遍缺了的行） */
    public static final int KIND_MISSING = 2;
    /** 同一册内行次，户号/金额/交存日期对不上 */
    public static final int KIND_CHANGED = 3;

    /** 册内行次（挂起行翻出来也带原来排第几） */
    private Integer rowNo;

    /** 差异种类，见本类常量 */
    private int kind;

    /** 第二遍递进来的户号 */
    private String inItemCode;
    /** 第二遍递进来的金额 */
    private BigDecimal inQty;
    /** 第二遍递进来的交存日期 */
    private Date inPayDate;

    /** 头一遍库里存着的户号 */
    private String storeItemCode;
    /** 头一遍库里存着的金额 */
    private BigDecimal storeQty;
    /** 头一遍库里存着的交存日期 */
    private Date storePayDate;

    /** 指到这一行的那句话 */
    private String detail;

    public PayRowDiff() {
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public int getKind() {
        return kind;
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public String getInItemCode() {
        return inItemCode;
    }

    public void setInItemCode(String inItemCode) {
        this.inItemCode = inItemCode;
    }

    public BigDecimal getInQty() {
        return inQty;
    }

    public void setInQty(BigDecimal inQty) {
        this.inQty = inQty;
    }

    public Date getInPayDate() {
        return inPayDate;
    }

    public void setInPayDate(Date inPayDate) {
        this.inPayDate = inPayDate;
    }

    public String getStoreItemCode() {
        return storeItemCode;
    }

    public void setStoreItemCode(String storeItemCode) {
        this.storeItemCode = storeItemCode;
    }

    public BigDecimal getStoreQty() {
        return storeQty;
    }

    public void setStoreQty(BigDecimal storeQty) {
        this.storeQty = storeQty;
    }

    public Date getStorePayDate() {
        return storePayDate;
    }

    public void setStorePayDate(Date storePayDate) {
        this.storePayDate = storePayDate;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
