package com.securepay.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {
    Optional<IdempotencyRecordEntity> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
}
