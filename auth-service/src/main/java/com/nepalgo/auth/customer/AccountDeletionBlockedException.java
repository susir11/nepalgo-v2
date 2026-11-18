package com.nepalgo.auth.customer;

// The account can't be deleted right now (e.g. a payment is being verified).
// Nothing was changed; the message is safe to show the customer.
public class AccountDeletionBlockedException extends RuntimeException {
    public AccountDeletionBlockedException(String message) {
        super(message);
    }
}
