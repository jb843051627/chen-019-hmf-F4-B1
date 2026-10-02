package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfBudgetSignMapper;
import com.fc.v2.model.auto.THmfBudgetSign;

/** 署名流水内存假 Mapper。 */
class FakeSignMapper extends InMemoryMapper<THmfBudgetSign> implements THmfBudgetSignMapper {

    @Override
    Serializable idOf(THmfBudgetSign t) {
        return t.getId();
    }

    @Override
    void assignId(THmfBudgetSign t, long id) {
        t.setId(id);
    }
}
