package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfCycleLogMapper;
import com.fc.v2.model.auto.THmfCycleLog;

/** 结息催交单轮次出手痕迹内存假 Mapper。 */
class FakeCycleLogMapper extends InMemoryMapper<THmfCycleLog> implements THmfCycleLogMapper {

    @Override
    Serializable idOf(THmfCycleLog t) {
        return t.getId();
    }

    @Override
    void assignId(THmfCycleLog t, long id) {
        t.setId(id);
    }
}
