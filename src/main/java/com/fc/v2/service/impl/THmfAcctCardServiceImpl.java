package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.THmfAcctCardMapper;
import com.fc.v2.mapper.auto.THmfArchMapper;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfArch;
import com.fc.v2.service.ITHmfAcctCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 分户账立户单Service业务层处理
 *
 * 列表与详情同一把尺：都只认未删（del_flag=0）的立户单，列表照调用方递来的条件过滤，
 * 不在服务层另塞状态、另钉死分页（分页由页面入口统一走 startPage）。
 * 底册头只按“在册且未删”认一遍——新增、改挂同一个取法，不许一处带删除标记一处裸查；
 * 立户单号（bill_no）是下游册子核销与户面余额挂接的户号，立单后冻结，后手改不得，
 * 改挂底册只同步底册代号与面积档次，不让底下已销账的行跟着抖成孤儿。
 * 删除一律逻辑删（del_flag=1），单还在、账不断，重新录入同号照头一遍的规矩再走一遍。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class THmfAcctCardServiceImpl extends ServiceImpl<THmfAcctCardMapper, THmfAcctCard> implements ITHmfAcctCardService {

    /** 立户单进展：待核对（新单落此态） */
    private static final int STATUS_PENDING = 0;
    /** 底册情形：在册 */
    private static final int ARCH_ACTIVE = 0;

    @Autowired
    private THmfArchMapper hmfArchMapper;

    @Override
    public THmfAcctCard selectTHmfAcctCardById(Long id) {
        if (id == null) {
            return null;
        }
        THmfAcctCard c = this.baseMapper.selectById(id);
        // 与列表同尺：删过的不当在册详情回。
        return c != null && (c.getDelFlag() == null || c.getDelFlag() == 0) ? c : null;
    }

    @Override
    public List<THmfAcctCard> selectTHmfAcctCardList(Wrapper<THmfAcctCard> queryWrapper) {
        // 调用方的条件照收（含状态等），服务层只钉同一条未删基线，与详情同尺。
        QueryWrapper<THmfAcctCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<THmfAcctCard>) queryWrapper
                : new QueryWrapper<THmfAcctCard>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTHmfAcctCard(THmfAcctCard record) {
        if (record == null) {
            return 0;
        }
        if (!StringUtils.isNotEmpty(record.getBillNo())) {
            return 0;
        }

        // 底册头只认在册且未删，整单就查这一遍，档次、底册代号都从它出，不许再裸查第二遍。
        THmfArch refArch = activeArch(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        String billNo = record.getBillNo().trim();
        Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<THmfAcctCard>()
                .eq("bill_no", billNo).eq("del_flag", 0));
        if (dupCnt != null && dupCnt > 0) {
            return 0;
        }

        record.setBillNo(billNo);
        record.setSiteNo(refArch.getSiteNo());
        record.setGradeLevel(gradeLevelOf(record.getQty(), refArch));
        record.setStatus(record.getStatus() == null ? STATUS_PENDING : record.getStatus());
        record.setCreateBy(billNo);
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTHmfAcctCard(THmfAcctCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        THmfAcctCard stored = selectTHmfAcctCardById(record.getId());
        if (stored == null) {
            return 0;
        }

        // 立户单号是下游销账/户面挂接的户号：冻结，不随后手改单而变（改号只给底下挂着的账抖出孤儿）。
        record.setBillNo(stored.getBillNo());

        if (record.getSiteId() != null && !record.getSiteId().equals(stored.getSiteId())) {
            // 改挂底册：同一把尺认在册且未删的底册；认不上整单不动。
            THmfArch refArch = activeArch(record.getSiteId());
            if (refArch == null) {
                return 0;
            }
            record.setSiteNo(refArch.getSiteNo());
            record.setGradeLevel(gradeLevelOf(
                    record.getQty() == null ? stored.getQty() : record.getQty(), refArch));
        } else {
            record.setSiteId(stored.getSiteId());
            record.setSiteNo(stored.getSiteNo());
            // 没改挂：面积动了就照原底册三线重折档；底册已不在册则档次维持原样，不拦别的修改。
            if (record.getQty() != null) {
                THmfArch refArch = activeArch(stored.getSiteId());
                if (refArch != null) {
                    record.setGradeLevel(gradeLevelOf(record.getQty(), refArch));
                }
            }
        }

        record.setUpdateTime(new Date());
        // 进门已照未删基线核过在册原单，按主键回写即可。
        return this.baseMapper.updateById(record);
    }

    @Override
    public int deleteTHmfAcctCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        return logicalDelete(Arrays.asList(idArr));
    }

    @Override
    public int deleteTHmfAcctCardById(Long id) {
        if (id == null) {
            return 0;
        }
        return logicalDelete(Arrays.asList(id));
    }

    /** 逻辑删：只翻删除标记，单与底下挂着的账都不断。 */
    private int logicalDelete(List<Long> ids) {
        int n = 0;
        for (Long id : ids) {
            THmfAcctCard c = this.baseMapper.selectById(id);
            if (c != null && (c.getDelFlag() == null || c.getDelFlag() == 0)) {
                c.setDelFlag(1);
                c.setUpdateTime(new Date());
                this.baseMapper.updateById(c);
                n++;
            }
        }
        return n;
    }

    /** 在册且未删的底册头：新增、改挂共用这一处取法。 */
    private THmfArch activeArch(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        List<THmfArch> rows = this.hmfArchMapper.selectList(new QueryWrapper<THmfArch>()
                .eq("id", siteId).eq("del_flag", 0));
        if (rows.isEmpty()) {
            return null;
        }
        THmfArch arch = rows.get(0);
        if (arch.getStatus() != null && arch.getStatus() != ARCH_ACTIVE) {
            return null;
        }
        return arch;
    }

    /**
     * 建面照底册三线折档：等于上限计入上一层。某条线没维护（null）不参与，
     * 三条线都没维护档次点不出来，给 0 待核，不许拿空阈值把整趟搅断。
     */
    private int gradeLevelOf(java.math.BigDecimal qty, THmfArch arch) {
        if (qty == null || arch == null) {
            return 0;
        }
        if (arch.getTh1Max() != null && qty.compareTo(arch.getTh1Max()) <= 0) {
            return 1;
        }
        if (arch.getTh2Max() != null && qty.compareTo(arch.getTh2Max()) <= 0) {
            return 2;
        }
        if (arch.getTh3Max() != null && qty.compareTo(arch.getTh3Max()) <= 0) {
            return 3;
        }
        if (arch.getTh1Max() == null && arch.getTh2Max() == null && arch.getTh3Max() == null) {
            return 0;
        }
        return 4;
    }
}
