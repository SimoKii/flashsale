# Flashsale - 기술 결정 기록

선착순 예약 시스템 설계에서 마주한 주요 쟁점과 그 선택의 근거를 기록합니다.

---

## 1. 비동기 큐 도입 (Redis Stream)

### 상황

평시 50 TPS, 00시 오픈 시 1~5분간 500~1000 TPS가 예상됩니다. HTTP 요청 스레드에서 재고 차감과 결제까지 동기 처리하면 스레드 풀이 고갈되어 서비스가 응답 불가 상태에 빠집니다.

### 선택

예약 요청을 Redis Stream에 적재한 뒤 별도 워커가 소비하는 비동기 구조로 분리했습니다. 클라이언트는 `202 Accepted`와 `ticketId`를 받고 결과를 폴링합니다.

### 왜 그렇게 판단했는지

API 레이어는 Redis `XADD` 한 번으로 즉시 응답하므로 수용 TPS가 크게 높아집니다. Redis Stream의 Consumer Group은 메시지를 ACK 전까지 보관하므로 처리 유실이 없으며, 일정 시간 ACK되지 않으면 다른 컨슈머가 재클레임할 수 있어 워커 재시작 시에도 메시지가 자동 복구됩니다.

**대안과 비교:**

- **Kafka**: Zookeeper/KRaft 클러스터, 토픽/파티션 관리 등 별도 운영 인력이 필요합니다. 메시지 영속성, 파티셔닝, 백프레셔 등 Kafka 고유 기능이 현 요구사항에서는 사용되지 않습니다.
- **DB 큐 테이블**: 워커가 `SELECT FOR UPDATE SKIP LOCKED`로 폴링하면 매 폴링마다 트랜잭션이 발생하여 DB 부하가 누적됩니다. 1000 TPS 적재 시 DB 커넥션 풀이 큐 처리에 점유되어 본 비즈니스 쿼리에 영향을 줍니다.
- **RabbitMQ**: 메시지 큐 기능은 충분하지만 Redis가 이미 재고 관리에 필수 도입되어 있어 인프라 한 종을 추가로 운영하는 비용이 발생합니다. 같은 Redis로 큐까지 처리하면 운영 컴포넌트가 줄어듭니다.

---

## 2. Redis Lua 스크립트로 재고 원자 차감

### 상황

수천 건이 동시에 재고를 차감합니다. "재고 조회 → 감소" 사이에 다른 요청이 끼어들면 초과 판매가 발생합니다.

### 선택

재고 예약과 복원을 Lua 스크립트로 구현했습니다 (`atomic_reserve.lua`, `atomic_restore.lua`).

### 왜 그렇게 판단했는지

Redis는 싱글 스레드이므로 Lua 스크립트 실행이 원자적입니다. 별도 분산 락 없이 초과 판매를 차단할 수 있습니다.

**대안과 비교:**

- **MySQL 비관적 락**: 선착순 트래픽에서 DB 커넥션 풀이 가장 먼저 고갈됩니다.
- **MySQL 낙관적 락**: 동시 충돌이 빈번하여 재시도가 폭증합니다.
- **Redis 분산 락 (Redlock)**: Lua 한 번 실행보다 라운드 트립이 많아 처리량이 낮습니다.

`atomic_reserve.lua`는 티켓 중복 처리 차단(`SADD ticket_processed`), 재고 감소(`DECRBY`), 보유자 등록(`ZADD`)을 하나의 스크립트로 처리합니다. 워커가 같은 메시지를 재처리해도 `ticket_processed`로 중복 차감을 방지합니다.

---

## 3. 멱등성 보장 전략

### 상황

요구사항에 "짧은 간격의 연속 결제 요청에 대한 중복 처리 방지"가 명시되어 있습니다. 분산 환경의 메시지 재처리, 클라이언트 재시도, 사용자 더블 클릭 등 다양한 경로로 중복이 발생할 수 있습니다.

### 선택

네 가지 계층에서 중복을 차단합니다.

| 계층 | 방어 수단 | 차단 시나리오                  |
|---|---|--------------------------|
| API | `Idempotency-Key` 헤더, 사용자별 진입 가드 (`SET NX`) | 클라이언트 재시도, 더블 클릭         |
| 워커 | 각 처리 단계에서 상태 확인 후 이미 처리되었으면 건너뜀 | Redis Stream 재전송, 워커 재시작 |
| DB | `orders.idempotency_key` UNIQUE 제약 | 동시 INSERT 경합             |
| PG | 결제 라인별 `idempotencyKey` 전달 | PG 측에서의 중복 청구            |

