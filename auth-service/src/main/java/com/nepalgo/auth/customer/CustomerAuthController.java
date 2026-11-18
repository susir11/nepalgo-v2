package com.nepalgo.auth.customer;

import com.nepalgo.auth.dto.AuthTokenResponse;
import com.nepalgo.auth.dto.FirebaseExchangeRequest;
import com.nepalgo.auth.dto.RefreshRequest;
import com.nepalgo.auth.dto.CustomerProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;
    private final AccountDeletionService accountDeletionService;

    // Typed confirmation guards against an accidental or replayed call deleting an account.
    public record DeleteAccountRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Pattern(regexp = "DELETE", message = "must be DELETE") String confirmation) {}

    @PostMapping("/firebase/exchange")
    public ResponseEntity<AuthTokenResponse> exchange(@Valid @RequestBody FirebaseExchangeRequest request) {
        return ResponseEntity.ok(customerAuthService.exchange(request.idToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(customerAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        customerAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(customerAuthService.getProfile(customerId));
    }

    // Spec 15.2: the customer deletes their own account from Profile.
    // 204 = gone (also on retry), 409 = blocked (payment being verified),
    // 503 = another service couldn't confirm; nothing deleted, safe to retry.
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.deleteAccount(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
