package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.model.auto.THmfUseFlow;

/**
 * 推进入口的唯一回话：这张单归在第几段、下一段收不收它，由服务层这一口定。
 * 屏上带出的段号与此回话对不齐时，以此回话为准。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class UseFlowVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 回话情形：1收下并往前挪了一档；0门槛不过/余额不足，停在原档整张账不动；
     *  2同档第二遍，账上还是头一遍那句话，不另起一行；3作废封停，续不动。 */
    public static final int CODE_MOVED = 1;
    public static final int CODE_STAY = 0;
    public static final int CODE_DUP = 2;
    public static final int CODE_VOID = 3;

    /** 系统按已过痕迹重放数出来的段次（权威值） */
    private int stage;

    /** 回话情形码，见本类常量 */
    private int code;

    /** 给柜员看的那句话 */
    private String message;

    /** 最新单据（stage 为镜像，与本回话的权威段次同源写出） */
    private THmfUseFlow bill;

    public UseFlowVerdict() {
    }

    public UseFlowVerdict(int stage, int code, String message, THmfUseFlow bill) {
        this.stage = stage;
        this.code = code;
        this.message = message;
        this.bill = bill;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public THmfUseFlow getBill() {
        return bill;
    }

    public void setBill(THmfUseFlow bill) {
        this.bill = bill;
    }
}