### 왜 그렇게 판단했는지

각 계층이 차단하는 중복 시나리오가 서로 다르기 때문입니다. API 헤더만으로는 워커 재시작 시 재전송되는 메시지를 막을 수 없고, DB UNIQUE 제약만으로는 PG에 두 번 청구되는 것을 막을 수 없습니다.

`IdempotencyScenarioTest`에서 동일 멱등키로 10개 동시 요청 시 DB 주문이 정확히 1건만 생성됨을 검증했습니다.

---

## 4. 복합 결제와 보상 트랜잭션

### 상황

요구사항에 명시된 복합 결제 (신용카드 + 포인트, Y페이 + 포인트)에서, 포인트 차감이 성공한 뒤 카드 승인이 실패하면 포인트를 환불해야 합니다.

### 선택

`sequence` 순서로 결제 라인을 처리하고, 중간에 실패하면 이미 승인된 결제를 역순으로 취소하는 Saga 패턴을 적용했습니다. 각 단계의 결과를 `payment_event` 테이블에 기록합니다.

### 왜 그렇게 판단했는지

PG는 외부 시스템이라 트랜잭션 범위에 포함시킬 수 없고, 부분 실패 시 환불 로직이 필요합니다. `payment_event` 기록은 워커 재시작 시 어디까지 진행됐는지 복원하기 위함입니다.

**PG 응답 불확실 (`PaymentResult.Unknown`) 처리:**

PG가 타임아웃 등으로 결제 성공 여부를 확정하지 못한 경우, 즉시 취소하면 실제로는 결제된 금액을 사용자에게 반영하지 못할 수 있습니다. 이 경우 주문을 UNCERTAIN 상태로 두고 `payment_reconcile_queue`에 등록하여 별도 워커가 재조회하도록 위임합니다.

---

## 5. 결제 게이트웨이 확장 구조

### 상황

요구사항에 "새로운 결제 수단 추가 시 Booking API 비즈니스 로직 수정 최소화"가 명시되어 있습니다.

### 선택

`PaymentGateway` 인터페이스를 정의하고 `supports()` 메서드로 결제 수단별 구현체를 선택하는 Strategy 패턴을 적용했습니다. Spring이 `List<PaymentGateway>`로 모든 구현체를 주입하고, 결제 처리 로직이 결제 수단 코드에 맞는 구현체를 찾습니다.

```java
public interface PaymentGateway {
    PaymentMethodCode supports();
    PaymentResult charge(PaymentLineCommand cmd);
    PaymentResult cancel(String pgTxId);
    PaymentResult inquiry(String idempotencyKey);
}
```

### 왜 그렇게 판단했는지

새 결제 수단 추가 시 `PaymentGateway` 구현체만 작성하고 `@Component`로 등록하면 자동으로 인식됩니다. 처리 로직(`PaymentOrchestrator`), 워커, 컨트롤러 코드는 변경되지 않습니다.

현재 구현체는 `CreditCardGateway`, `YPayGateway`, `YPointGateway` 세 종류입니다.

---

## 6. 고가용성 Redis

### 상황

요구사항에 "Redis 장애 시 Fallback 전략 수립"이 명시되어 있습니다. 예약 큐, 재고, 멱등성 키 등 핵심 상태가 모두 Redis에 있으므로 Master 장애 시 서비스가 전면 중단됩니다.

### 선택

Master 1대 + Replica 2대 + Sentinel 3대로 구성했습니다.

### 왜 그렇게 판단했는지

Sentinel이 Master 장애를 자동으로 감지하고 Replica를 Master로 승격시킵니다. 페일오버는 10~30초 내 완료되며 애플리케이션 재시작이 필요하지 않습니다.

Sentinel을 홀수(3대)로 둔 이유는 과반수 판단(quorum=2)을 위함이며, split-brain을 방지합니다.

Lettuce 클라이언트는 `spring.data.redis.sentinel.master`와 `nodes` 설정만으로 토폴로지를 자동 추적하므로 애플리케이션 코드 변경이 필요하지 않습니다.

### 한계

Sentinel은 Master 장애만 해결합니다. Redis 전체 클러스터가 다운되거나 네트워크가 분리되는 시나리오는 별도 fallback이 필요하나 현재 미구현 상태입니다 (8번 항목 참조).

---

## 7. Kill Switch와 진입 가드

### 상황

운영 중 PG 장애나 재고 불일치 등 이상 징후가 감지되면 코드 배포 없이 즉시 트래픽을 차단해야 합니다. 또한 한 사용자가 짧은 시간에 여러 번 예약을 시도하면 한 사람이 여러 자리를 차지할 수 있습니다.

