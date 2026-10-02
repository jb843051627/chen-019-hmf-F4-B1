package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.THmfPayBook;
import com.fc.v2.model.auto.THmfPayRow;

/**
 * 一册流水进门核销一趟的唯一回话（回执）。
 *
 * 册面总行数、这趟核销（已销）行数、挂账行数三个数在服务层同一回统计里给出，
 * 册首与各行一次落库；销掉的加挂起的等于本册行数。页面自己点数那一回不算。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class PayBookReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 头一遍：整册收下，逐行判定已办完入库 */
    public static final int CODE_ACCEPTED = 1;
    /** 同号第二遍：只拿行比了库里已有的行，比完就收，去向不改、行不加不改 */
    public static final int CODE_COMPARED = 2;
    /** 行数超过中心定的上限：原册退回，一行不留 */
    public static final int CODE_REJECTED = 3;

    /** 回话情形码，见本类常量 */
    private int code;

    /** 册次号（进门定死的那个） */
    private String batchNo;

    /** 本册几行 */
    private int totalRows;
    /** 销掉几行 */
    private int clearedRows;
    /** 挂起几行 */
    private int heldRows;

    /** 核销结果名：核讫／空册（全册没有一行能核只许写空册） */
    private String resultText;

    /** 给柜员看的那句话 */
    private String message;

    /** 落定的册头（三数与逐行同源写出） */
    private THmfPayBook book;

    /** 各行判定（按册内行次排；挂起行也带着原来排第几） */
    private List<THmfPayRow> rows = new ArrayList<>();

    /** 同号第二遍比对时，对不上的行（逐行列明，不拿成数搪塞） */
    private List<PayRowDiff> diffs = new ArrayList<>();

    public PayBookReceipt() {
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
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

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public THmfPayBook getBook() {
        return book;
    }

    public void setBook(THmfPayBook book) {
        this.book = book;
    }

    public List<THmfPayRow> getRows() {
        return rows;
    }

    public void setRows(List<THmfPayRow> rows) {
        this.rows = rows;
    }

    public List<PayRowDiff> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<PayRowDiff> diffs) {
        this.diffs = diffs;
    }
}
