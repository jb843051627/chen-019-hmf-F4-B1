package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.THmfPayBook;
import com.fc.v2.model.auto.THmfPayRow;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookReceipt;

/**
 * 银行交存汇总册核销行 Service接口（batch-process 形状：整批提交，无单条增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITHmfPayRowService {

    /** 按主键回查单行 */
    THmfPayRow selectTHmfPayRowById(Long id);

    /**
     * 整批提交：逐行校验，合法的入库、非法的记入失败明细（保留原始行号，按行号升序），返回**成功行数**。
     * 同批里同一明细码重复进门的，重复条记失败、不连累别行；
     * 同一批次重复提交不得重复入库（回话头一遍成功数）；行数超过单批上限返回 -1 且整批不入库；
     * 空批次或空册次号返回 0。册次号只认入参；到账金额归一到库口精度（decimal(12,2)）后再入库。
     */
    int submitBatch(String batchNo, List<THmfPayRow> rows);

    /** 该批次的失败行明细（按行号升序） */
    List<THmfPayRow> listErrors(String batchNo);

    // ------------------------------------------------- 今年钉死的册子核销口径（另开的批法）

    /**
     * 一册流水进门核销一趟：册次号进门定死，每行按先后自占册内行次；
     * 销账认户号（名字像不算对上），同户号几笔在户面上累加不覆盖，
     * 累到超过本户应缴数的那一行就地挂起并写明缘由，同册其余各行照走；
     * 全册没有一行能核，回执只许写“空册”不许写核讫。
     * 册号第二遍进来只拿行比库里已有的行：去向不改、行不加不改，差异逐行列回；
     * 行数超过中心定的上限，原册退回、一行不留。
     * 册面总行数、这趟已销行数、挂起行数出自同一回统计，册首与各行一次落库。
     */
    PayBookReceipt reconcilePayBook(String batchNo, List<THmfPayRow> rows);

    /** 末了一笔对账：册首数与逐行销出来的数同一回计算核对，接不平当场列出差行。 */
    PayBookAudit auditPayBook(String batchNo);

    /**
     * 挂起的行连着跨过两个对账季还没有去向，挪到往期那一层（现行册面不再露，往期只供翻）；
     * 挪层不改去向、不动册首三数。返回挪了几行。
     */
    int sweepHeldToArchived(Date now);

    /** 撤册手续：在册的册撤下（册情形置已撤、各行逐回），撤后同号方可重新进门。 */
    THmfPayBook withdrawPayBook(String batchNo, String operator, String reason);

    /** 回查在册册头（同号第二遍的结果照册首原样回话） */
    THmfPayBook selectPayBook(String batchNo);

    /** 翻现行册面（挂起未挪往期的行也在内，按册内行次排；挂起行带着它原来排第几） */
    List<THmfPayRow> listCurrentRows(String batchNo);

    /** 翻往期那一层（连跨两个对账季挪过来的挂起行，按册内行次排，只供人翻） */
    List<THmfPayRow> listArchivedRows(String batchNo);
}
