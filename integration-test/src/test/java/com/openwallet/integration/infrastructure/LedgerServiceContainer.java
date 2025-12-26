package com.openwallet.integration.infrastructure;

import com.openwallet.ledger.LedgerServiceApplication;

/**
 * Container for ledger-service in integration tests.
 * Manages the lifecycle of the ledger service.
 */
public class LedgerServiceContainer extends ServiceContainer {

    public static final int DEFAULT_PORT = 9004;

    public LedgerServiceContainer(InfrastructureInfo infrastructure) {
        super("ledger-service", DEFAULT_PORT, infrastructure);
    }

    @Override
    protected Class<?> getMainClass() {
        return LedgerServiceApplication.class;
    }

    @Override
    public String getServiceName() {
        return "ledger-service";
    }
}

