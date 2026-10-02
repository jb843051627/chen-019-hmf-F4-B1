package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.THmfUseFlowStageLog;

/**
 * 一趟链倒查核验的回话（照现行过口痕迹样式回话，不另立新账张）。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class UseFlowAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 这趟走没走通：true 四档与列支处处咬合；false 见任一处不合即不通 */
    private boolean pass;

    /** 不通时指到第一处不合的那句话；通时为核验咬合说明 */
    private String reason;

    /** 从结算倒回立项捋出来的现行有效痕迹（倒序） */
    private List<THmfUseFlowStageLog> trail = new ArrayList<>();

    /** 不合规的那几行（多记载/缺档/事后补录/列支对不上） */
    private List<THmfUseFlowStageLog> badRows = new ArrayList<>();

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

    public List<THmfUseFlowStageLog> getTrail() {
        return trail;
    }

    public void setTrail(List<THmfUseFlowStageLog> trail) {
        this.trail = trail;
    }

    public List<THmfUseFlowStageLog> getBadRows() {
        return badRows;
    }

    public void setBadRows(List<THmfUseFlowStageLog> badRows) {
        this.badRows = badRows;
    }
}
