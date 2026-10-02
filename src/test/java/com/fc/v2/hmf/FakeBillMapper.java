package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfBudgetBillMapper;
import com.fc.v2.model.auto.THmfBudgetBill;

/** 预算单内存假 Mapper。 */
class FakeBillMapper extends InMemoryMapper<THmfBudgetBill> implements THmfBudgetBillMapper {

    @Override
    Serializable idOf(THmfBudgetBill t) {
        return t.getId();
    }

    @Override
    void assignId(THmfBudgetBill t, long id) {
        t.setId(id);
    }
}