### 선택

- **Kill Switch**: `kill_switch:product:{id}` Redis 키 존재 여부로 차단합니다. 활성화 시 신규 요청은 DLQ로 라우팅됩니다.
- **진입 가드**: 예약 접수 시 `booking:in-flight:user:{userId}` 키를 `SET NX EX 30`으로 설정합니다. 이미 존재하면 409를 반환합니다.

### 왜 그렇게 판단했는지

Kill Switch를 DB가 아닌 Redis에 둔 이유는 즉시 반영을 위해서입니다. `redis-cli SET kill_switch:product:1 1`으로 차단할 수 있고, 상품별로 독립적으로 동작합니다.

진입 가드와 멱등성 키는 역할이 다릅니다.

- **멱등성 키**: 동일 요청의 재전송을 같은 결과로 처리합니다 (네트워크 재시도).
- **진입 가드**: 다른 멱등키를 가진 동시 요청의 중복 진입을 차단합니다.

진입 가드의 30초 TTL은 워커 크래시 시 자동 해제를 보장합니다.

---

## 8. 이 과제에서 더 추가한다면

시간 제약으로 다음 컴포넌트는 인터페이스만 정의된 상태입니다. 시간이 더 있었다면 다음 순서로 구현했을 것입니다.

### 8-1. PendingOrderSweepWorker

만료된 PENDING 주문을 자동으로 정리하는 워커입니다.

워커가 처리 중에 크래시하면 PENDING 주문이 영구 잔존하고 해당 재고가 복원되지 않습니다. 10개 한정 중 일부가 영구 점유 상태로 남으면 미달 판매가 발생합니다.

1분 주기로 `expires_at`이 지난 PENDING 주문을 조회하여 재고 복원, 주문 FAILED 처리, 진입 가드 키 삭제를 수행합니다. ShedLock으로 인스턴스 간 중복 실행을 방지합니다.

### 8-2. PaymentReconcileWorker

UNCERTAIN 상태 주문을 PG에 재조회하여 확정하는 워커입니다.

PG 타임아웃으로 UNCERTAIN이 된 주문이 `payment_reconcile_queue`에 적재되지만 후속 처리가 없어 영구 잔존합니다. 사용자가 결제 결과를 확인할 수 없는 상태가 지속됩니다.

30초 주기로 PENDING 항목을 조회하여 PG `inquiry` 호출 후 결과를 반영합니다. Success는 PAID로 확정, Failure는 재고 복원 후 FAILED 처리, Unknown은 지수 백오프로 재시도합니다.

### 8-3. StockPortFacade (Circuit Breaker → DB Fallback)

Redis 전체 장애 시 DB로 fallback하는 경로입니다.

Sentinel이 Master 페일오버는 처리하지만 Redis 전체 클러스터 다운이나 네트워크 분리 시에는 서비스가 중단됩니다. 이를 막으려면 Circuit Breaker로 Redis 호출 실패를 감지하고 DB 직접 차감 경로로 우회해야 합니다.

Redis 호출을 facade로 감싸고 Resilience4j Circuit Breaker를 적용합니다. Circuit OPEN 시 DB `SELECT FOR UPDATE`로 재고 차감을 대체합니다. DB는 Redis보다 느려 1000 TPS는 견디기 어렵지만 "전체 다운"은 막을 수 있습니다.

### 8-4. RedisHealthCheckWorker

Redis 상태를 주기적으로 확인하여 Kill Switch를 자동으로 조작하는 워커입니다.

현재는 운영자가 수동으로 Kill Switch를 토글해야 합니다. 장애 감지부터 차단까지의 시간을 줄이려면 자동화가 필요합니다.

30초 주기로 Redis PING을 수행합니다. N회 연속 실패 시 Kill Switch ON, 복구 감지 시 OFF로 자동 전환합니다.

---

## 9. 도입한 라이브러리

| 라이브러리 | 사유 |
|---|---|
| `spring-boot-starter-data-redis` | Redis Stream, Lua, Sentinel 통합 |
| `spring-boot-starter-data-jpa` | 도메인 영속성 |
| `flyway-mysql` | DDL 마이그레이션 자동화 |
| `resilience4j-spring-boot3` | Circuit Breaker (PG 장애 대응) |
| `shedlock-spring`, `shedlock-provider-jdbc-template` | 분산 스케줄러 락 (실행 중복 방지) |
| `testcontainers` | 통합 테스트용 컨테이너 (실제 Redis/MySQL 동작 검증) |
| `awaitility` | 비동기 테스트 폴링 |
| `lombok` | 보일러플레이트 감소 |