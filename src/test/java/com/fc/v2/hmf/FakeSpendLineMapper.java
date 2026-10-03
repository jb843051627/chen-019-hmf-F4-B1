package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfSpendLineMapper;
import com.fc.v2.model.auto.THmfSpendLine;

/** 支出审定额线内存假 Mapper。 */
class FakeSpendLineMapper extends InMemoryMapper<THmfSpendLine> implements THmfSpendLineMapper {

    @Override
    Serializable idOf(THmfSpendLine t) {
        return t.getId();
    }

    @Override
    void assignId(THmfSpendLine t, long id) {
        t.setId(id);
    }
}
