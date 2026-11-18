package com.nepalgo.auth.customer;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Customer-initiated account deletion (spec 15.2). Each service erases its OWN
// data, in this order, and auth deletes the account only after every other step
// has confirmed (fail-closed). Analogy: closing a bank account - cancel pending
// transfers and redact the statements (booking), stop all mail to the customer
// (notification), close the online-banking login (Firebase), and only then close
// the account itself and retire its number forever.
//
// Retry-safe: every step is idempotent. If step 3 fails, the customer presses
// "Delete" again and steps 1-2 simply find nothing left to do.
//
// Deliberately KEPT (tax law / legal evidence, Privacy Policy s.4): bills and
// invoices (payment-service), consent records (legal-service), and the booking
// rows themselves (now without name, passport and e-mail).
@Slf4j
@Service
public class AccountDeletionService {

    private final CustomerRepository customers;
    private final RetiredNepalgoIdRepository retiredIds;
    private final TransactionTemplate tx;
    private final RestClient.Builder lb;
    private final String internalApiKey;

    // Explicit constructor (not Lombok): Lombok would drop the @Qualifier.
    public AccountDeletionService(CustomerRepository customers, RetiredNepalgoIdRepository retiredIds, TransactionTemplate tx,
                                  @LoadBalanced @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lb,
                                  @Value("${nepalgo.internal.api-key}") String internalApiKey) {
        this.customers = customers;
        this.retiredIds = retiredIds;
        this.tx = tx;
        this.lb = lb;
        this.internalApiKey = internalApiKey;
    }

    public void deleteAccount(Long customerId) {
        Customer customer = customers.findById(customerId).orElse(null);
        if (customer == null) {
            return;   // already deleted (e.g. retry after a timeout): same outcome, no error
        }

        // 1. booking-service: withdraw open requests, strip personal data (409 while a payment is being verified)
        callErase("booking-service", "http://booking-service/internal/bookings/customers/{id}/erase", customerId);
        // 2. notification-service: inbox, queued e-mails/pushes, device tokens
        callErase("notification-service", "http://notification-service/internal/notifications/customers/{id}/erase", customerId);
        // 3. Firebase login (holds the e-mail address). "Not found" = already gone = fine.
        try {
            FirebaseAuth.getInstance().deleteUser(customer.getFirebaseUid());
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw new DeletionDependencyUnavailableException("Firebase", e);
            }
        }
        // 4. our own data, atomically: retire the NepalGo ID forever, delete the row
        //    (refresh tokens cascade; admin audit rows keep their text, FK set to NULL - V4).
        tx.executeWithoutResult(status -> {
            if (!retiredIds.existsById(customer.getNepalgoId())) {
                retiredIds.save(new RetiredNepalgoId(customer.getNepalgoId()));
            }
            customers.deleteById(customerId);
        });
        log.info("Customer account {} deleted; NepalGo ID retired", customerId);
    }

    private void callErase(String service, String url, Long customerId) {
        try {
            Map<?, ?> result = lb.build().post().uri(url, customerId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve().body(Map.class);
            log.info("Account deletion {}: {} erased {}", customerId, service, result);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                Map<?, ?> body = e.getResponseBodyAs(Map.class);
                Object detail = body == null ? null : body.get("detail");
                throw new AccountDeletionBlockedException(detail != null ? detail.toString()
                        : "Your account can't be deleted right now. Please try again later.");
            }
            throw new DeletionDependencyUnavailableException(service, e);
        } catch (RuntimeException e) {
            throw new DeletionDependencyUnavailableException(service, e);
        }
    }
}
