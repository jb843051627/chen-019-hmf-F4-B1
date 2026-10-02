package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.THmfPayRow;

/**
 * 一册流水进门的整批递交（batch-process 形状）：整册一起递，没有单条增删改的把手。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class PayBookSubmit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 册次号（进门定死） */
    private String batchNo;

    /** 整册各行（行次进册按先后自占，不由人手填） */
    private List<THmfPayRow> rows = new ArrayList<>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public List<THmfPayRow> getRows() {
        return rows;
    }

    public void setRows(List<THmfPayRow> rows) {
        this.rows = rows;
    }
}
