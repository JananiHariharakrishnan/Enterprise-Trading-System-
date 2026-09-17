package com.tradingsystem.model;

import com.tradingsystem.enums.BalanceTransactionType;

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

        public void validate() {
            if (this.accountId == null || this.accountId <= 0) {
                throw new IllegalArgumentException("Invalid account id");
            }
            if (this.type == null) {
                throw new IllegalArgumentException("Transaction type is required");
            }
            if (this.amount == null) {
                throw new IllegalArgumentException("Amount is required");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BalanceHistory that = (BalanceHistory) o;

            if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) return false;
            if (type != that.type) return false;
            if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
            return relatedOrderId != null ? relatedOrderId.equals(that.relatedOrderId) : that.relatedOrderId == null;
        }

        @Override
        public int hashCode() {
            int result = accountId != null ? accountId.hashCode() : 0;
            result = 31 * result + (type != null ? type.hashCode() : 0);
            result = 31 * result + (amount != null ? amount.hashCode() : 0);
            result = 31 * result + (relatedOrderId != null ? relatedOrderId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "BalanceHistory{" +
                    "accountId=" + accountId +
                    ", type=" + type +
                    ", amount=" + amount +
                    ", relatedOrderId=" + relatedOrderId +
                    '}';
        }
    }
