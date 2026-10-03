package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
 * 口径（今年钉死）：
 * 明细页与详情同一把尺——未删即翻得到，不再暗加“待核对”门槛；分页由调用方统一处理。
 * 立户挂底册：底册得在册、未迁出，代号照底册写。面积、应缴额进门先归一到库口精度
 * （与交存行同一处 {@link PayRowAmounts}），两处一个尺度。面积折档走
 * {@link BandScale} 唯一一处，压着线计入上一层；底册缺线判不出来，档次留空，
 * 不许搅断立户。条目动过，档次照改动后的面积与底册三线重折，换挂底册代号跟着走，
 * 不许一头改了、一头还照旧。删除只销记不抹张——底下挂着的交存行、户面账都照单号认户，
 * 抹了张全跟着抖。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class THmfAcctCardServiceImpl extends ServiceImpl<THmfAcctCardMapper, THmfAcctCard> implements ITHmfAcctCardService {

    @Autowired
    private THmfArchMapper hmfArchMapper;

    @Override
    public THmfAcctCard selectTHmfAcctCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<THmfAcctCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<THmfAcctCard> selectTHmfAcctCardList(Wrapper<THmfAcctCard> queryWrapper) {
        // 明细页与详情同一把尺：未删即见；查询条件照调用方递来的走，分页也由调用方统一处理，
        // 本层不再私开 startPage、不再暗加进展门槛。
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
        // 挂底册：底册得在册、未迁出；代号照底册写，不许各写各的。
        THmfArch arch = record.getSiteId() == null ? null : this.hmfArchMapper.selectOne(
                new QueryWrapper<THmfArch>().eq("id", record.getSiteId()).eq("del_flag", 0));
        if (arch == null) {
            return 0;
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            return 0;
        }
        record.setSiteNo(arch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<THmfAcctCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        // 存量数据进门先归一到库口精度（面积/应缴额与交存行同一把尺），再折档再落库。
        record.setQty(PayRowAmounts.normalize(record.getQty()));
        record.setFineAmt(PayRowAmounts.normalize(record.getFineAmt()));
        record.setGradeLevel(gradeOf(record.getQty(), arch));
        record.setDelFlag(0);
        // 创建者由全局填充记实际操作人，本层不代写。
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTHmfAcctCard(THmfAcctCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        THmfAcctCard cur = this.baseMapper.selectOne(new QueryWrapper<THmfAcctCard>()
                .eq("id", record.getId()).eq("del_flag", 0));
        if (cur == null) {
            return 0;
        }
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<THmfAcctCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        // 进门先归一：面积/应缴额与库口同一尺度。
        if (record.getQty() != null) {
            record.setQty(PayRowAmounts.normalize(record.getQty()));
        }
        if (record.getFineAmt() != null) {
            record.setFineAmt(PayRowAmounts.normalize(record.getFineAmt()));
        }

        // 换挂底册：代号跟着新底册走——底下挂着的记录照 site_no 认，不许一头改一头照旧。
        THmfArch arch = null;
        if (record.getSiteId() != null) {
            arch = this.hmfArchMapper.selectOne(new QueryWrapper<THmfArch>()
                    .eq("id", record.getSiteId()).eq("del_flag", 0));
            if (arch == null || (arch.getStatus() != null && arch.getStatus() == 1)) {
                return 0;
            }
            record.setSiteNo(arch.getSiteNo());
        } else if (cur.getSiteId() != null) {
            arch = this.hmfArchMapper.selectOne(new QueryWrapper<THmfArch>()
                    .eq("id", cur.getSiteId()).eq("del_flag", 0));
        }

        // 条目动过，档次跟着重折：照改动后的面积与底册三线重算，不留旧档。
        BigDecimal qty = record.getQty() != null ? record.getQty() : cur.getQty();
        Integer grade = gradeOf(qty, arch);
        if (grade != null) {
            record.setGradeLevel(grade);
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<THmfAcctCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTHmfAcctCardByIds(String ids) {
        if (StringUtils.isEmpty(ids)) {
            return 0;
        }
        int affected = 0;
        for (Long id : ConvertUtil.toLongArray(ids)) {
            affected += deleteTHmfAcctCardById(id);
        }
        return affected;
    }

    @Override
    public int deleteTHmfAcctCardById(Long id) {
        if (id == null) {
            return 0;
        }
        // 只销记不抹张：底下挂着的交存行、户面账都照单号认户，抹了张全跟着抖。
        THmfAcctCard del = new THmfAcctCard();
        del.setId(id);
        del.setDelFlag(1);
        del.setUpdateTime(new Date());
        return this.baseMapper.update(del, new UpdateWrapper<THmfAcctCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /**
     * 面积照底册三线折档（与支出审定额线同一处算法 {@link BandScale}，压着线计入上一层）。
     * 底册缺线判不出来：档次留空，不搅断立户/改动。
     */
    private static Integer gradeOf(BigDecimal qty, THmfArch arch) {
        if (qty == null || arch == null) {
            return null;
        }
        int band = BandScale.bandOf(qty, arch.getTh1Max(), arch.getTh2Max(), arch.getTh3Max());
        return band == 0 ? null : band;
    }
}
