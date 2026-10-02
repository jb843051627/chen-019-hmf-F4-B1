package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfPayRowMapper;
import com.fc.v2.model.auto.THmfPayRow;

/** 银行交存核销行内存假 Mapper。 */
class FakePayRowMapper extends InMemoryMapper<THmfPayRow> implements THmfPayRowMapper {

    @Override
    Serializable idOf(THmfPayRow t) {
        return t.getId();
    }

    @Override
    void assignId(THmfPayRow t, long id) {
        t.setId(id);
    }
}
