package com.fc.v2.service.impl;

import java.math.BigDecimal;

/**
 * 三条上限线折档的唯一算法——支出审定额线（{@link THmfSpendLineServiceImpl}）与
 * 分户立户单面积折档（{@link THmfAcctCardServiceImpl}）共用这一处，不许两个模块各写各的：
 * 口径只认这里，谁再改只动这一份，省得一头改了、一头还照旧。
 *
 * 口径（与种子档案一致）：三条上限线由低到高，输入落在哪根线之下归哪一档（1..3），
 * 高过最高线归第 4 档；压着线（等于上限）计入上一层——严格小于才归本档。
 * 缺的那根线不卡（跳过），一条缺值不许搅断整轮；三根线全缺或输入缺失，判不出来，回 0
 * （求值处按不可用回 0，立户处档次留空，都不许抛错）。
 *
 * @author fuce
 * @date 2026-10-03
 */
final class BandScale {

    private BandScale() {
    }

    /**
     * 折档：input &lt; th1 → 1；input &lt; th2 → 2；input &lt; th3 → 3；否则 → 4。
     * 等于上限不计入本档（计入上一层）。为 null 的线跳过；三线全缺或 input 为 null 回 0。
     */
    static int bandOf(BigDecimal input, BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        if (input == null) {
            return 0;
        }
        boolean anyLine = false;
        if (th1 != null) {
            anyLine = true;
            if (input.compareTo(th1) < 0) {
                return 1;
            }
        }
        if (th2 != null) {
            anyLine = true;
            if (input.compareTo(th2) < 0) {
                return 2;
            }
        }
        if (th3 != null) {
            anyLine = true;
            if (input.compareTo(th3) < 0) {
                return 3;
            }
        }
        return anyLine ? 4 : 0;
    }
}
