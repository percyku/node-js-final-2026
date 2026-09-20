package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.common.DeleteResult;
import com.percyku.livefit.dto.creditpackage.CreditPackageCreatedResponse;
import com.percyku.livefit.dto.creditpackage.CreditPackageRequest;
import com.percyku.livefit.dto.creditpackage.CreditPackageResponse;
import com.percyku.livefit.entity.CreditPackage;
import com.percyku.livefit.entity.CreditPurchase;
import com.percyku.livefit.repository.CreditPackageRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/creditPackage.js 的業務邏輯 */
@Service
public class CreditPackageService {

    private final CreditPackageRepository creditPackageRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;

    public CreditPackageService(CreditPackageRepository creditPackageRepository,
                                CreditPurchaseRepository creditPurchaseRepository) {
        this.creditPackageRepository = creditPackageRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
    }

    @Transactional(readOnly = true)
    public List<CreditPackageResponse> getAll() {
        return creditPackageRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(CreditPackageResponse::from)
                .toList();
    }

    @Transactional
    public CreditPackageCreatedResponse create(CreditPackageRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.name())
                || !ValidUtils.isInteger(request.creditAmount())
                || !ValidUtils.isInteger(request.price())
                || request.creditAmount() <= 0
                || request.price() <= 0) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        String trimmed = request.name().trim();
        if (creditPackageRepository.findByName(trimmed).isPresent()) {
            throw ApiException.conflict(ErrorMessages.DUPLICATED);
        }

        CreditPackage entity = new CreditPackage();
        entity.setName(trimmed);
        entity.setCreditAmount(request.creditAmount());
        entity.setPrice(request.price());
        // saveAndFlush：同上，確保 createdAt 有值
        return CreditPackageCreatedResponse.from(creditPackageRepository.saveAndFlush(entity));
    }

    @Transactional
    public DeleteResult delete(UUID creditPackageId) {
        if (!creditPackageRepository.existsById(creditPackageId)) {
            throw ApiException.badRequest(ErrorMessages.INVALID_ID);
        }
        creditPackageRepository.deleteById(creditPackageId);
        return DeleteResult.of(1);
    }

    /** POST /api/credit-package/{id}：使用者購買方案 */
    @Transactional
    public void purchase(UUID userId, UUID creditPackageId) {
        CreditPackage creditPackage = creditPackageRepository.findById(creditPackageId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.INVALID_ID));

        CreditPurchase purchase = new CreditPurchase();
        purchase.setUserId(userId);
        purchase.setCreditPackageId(creditPackage.getId());
        purchase.setPurchasedCredits(creditPackage.getCreditAmount());
        purchase.setPricePaid(BigDecimal.valueOf(creditPackage.getPrice()));
        purchase.setPurchaseAt(Instant.now());
        creditPurchaseRepository.save(purchase);
    }
}
