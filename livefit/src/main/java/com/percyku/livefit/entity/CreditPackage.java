package com.percyku.livefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 對應 backend/entities/CreditPackage.js，table: credit_packages（name 未設長度、price 為 integer） */
@Entity
@Table(name = "credit_packages")
public class CreditPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "credit_amount", nullable = false)
    private Integer creditAmount = 0;

    @Column(name = "price", nullable = false)
    private Integer price = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCreditAmount() { return creditAmount; }
    public void setCreditAmount(Integer creditAmount) { this.creditAmount = creditAmount; }
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
    public Instant getCreatedAt() { return createdAt; }
}
