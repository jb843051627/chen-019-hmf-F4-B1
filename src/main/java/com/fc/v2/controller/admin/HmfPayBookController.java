package com.fc.v2.controller.admin;

import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.model.custom.PayBookSubmit;
import com.fc.v2.service.ITHmfPayRowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 银行交存汇总册核销 Controller（batch-process：整册进门，无单条增删改把手）
 *
 * 哪几行已销、哪几行挂起、哪几行退往期，全由服务层 reconcile 一趟判定完再入库；
 * 页面既塞不进多余一行，也抽不走任何一行。同号第二遍只比对，要改先走撤册手续。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "银行交存汇总册核销")
@Controller
@RequestMapping("/hmfPayBook")
public class HmfPayBookController extends BaseController {

    private final String prefix = "admin/hmfPayBook";

    @Autowired
    private ITHmfPayRowService hmfPayRowService;

    @ApiOperation(value = "册面台账跳转", notes = "册面台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("hmfPayBook:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "银行交存册核销", action = "reconcile")
    @ApiOperation(value = "一册流水进门核销一趟（整批判定后入库）", notes = "整册核销")
    @PostMapping("/reconcile")
    @RequiresPermissions("hmfPayBook:reconcile")
    @ResponseBody
    public AjaxResult reconcile(PayBookSubmit submit) {
        if (submit == null || submit.getRows() == null) {
            return AjaxResult.error("整册一起递，空册子也按册子走");
        }
        PayBookReceipt receipt = hmfPayRowService.reconcilePayBook(submit.getBatchNo(), submit.getRows());
        if (receipt == null) {
            return AjaxResult.error("册次号缺失或有行缺户号/金额/交存日期，整册不收（一行不留）");
        }
        if (receipt.getCode() == PayBookReceipt.CODE_REJECTED) {
            return AjaxResult.error(receipt.getMessage());
        }
        return AjaxResult.success(200, receipt.getMessage(), receipt);
    }

    @Log(title = "银行交存册末了对账", action = "audit")
    @ApiOperation(value = "末了一笔对账（三数同源，接不平列差行）", notes = "册首与逐行核对")
    @GetMapping("/audit")
    @RequiresPermissions("hmfPayBook:audit")
    @ResponseBody
    public AjaxResult audit(String batchNo) {
        PayBookAudit audit = hmfPayRowService.auditPayBook(batchNo);
        if (audit == null) {
            return AjaxResult.error("册次号为空，无从对账");
        }
        return AjaxResult.successData(200, audit);
    }

    @Log(title = "银行交存册往期挪层", action = "sweep")
    @ApiOperation(value = "挂起连跨两个对账季挪往期", notes = "往期清册")
    @PostMapping("/sweepArchived")
    @RequiresPermissions("hmfPayBook:sweep")
    @ResponseBody
    public AjaxResult sweepArchived() {
        int moved = hmfPayRowService.sweepHeldToArchived(new Date());
        return AjaxResult.success("本次挪往期" + moved + "行");
    }

    @Log(title = "银行交存册撤册", action = "withdraw")
    @ApiOperation(value = "撤册手续（要改先撤，撤后同号方可重进门）", notes = "撤册")
    @PostMapping("/withdraw")
    @RequiresPermissions("hmfPayBook:withdraw")
    @ResponseBody
    public AjaxResult withdraw(String batchNo, String reason) {
        return hmfPayRowService.withdrawPayBook(batchNo, null, reason) == null
                ? AjaxResult.error("查不到在册的这一册，撤不了")
                : AjaxResult.success("已撤册，同号可重新进门核销");
    }
}
