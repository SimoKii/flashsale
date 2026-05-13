CREATE TABLE product (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(200) NOT NULL,
    price        BIGINT       NOT NULL,
    checkin_at   DATETIME     NOT NULL,
    checkout_at  DATETIME     NOT NULL,
    sale_open_at DATETIME     NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE stock (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    product_id BIGINT   NOT NULL,
    total      INT      NOT NULL,
    reserved   INT      NOT NULL DEFAULT 0,
    sold       INT      NOT NULL DEFAULT 0,
    version    BIGINT   NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_stock_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE point_account (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    balance    BIGINT   NOT NULL DEFAULT 0,
    version    BIGINT   NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_point_account_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE point_tx (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    order_id   BIGINT      NULL,
    type       VARCHAR(20) NOT NULL COMMENT 'USE / REFUND / EARN',
    amount     BIGINT      NOT NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_point_tx_user (user_id),
    INDEX idx_point_tx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_method (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(50) NOT NULL,
    is_external TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payment_method_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE orders (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    product_id       BIGINT      NOT NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    total_amount     BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL COMMENT 'PENDING / PAID / COMPENSATING / FAILED / UNCERTAIN / CANCELED',
    response_body    TEXT        NULL,
    expires_at       DATETIME    NOT NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_orders_idempotency_key (idempotency_key),
    INDEX idx_orders_user (user_id),
    INDEX idx_orders_status_expires (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_line (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    order_id            BIGINT       NOT NULL,
    payment_method_code VARCHAR(20)  NOT NULL,
    amount              BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL COMMENT 'REQUESTED / APPROVED / DECLINED / CANCELED / CANCEL_PENDING / CANCEL_FAILED / UNCERTAIN',
    sequence            INT          NOT NULL COMMENT '1: 포인트, 2: 외부 결제',
    pg_tx_id            VARCHAR(100) NULL,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_line_order_seq (order_id, sequence)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    payment_line_id BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    pg_tx_id        VARCHAR(100) NULL,
    response_body   TEXT         NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_event_line (payment_line_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_reconcile_queue (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    type             VARCHAR(20) NOT NULL COMMENT 'PAYMENT_INQUIRY / CANCEL_RETRY',
    order_id         BIGINT      NOT NULL,
    payment_line_id  BIGINT      NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / FAILED',
    retry_count      INT         NOT NULL DEFAULT 0,
    next_retry_at    DATETIME    NOT NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_reconcile_status_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
