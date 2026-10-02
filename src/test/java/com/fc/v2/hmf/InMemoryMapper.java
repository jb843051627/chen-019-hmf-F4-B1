package com.fc.v2.hmf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 测试用内存 BaseMapper：按 Wrapper 等值条件过滤。仅实现核签业务实际调用的方法，
 * 其余方法给桩（核签业务不应调用）。
 */
abstract class InMemoryMapper<T> implements BaseMapper<T> {

    protected final List<T> rows = new ArrayList<>();
    protected final AtomicLong seq = new AtomicLong(1000);

    List<T> all() {
        return rows;
    }

    abstract Serializable idOf(T t);

    abstract void assignId(T t, long id);

    @Override
    public int insert(T entity) {
        assignId(entity, seq.incrementAndGet());
        rows.add(entity);
        return 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T selectById(Serializable id) {
        for (T r : rows) {
            if (id.equals(idOf(r))) {
                return r;
            }
        }
        return null;
    }

    @Override
    public Integer selectCount(Wrapper<T> queryWrapper) {
        return Integer.valueOf(WrapperEval.filter(rows, queryWrapper).size());
    }

    @Override
    public List<T> selectList(Wrapper<T> queryWrapper) {
        List<T> round = WrapperEval.filter(rows, queryWrapper);
        round.sort(WrapperEval.orderOf(queryWrapper));
        return round;
    }

    @Override
    public int updateById(T entity) {
        for (int i = 0; i < rows.size(); i++) {
            if (idOf(rows.get(i)).equals(idOf(entity))) {
                rows.set(i, entity);
                return 1;
            }
        }
        return 0;
    }

    // ---- 核签业务不调用，桩之 ----

    @Override
    public int deleteById(Serializable id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Wrapper<T> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(T entity, Wrapper<T> updateWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> selectBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> selectByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T selectOne(Wrapper<T> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<T> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> selectObjs(Wrapper<T> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E extends IPage<T>> E selectPage(E page, Wrapper<T> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E extends IPage<Map<String, Object>>> E selectMapsPage(E page, Wrapper<T> queryWrapper) {
        throw new UnsupportedOperationException();
    }
}
