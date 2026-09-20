package com.percyku.livefit.repository;

import com.percyku.livefit.entity.CreditPackage;
import com.percyku.livefit.repository.projection.CreditPackageTotals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditPackageRepository extends JpaRepository<CreditPackage, UUID> {

    Optional<CreditPackage> findByName(String name);

    List<CreditPackage> findAllByOrderByCreatedAtAsc();

    /** M6 營收：全站方案的總堂數與總價，用來換算單堂均價 */
    @Query(value = "SELECT COALESCE(SUM(credit_amount), 0) AS totalCreditAmount, "
            + "COALESCE(SUM(price), 0) AS totalPrice FROM credit_packages",
            nativeQuery = true)
    CreditPackageTotals findTotals();
}
