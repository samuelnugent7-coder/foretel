package com.coreclub.fulfillment.model;

import java.util.Collections;
import java.util.List;

public record PackageSyncRequest(String token, List<ManagedPackage> packages) {

    public PackageSyncRequest {
        packages = packages == null ? Collections.emptyList() : packages;
    }

    public boolean isValid() {
        return token != null && !token.isBlank();
    }
}
