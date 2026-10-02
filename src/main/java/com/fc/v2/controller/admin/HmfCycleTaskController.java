package com.fc.v2.controller.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THmfCycleTask;
import com.fc.v2.model.custom.CycleSweep;
import com.fc.v2.service.ITHmfCycleTaskService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;

/**
 * 结息催交单 Controller（只读台账：照日子由系统自己走，人只管看结果）。
 *
 * 这一面只有翻与看，没有改单的把手——挑哪些单、办成或按住、逾期转催，
 * 全凭服务层定时任务当轮算完再回写，界面补不进一单也拦不住少挑一单。
 * 三数从档里照最近一轮的痕迹重数，保证看屏那一回与点档那一回是同一次算的。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "结息催交单")
@Controller
@RequestMapping("/hmfCycleTask")
public class HmfCycleTaskController extends BaseController {

    @Autowired
    private ITHmfCycleTaskService hmfCycleTaskService;

    @Log(title = "结息催交单台账", action = "list")
    @ApiOperation(value = "台账（只翻不改，催不动的也翻得到）", notes = "结息催交单台账")
    @GetMapping("/list")
    @RequiresPermissions("hmfCycleTask:list")
    @ResponseBody
    public ResultTable list() {
        List<THmfCycleTask> rows = hmfCycleTaskService.selectCycleTaskList();
        return pageTable(rows, rows.size());
    }

    @ApiOperation(value = "本轮三数（从档里照最近一轮重数，看屏即点档）", notes = "本轮三数")
    @GetMapping("/round")
    @RequiresPermissions("hmfCycleTask:list")
    @ResponseBody
    public AjaxResult round() {
        // 不接受页面传来的时刻：以任务记的最近一轮为准，页面时刻与它对不齐时听任务的。
        CycleSweep sweep = hmfCycleTaskService.replayRound(null);
        return AjaxResult.successData(200, sweep);
    }

    @ApiOperation(value = "某条目逐笔出手痕迹（撤回、两路送达各一笔）", notes = "出手痕迹")
    @GetMapping("/logs/{id}")
    @ResponseBody
    public AjaxResult logs(@PathVariable("id") Long id) {
        return AjaxResult.successData(200, hmfCycleTaskService.listTaskLogs(id));
    }
}
