package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfArchMapper;
import com.fc.v2.model.auto.THmfArch;

/** 分户底册内存假 Mapper。 */
class FakeArchMapper extends InMemoryMapper<THmfArch> implements THmfArchMapper {

    @Override
    Serializable idOf(THmfArch t) {
        return t.getId();
    }

    @Override
    void assignId(THmfArch t, long id) {
        t.setId(id);
    }
}
