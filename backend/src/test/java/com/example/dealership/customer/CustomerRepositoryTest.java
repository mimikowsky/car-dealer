package com.example.dealership.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class CustomerRepositoryTest {

    private static final String EMAIL = "ewa.zielinska@example.com";
    private static final String OTHER_EMAIL = "marek.lewandowski@example.com";

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void existsByEmailIgnoreCaseAndIdNotFindsEmailOfAnotherCustomer() {
        Customer first = customerRepository.save(
                new Customer("Ewa", "Zielińska", EMAIL, "600 100 200", null));
        Customer second = customerRepository.save(
                new Customer("Marek", "Lewandowski", OTHER_EMAIL, "600 100 300", "1234567890"));
        entityManager.flush();

        assertThat(customerRepository.existsByEmailIgnoreCaseAndIdNot(EMAIL, second.getId())).isTrue();
        assertThat(customerRepository.existsByEmailIgnoreCaseAndIdNot("Ewa.Zielinska@EXAMPLE.com", second.getId()))
                .isTrue();
        assertThat(customerRepository.existsByEmailIgnoreCaseAndIdNot(EMAIL, first.getId())).isFalse();
        assertThat(customerRepository.existsByEmailIgnoreCase("EWA.ZIELINSKA@example.com")).isTrue();
        assertThat(customerRepository.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    void saveStoresCustomerThatCanBeReadBack() {
        Customer saved = customerRepository.save(
                new Customer("Ewa", "Zielińska", EMAIL, "+48 600 100 200", "1234567890"));
        entityManager.flush();
        entityManager.clear();

        Customer found = customerRepository.findById(saved.getId()).orElseThrow();

        assertThat(found).isNotSameAs(saved);
        assertThat(found.getFirstName()).isEqualTo("Ewa");
        assertThat(found.getLastName()).isEqualTo("Zielińska");
        assertThat(found.getEmail()).isEqualTo(EMAIL);
        assertThat(found.getPhone()).isEqualTo("+48 600 100 200");
        assertThat(found.getNip()).isEqualTo("1234567890");
        assertThat(customerRepository.existsByEmailIgnoreCase(EMAIL)).isTrue();
        assertThat(customerRepository.existsByEmailIgnoreCaseAndIdNot(EMAIL, saved.getId())).isFalse();
    }
}
