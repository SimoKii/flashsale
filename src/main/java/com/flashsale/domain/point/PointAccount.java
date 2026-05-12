package com.flashsale.domain.point;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import lombok.Getter;

@Getter
public class PointAccount {

    private final long userId;
    private long balance;
    private final long version;

    private PointAccount(
            final long userId,
            final long balance,
            final long version
    ) {
        if (userId <= 0) throw new DomainException("userId must be positive");
        if (balance < 0) throw new DomainException("balance must be non-negative");
        this.userId = userId;
        this.balance = balance;
        this.version = version;
    }

    public static PointAccount of(
            final long userId,
            final long balance
    ) {
        return new PointAccount(
                userId,
                balance,
                0
        );
    }

    public static PointAccount restore(
            final long userId,
            final long balance,
            final long version
    ) {
        return new PointAccount(
                userId,
                balance,
                version
        );
    }

    public void deduct(
            final Money amount
    ) {
        if (balance < amount.amount()) {
            throw new DomainException("Insufficient point balance: " + balance + " < " + amount.amount());
        }
        balance -= amount.amount();
    }

    public void refund(
            final Money amount
    ) {
        balance += amount.amount();
    }
}
