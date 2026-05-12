package com.flashsale.domain.shared;

import com.flashsale.common.exception.DomainException;

public record Money(long amount) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (amount < 0) {
            throw new DomainException("Amount cannot be negative: " + amount);
        }
    }

    public static Money of(
            final long amount
    ) {
        return new Money(amount);
    }

    public Money plus(
            final Money other
    ) {
        return new Money(this.amount + other.amount);
    }

    public Money minus(
            final Money other
    ) {
        if (this.amount < other.amount) {
            throw new DomainException("Insufficient balance: " + this.amount + " < " + other.amount);
        }
        return new Money(this.amount - other.amount);
    }

    public boolean isLessThan(
            final Money other
    ) {
        return this.amount < other.amount;
    }
}
