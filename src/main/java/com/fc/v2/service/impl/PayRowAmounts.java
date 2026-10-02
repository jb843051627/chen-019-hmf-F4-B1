package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 本行到账金额（t_hmf_pay_row.qty）的统一口径。
 *
 * 到账金额是算钱的底子：库口钉死 decimal(12,2)，进门的钱先在这一处归一再落库、再记账，
 * 旧批法 {@link THmfPayRowServiceImpl#submitBatch}、册子核销
 * {@link THmfPayRowServiceImpl#reconcilePayBook} 与下游户面余额
 * （{@link THmfUseFlowServiceImpl}）都只认归一后的数，不许各算各的——
 * 底子一偏，册首、户面、列支便处处接不平。
 *
 * @author fuce
 * @date 2026-10-02
 */
final class PayRowAmounts {

    /** 与 t_hmf_pay_row.qty 库口一致：小数留到分。 */
    static final int DB_SCALE = 2;

    private PayRowAmounts() {
    }

    /**
     * 归一到库口精度：四舍五入到分（8000.005→8000.01）。
     * null 原样回 null——正不正由各进门处自己判，这里只管归一度。
     */
    static BigDecimal normalize(BigDecimal qty) {
        return qty == null ? null : qty.setScale(DB_SCALE, RoundingMode.HALF_UP);
    }
}
