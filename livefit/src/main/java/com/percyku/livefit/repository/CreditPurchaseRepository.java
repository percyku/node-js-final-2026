package com.percyku.livefit.repository;

import com.percyku.livefit.entity.CreditPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CreditPurchaseRepository extends JpaRepository<CreditPurchase, UUID> {

    @Query("SELECT cp FROM CreditPurchase cp JOIN FETCH cp.creditPackage "
            + "WHERE cp.userId = :userId ORDER BY cp.purchaseAt DESC")
    List<CreditPurchase> findByUserIdWithPackageOrderByPurchaseAtDesc(UUID userId);

    /** 使用者購買的總堂數；無購買紀錄時回 0（Node 版此處會得到 null 而使餘額變負數） */
    @Query("SELECT COALESCE(SUM(cp.purchasedCredits), 0) FROM CreditPurchase cp WHERE cp.userId = :userId")
    long sumPurchasedCreditsByUserId(UUID userId);
}
