package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.THmfPayBook;
import com.fc.v2.model.auto.THmfPayRow;

/**
 * 末了一笔对账的回话：册首那个数与逐行销出来的数对不对得上，由同一回计算给出。
 * 两边接不平，当场把差在哪几行列出来，不许拿一个成数搪塞。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class PayBookAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 对得上：册首三数与逐行同源，销+挂=本 */
    private boolean pass;

    /** 不通时指到差在哪的那句话；通时为咬合说明 */
    private String reason;

    /** 被核的册头 */
    private THmfPayBook book;

    /** 本册几行（同一回计算点出，不另读第二回） */
    private int totalRows;
    /** 销掉几行 */
    private int clearedRows;
    /** 挂起几行 */
    private int heldRows;

    /** 接不平时，差在的那几行（逐行列明） */
    private List<THmfPayRow> badRows = new ArrayList<>();

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public THmfPayBook getBook() {
        return book;
    }

    public void setBook(THmfPayBook book) {
        this.book = book;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getClearedRows() {
        return clearedRows;
    }

    public void setClearedRows(int clearedRows) {
        this.clearedRows = clearedRows;
    }

    public int getHeldRows() {
        return heldRows;
    }

    public void setHeldRows(int heldRows) {
        this.heldRows = heldRows;
    }

    public List<THmfPayRow> getBadRows() {
        return badRows;
    }

    public void setBadRows(List<THmfPayRow> badRows) {
        this.badRows = badRows;
    }
}
