package com.openwallet.integration.infrastructure;

import com.openwallet.wallet.WalletServiceApplication;

/**
 * Container for wallet-service in integration tests.
 * Manages the lifecycle of the wallet service.
 */
public class WalletServiceContainer extends ServiceContainer {

    public static final int DEFAULT_PORT = 9003;

    public WalletServiceContainer(InfrastructureInfo infrastructure) {
        super("wallet-service", DEFAULT_PORT, infrastructure);
    }

    @Override
    protected Class<?> getMainClass() {
        return WalletServiceApplication.class;
    }

    @Override
    public String getServiceName() {
        return "wallet-service";
    }
}

