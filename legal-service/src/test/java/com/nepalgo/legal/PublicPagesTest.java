package com.nepalgo.legal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// The public legal pages are rendered from plain text; these pin down the layout
// rules and, above all, that admin-authored text can never inject HTML/script.
class PublicPagesTest {

    @Test
    void headingsParagraphsAndBullets() {
        String html = PublicPages.render("NEPALGO — PRIVACY POLICY\n\nIntro text.\n\n1. INFORMATION WE COLLECT\n- Name\n- E-mail\nAfter list.");
        assertThat(html).isEqualTo("<h1>NEPALGO — PRIVACY POLICY</h1><p>Intro text.</p><h2>1. INFORMATION WE COLLECT</h2>"
                + "<ul><li>Name</li><li>E-mail</li></ul><p>After list.</p>");
    }

    @Test
    void textIsEscaped_noScriptInjection() {
        String html = PublicPages.render("Hello <script>alert('x')</script> & \"quotes\"");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp;");
    }

    @Test
    void numberedHeadingRunningIntoItsFirstSentenceIsSplit() {
        assertThat(PublicPages.render("2. HOW WE USE THIS INFORMATION To create and maintain your account."))
                .isEqualTo("<h2>2. HOW WE USE THIS INFORMATION</h2><p>To create and maintain your account.</p>");
        assertThat(PublicPages.render("Intro.\n\n7. CHILDREN The Service is not for children."))
                .endsWith("<h2>7. CHILDREN</h2><p>The Service is not for children.</p>");
        // Terms style: heading ends with a full stop
        assertThat(PublicPages.render("1. ELIGIBILITY. You must be at least 18."))
                .isEqualTo("<h2>1. ELIGIBILITY.</h2><p>You must be at least 18.</p>");
        // a normal sentence that merely starts with a number is NOT a heading
        assertThat(PublicPages.render("Intro.\n\n3. We may share data with hotels."))
                .endsWith("<p>3. We may share data with hotels.</p>");
    }

    @Test
    void headingDetection() {
        assertThat(PublicPages.isHeading("4. HOW WE STORE AND PROTECT IT")).isTrue();
        assertThat(PublicPages.isHeading("We store data in Nepal.")).isFalse();
        assertThat(PublicPages.isHeading("1.")).isFalse();
    }
}
