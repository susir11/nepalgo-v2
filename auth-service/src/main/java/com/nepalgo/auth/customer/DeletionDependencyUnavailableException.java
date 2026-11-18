package com.nepalgo.auth.customer;

// Another service couldn't confirm it erased its part. We stop BEFORE deleting
// the account (fail-closed), so a retry is always safe.
public class DeletionDependencyUnavailableException extends RuntimeException {
    public DeletionDependencyUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable, nothing was deleted. Please try again in a moment.", cause);
    }
}
