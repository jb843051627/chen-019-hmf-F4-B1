package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.custom.UseFlowAdvance;
import com.fc.v2.model.custom.UseFlowAudit;
import com.fc.v2.model.custom.UseFlowVerdict;
import com.fc.v2.service.ITHmfUseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 维修资金使用申请单 Controller（四档链：唯一推进入口）
 *
 * 这张单归在第几段、下一段收不收，只认服务层 {@code push} 一个入口的回话；
 * 界面不留第二个能直接改段落的把手，递上来的段号只当意图。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "维修资金使用申请单")
@Controller
@RequestMapping("/hmfUseFlow")
public class HmfUseFlowController extends BaseController {

    private final String prefix = "admin/hmfUseFlow";

    @Autowired
    private ITHmfUseFlowService hmfUseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("hmfUseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "维修资金使用申请单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("hmfUseFlow:list")
    @ResponseBody
    public ResultTable list(THmfUseFlow record) {
        QueryWrapper<THmfUseFlow> queryWrapper = new QueryWrapper<THmfUseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<THmfUseFlow> page =
                new com.github.pagehelper.PageInfo<THmfUseFlow>(hmfUseFlowService.selectTHmfUseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "维修资金使用申请单起单", action = "open")
    @ApiOperation(value = "起单进立项（四样齐才起得来）", notes = "起单进立项")
    @PostMapping("/open")
    @RequiresPermissions("hmfUseFlow:open")
    @ResponseBody
    public AjaxResult open(THmfUseFlow record) {
        THmfUseFlow r = hmfUseFlowService.openFlow(record.getBizNo(), record.getSiteNo(),
                record.getProjectPlace(), record.getBudgetAmt(), record.getBuilderName(),
                record.getApplySource(), null, record.getRemark());
        return r == null ? AjaxResult.error("立项四样不齐（或底册缺失），或该申请号下前一张还在办/已作废，起不了第二张")
                : AjaxResult.success("已起单进立项档").put("data", r);
    }

    @Log(title = "维修资金使用申请单推进", action = "push")
    @ApiOperation(value = "唯一推进入口（往前/往后都走这里，段次听服务层回话）", notes = "唯一推进入口")
    @PostMapping("/push")
    @RequiresPermissions("hmfUseFlow:push")
    @ResponseBody
    public AjaxResult push(UseFlowAdvance advance) {
        UseFlowVerdict verdict = hmfUseFlowService.pushUseFlow(advance);
        if (verdict == null) {
            return AjaxResult.error("单据不存在");
        }
        // 段次以回话为准：屏上带出的段号对不齐时，前端照 data.stage 与 code 回显。
        String msg = verdict.getMessage() + "（当前段：" + verdict.getStage() + "）";
        return verdict.getCode() == UseFlowVerdict.CODE_MOVED
                ? AjaxResult.success(200, msg, verdict.getBill())
                : AjaxResult.error(msg).put("data", verdict.getBill())
                    .put("stage", verdict.getStage()).put("verdictCode", verdict.getCode());
    }

    @Log(title = "维修资金使用申请单作废", action = "void")
    @ApiOperation(value = "作废（字改不得、张删不得）", notes = "作废封存")
    @PostMapping("/void")
    @RequiresPermissions("hmfUseFlow:void")
    @ResponseBody
    public AjaxResult voidFlow(Long id, String remark) {
        return toAjax(hmfUseFlowService.voidUseFlow(id, null, remark) != null ? 1 : 0);
    }

    @Log(title = "维修资金使用申请单倒查", action = "audit")
    @ApiOperation(value = "走完那趟的倒查账（从结算倒回立项）", notes = "倒查核验")
    @GetMapping("/audit")
    @RequiresPermissions("hmfUseFlow:audit")
    @ResponseBody
    public AjaxResult audit(Long id) {
        UseFlowAudit audit = hmfUseFlowService.auditUseFlow(id);
        return audit == null ? AjaxResult.error("单据不存在") : AjaxResult.successData(200, audit);
    }
}
