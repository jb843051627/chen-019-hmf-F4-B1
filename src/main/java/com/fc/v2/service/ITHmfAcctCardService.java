package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.THmfAcctCard;

import java.util.List;

/**
 * 分户账立户单 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITHmfAcctCardService {

    /** 按主键查询 */
    THmfAcctCard selectTHmfAcctCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<THmfAcctCard> selectTHmfAcctCardList(Wrapper<THmfAcctCard> queryWrapper);

    /** 新增 */
    int insertTHmfAcctCard(THmfAcctCard record);

    /** 修改 */
    int updateTHmfAcctCard(THmfAcctCard record);

    /** 批量删除 */
    int deleteTHmfAcctCardByIds(String ids);

    /** 按主键删除 */
    int deleteTHmfAcctCardById(Long id);
}
