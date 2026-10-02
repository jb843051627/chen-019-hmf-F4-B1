package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.service.ITHmfAcctCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 分户账立户单 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "分户账立户单")
@Controller
@RequestMapping("/HmfAcctCardController")
public class HmfAcctCardController extends BaseController {

    private final String prefix = "admin/hmfAcctCard";

    @Autowired
    private ITHmfAcctCardService hmfAcctCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("hmf:hmfAcctCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "分户账立户单集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("hmf:hmfAcctCard:list")
    @ResponseBody
    public ResultTable list(THmfAcctCard record) {
        QueryWrapper<THmfAcctCard> queryWrapper = new QueryWrapper<THmfAcctCard>();
        startPage();
        com.github.pagehelper.PageInfo<THmfAcctCard> page =
                new com.github.pagehelper.PageInfo<THmfAcctCard>(hmfAcctCardService.selectTHmfAcctCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "分户账立户单新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("hmf:hmfAcctCard:add")
    @ResponseBody
    public AjaxResult add(THmfAcctCard record) {
        return toAjax(hmfAcctCardService.insertTHmfAcctCard(record));
    }

    @Log(title = "分户账立户单修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("hmf:hmfAcctCard:edit")
    @ResponseBody
    public AjaxResult editSave(THmfAcctCard record) {
        return toAjax(hmfAcctCardService.updateTHmfAcctCard(record));
    }

    @Log(title = "分户账立户单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("hmf:hmfAcctCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(hmfAcctCardService.deleteTHmfAcctCardByIds(ids));
    }
}
