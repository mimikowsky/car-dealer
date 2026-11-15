package com.example.dealership.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Date rules of the request with a fixed "today" (INTENT A5, Z5, Z12). English messages are asserted in
// DocumentControllerTest; a plain Validator uses the JVM locale.
class DocumentRequestValidationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        factory = Validation.byDefaultProvider().configure().clockProvider(() -> clock).buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void issueDateTodayIsValid() {
        assertThat(validate(TODAY, null)).isEmpty();
    }

    @Test
    void issueDateTomorrowIsRejected() {
        assertThat(validate(TODAY.plusDays(1), null))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("issueDate");
    }

    @Test
    void expiryDateEqualToIssueDateIsValid() {
        assertThat(validate(TODAY, TODAY)).isEmpty();
    }

    @Test
    void expiryDateInThePastIsValid() {
        assertThat(validate(TODAY.minusYears(2), TODAY.minusYears(1))).isEmpty();
    }

    @Test
    void expiryDateBeforeIssueDateIsRejectedOnExpiryDate() {
        assertThat(validate(TODAY, TODAY.minusDays(1)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("expiryDate");
    }

    private Set<ConstraintViolation<DocumentRequest>> validate(LocalDate issueDate, LocalDate expiryDate) {
        return validator.validate(new DocumentRequest(DocumentType.OC_POLICY, "OC-1", issueDate, expiryDate));
    }
}
