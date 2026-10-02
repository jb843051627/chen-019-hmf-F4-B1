package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfPayBookMapper;
import com.fc.v2.model.auto.THmfPayBook;

/** 银行交存汇总册册头内存假 Mapper。 */
class FakePayBookMapper extends InMemoryMapper<THmfPayBook> implements THmfPayBookMapper {

    @Override
    Serializable idOf(THmfPayBook t) {
        return t.getId();
    }

    @Override
    void assignId(THmfPayBook t, long id) {
        t.setId(id);
    }
}
