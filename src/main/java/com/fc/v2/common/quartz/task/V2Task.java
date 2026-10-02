package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.CycleSweep;
import com.fc.v2.service.ITHmfCycleTaskService;
import com.fc.v2.service.ITHmfUseFlowService;
import com.fc.v2.service.ITSysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 *测试类
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	@Autowired
    private ITSysUserService userService;

	@Autowired
	private ITHmfUseFlowService hmfUseFlowService;

	@Autowired
	private ITHmfCycleTaskService hmfCycleTaskService;

	/**
	 * 结息催交单照日子自走来路：当轮算完再回写，界面补不进也拦不住。
	 * 三数（等着办/已办结/催不动）同一次算出，没有到期条目是正常收尾，不告警。
	 * runOnce(Date) 旧签名原样不动，对外挂的挑单走 sweepCycleTasks 这一个新写法。
	 */
	public void sweepCycleTasks()
	{
		CycleSweep sweep = hmfCycleTaskService.sweepCycleTasks(null);
		System.out.println("结息催交本轮：" + sweep.getMessage() + "，执行时间:" + DateUtil.now());
	}

	/**
	 * 使用申请单公示天数自推来路：天数走完的单在这一脚上往前扫一档，
	 * 与柜员手点并到服务层同一个推进算法，谁先满足谁落，第二遍不另起一行。
	 */
	public void sweepUseFlowPublicity()
	{
		int moved = hmfUseFlowService.sweepDuePublicity(null);
		System.out.println("公示天数自推完成，扫动" + moved + "张，执行时间:" + DateUtil.now());
	}
    /**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }
}
