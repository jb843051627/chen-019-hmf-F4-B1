package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfCycleTaskMapper;
import com.fc.v2.model.auto.THmfCycleTask;

/** 结息催交单内存假 Mapper。 */
class FakeCycleTaskMapper extends InMemoryMapper<THmfCycleTask> implements THmfCycleTaskMapper {

    @Override
    Serializable idOf(THmfCycleTask t) {
        return t.getId();
    }

    @Override
    void assignId(THmfCycleTask t, long id) {
        t.setId(id);
    }
}
