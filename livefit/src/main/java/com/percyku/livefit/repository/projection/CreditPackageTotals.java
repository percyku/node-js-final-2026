package com.percyku.livefit.repository.projection;

import java.math.BigDecimal;

/** M6 營收換算單堂均價用：全站方案總堂數與總價 */
public interface CreditPackageTotals {
    BigDecimal getTotalCreditAmount();
    BigDecimal getTotalPrice();
}
