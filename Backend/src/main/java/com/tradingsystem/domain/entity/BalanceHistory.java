package com.tradingsystem.domain.entity;

import com.tradingsystem.domain.enums.BalanceTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

    public class BalanceHistory {

        private Long id;
        private Long accountId;
        private BalanceTransactionType type;
        private BigDecimal amount;
        private UUID relatedOrderId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getAccountId() {
            return accountId;
        }

        public void setAccountId(Long accountId) {
            this.accountId = accountId;
        }

        public BalanceTransactionType getType() {
            return type;
        }

        public void setType(BalanceTransactionType type) {
            this.type = type;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public UUID getRelatedOrderId() {
            return relatedOrderId;
        }

        public void setRelatedOrderId(UUID relatedOrderId) {
            this.relatedOrderId = relatedOrderId;
        }
    }
