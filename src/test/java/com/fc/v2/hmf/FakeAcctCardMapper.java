package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfAcctCardMapper;
import com.fc.v2.model.auto.THmfAcctCard;

/** 分户账立户单内存假 Mapper。 */
class FakeAcctCardMapper extends InMemoryMapper<THmfAcctCard> implements THmfAcctCardMapper {

    @Override
    Serializable idOf(THmfAcctCard t) {
        return t.getId();
    }

    @Override
    void assignId(THmfAcctCard t, long id) {
        t.setId(id);
    }
}
