package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfUseFlowStageLogMapper;
import com.fc.v2.model.auto.THmfUseFlowStageLog;

/** 使用申请单过口痕迹内存假 Mapper。 */
class FakeStageLogMapper extends InMemoryMapper<THmfUseFlowStageLog> implements THmfUseFlowStageLogMapper {

    @Override
    Serializable idOf(THmfUseFlowStageLog t) {
        return t.getId();
    }

    @Override
    void assignId(THmfUseFlowStageLog t, long id) {
        t.setId(id);
    }
}
