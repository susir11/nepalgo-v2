package com.nepalgo.legal;

import com.nepalgo.legal.common.LegalErrors;
import com.nepalgo.legal.content.SiteContent;
import com.nepalgo.legal.content.SiteContentServiceTestBridge;
import com.nepalgo.legal.document.LegalDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain unit tests (no Spring context, no DB).
class LegalUnitTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void fingerprintChangesWhenAnyFieldChanges() {
        LegalDocument d = new LegalDocument();
        d.setTitle("T");
        d.setFullText("body");
        d.setCancellationMatrix("c");
        String h1 = d.computeSha256();
        assertThat(h1).hasSize(64).isEqualTo(d.computeSha256());   // deterministic
        d.setCancellationMatrix("c ");                               // even one space
        assertThat(d.computeSha256()).isNotEqualTo(h1);
    }

    @Test
    void fieldBoundariesAreUnambiguous() {
        // "ab"+"c" vs "a"+"bc" must not collide: the separator makes them distinct.
        LegalDocument x = new LegalDocument();
        x.setTitle("ab");
        x.setFullText("c");
        LegalDocument y = new LegalDocument();
        y.setTitle("a");
        y.setFullText("bc");
        assertThat(x.computeSha256()).isNotEqualTo(y.computeSha256());
    }

    @Test
    void onboardingValidation() throws Exception {
        var ok = json.readTree("{\"slides\":[{\"headline\":\"Hi\",\"body\":\"text\"}]}");
        SiteContentServiceTestBridge.validate(SiteContent.Key.ONBOARDING, ok);
        var noSlides = json.readTree("{\"slides\":[]}");
        assertThatThrownBy(() -> SiteContentServiceTestBridge.validate(SiteContent.Key.ONBOARDING, noSlides))
                .isInstanceOf(LegalErrors.LegalException.class);
        var blankHeadline = json.readTree("{\"slides\":[{\"headline\":\" \",\"body\":\"x\"}]}");
        assertThatThrownBy(() -> SiteContentServiceTestBridge.validate(SiteContent.Key.ONBOARDING, blankHeadline))
                .isInstanceOf(LegalErrors.LegalException.class);
    }

    @Test
    void companyProfileValidation() throws Exception {
        var missingEmail = json.readTree("{\"legalName\":\"N\",\"registrationNumber\":\"1\",\"registeredAddress\":\"KTM\",\"website\":\"w\"}");
        assertThatThrownBy(() -> SiteContentServiceTestBridge.validate(SiteContent.Key.COMPANY_PROFILE, missingEmail))
                .isInstanceOf(LegalErrors.LegalException.class);
    }
}
