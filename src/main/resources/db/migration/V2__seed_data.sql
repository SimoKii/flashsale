INSERT INTO payment_method (code, name, is_external, created_at, updated_at) VALUES
    ('CREDIT_CARD', '신용카드', 1, NOW(), NOW()),
    ('YPAY',        'Y페이',   1, NOW(), NOW()),
    ('YPOINT',      'Y포인트', 0, NOW(), NOW());

INSERT INTO user (name, created_at, updated_at) VALUES
    ('테스트유저1', NOW(), NOW()),
    ('테스트유저2', NOW(), NOW()),
    ('테스트유저3', NOW(), NOW()),
    ('테스트유저4', NOW(), NOW()),
    ('테스트유저5', NOW(), NOW());

INSERT INTO point_account (user_id, balance, version, created_at, updated_at)
SELECT id, 100000, 0, NOW(), NOW()
FROM user;

INSERT INTO product (name, price, checkin_at, checkout_at, sale_open_at, created_at, updated_at) VALUES
    ('초특가 제주 숙소', 50000,
     '2026-06-01 15:00:00', '2026-06-02 11:00:00',
     '2026-05-14 00:00:00',
     NOW(), NOW());

INSERT INTO stock (product_id, total, reserved, sold, version, created_at, updated_at)
SELECT id, 10, 0, 0, 0, NOW(), NOW()
FROM product
WHERE name = '초특가 제주 숙소';
