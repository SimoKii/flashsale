package com.flashsale.application.booking;

public sealed interface OrchestratorResult permits OrchestratorResult.AllPaid, OrchestratorResult.Failed, OrchestratorResult.Uncertain, OrchestratorResult.Compensating {

    record AllPaid() implements OrchestratorResult {}

    record Failed() implements OrchestratorResult {}

    record Uncertain() implements OrchestratorResult {}

    record Compensating() implements OrchestratorResult {}
}
