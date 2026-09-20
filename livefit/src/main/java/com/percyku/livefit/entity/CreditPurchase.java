package com.percyku.livefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 對應 backend/entities/CreditPurchase.js，table: credit_purchase。
 * price_paid 是 numeric(10,2)，purchase_at 為手動寫入（非自動建立時間）。
 */
@Entity
@Table(name = "credit_purchase")
public class CreditPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credit_package_id", nullable = false)
    private UUID creditPackageId;

    @Column(name = "purchased_credits", nullable = false)
    private Integer purchasedCredits;

    @Column(name = "price_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePaid;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "purchase_at", nullable = false)
    private Instant purchaseAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_package_id", insertable = false, updatable = false)
    private CreditPackage creditPackage;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getCreditPackageId() { return creditPackageId; }
    public void setCreditPackageId(UUID creditPackageId) { this.creditPackageId = creditPackageId; }
    public Integer getPurchasedCredits() { return purchasedCredits; }
    public void setPurchasedCredits(Integer purchasedCredits) { this.purchasedCredits = purchasedCredits; }
    public BigDecimal getPricePaid() { return pricePaid; }
    public void setPricePaid(BigDecimal pricePaid) { this.pricePaid = pricePaid; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPurchaseAt() { return purchaseAt; }
    public void setPurchaseAt(Instant purchaseAt) { this.purchaseAt = purchaseAt; }
    public CreditPackage getCreditPackage() { return creditPackage; }
}
