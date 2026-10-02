package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfUseFlowMapper;
import com.fc.v2.model.auto.THmfUseFlow;

/** 使用申请单内存假 Mapper。 */
class FakeUseFlowMapper extends InMemoryMapper<THmfUseFlow> implements THmfUseFlowMapper {

    @Override
    Serializable idOf(THmfUseFlow t) {
        return t.getId();
    }

    @Override
    void assignId(THmfUseFlow t, long id) {
        t.setId(id);
    }
}
