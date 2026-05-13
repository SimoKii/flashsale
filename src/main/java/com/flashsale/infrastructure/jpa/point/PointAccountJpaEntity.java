package com.flashsale.infrastructure.jpa.point;

import com.flashsale.domain.point.PointAccount;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccountJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private long userId;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private Long version;

    public static PointAccountJpaEntity from(
            final PointAccount account
    ) {
        PointAccountJpaEntity entity = new PointAccountJpaEntity();
        entity.userId = account.getUserId();
        entity.balance = account.getBalance();
        return entity;
    }

    public PointAccount toDomain() {
        return PointAccount.restore(
                userId,
                balance,
                version
        );
    }

    public void updateFrom(
            final PointAccount account
    ) {
        this.balance = account.getBalance();
        this.version = account.getVersion();
    }
}