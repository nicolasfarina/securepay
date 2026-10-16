package com.securepay.payment;

import com.jayway.jsonpath.JsonPath;
import com.securepay.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in: only run against a disposable PostgreSQL database named securepay_test. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SECUREPAY_POSTGRES_TEST", matches = "true")
class PaymentIdempotencyPostgresTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentService paymentService;
    @MockitoSpyBean private IdempotencyRecordRepository records;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        PostgresTestDatabase.configure(properties);
    }

    @BeforeEach
    void clearDatabase() {
        records.deleteAll();
        payments.deleteAll();
    }

    @AfterEach
    void resetRepository() {
        reset(records);
    }

    @Test
    void eightConcurrentEquivalentRequestsReturnOnePaymentWithoutOrphans() throws Exception {
        String authorization = bearer("customer-123");
        forceInitialLookupRace(8);
        var requests = new ArrayList<Callable<String>>();
        for (int i = 0; i < 8; i++) {
            requests.add(() -> create(authorization, "concurrent-key", "10.00", 201));
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            var responses = executor.invokeAll(requests, 45, TimeUnit.SECONDS);
            var ids = new ArrayList<String>();
            for (var response : responses) ids.add(response.get());
            assertThat(ids).hasSize(8).containsOnly(ids.getFirst());
            assertThat(payments.count()).isEqualTo(1);
            assertThat(records.count()).isEqualTo(1);
        }
    }

    @Test
    void concurrentDifferentPayloadsReturnOneCreatedAndOneConflict() throws Exception {
        String authorization = bearer("customer-123");
        forceInitialLookupRace(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(java.util.List.of(
                    (Callable<Integer>) () -> createStatus(authorization, "conflict-key", "10.00"),
                    () -> createStatus(authorization, "conflict-key", "20.00")), 45, TimeUnit.SECONDS);
            assertThat(java.util.List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(payments.count()).isEqualTo(1);
            assertThat(records.count()).isEqualTo(1);
        }
    }

    @Test
    void sequentialRetryNormalizesAmountAndRejectsChangedPayload() throws Exception {
        String authorization = bearer("customer-123");
        String original = create(authorization, "retry-key", "10.00", 201);
        assertThat(create(authorization, "retry-key", "10.0", 201)).isEqualTo(original);
        assertThat(createStatus(authorization, "retry-key", "20.00")).isEqualTo(409);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(records.count()).isEqualTo(1);
    }

    @Test
    void differentCustomersCanUseTheSameKey() throws Exception {
        String first = create(bearer("customer-123"), "shared-key", "10.00", 201);
        String second = create(bearer("customer-789"), "shared-key", "10.00", 201);
        assertThat(second).isNotEqualTo(first);
        assertThat(payments.count()).isEqualTo(2);
        assertThat(records.count()).isEqualTo(2);
    }

    @Test
    void unrelatedIntegrityFailureIsRethrownAndLeavesNoPayment() {
        var invalid = new CreatePaymentRequest("m".repeat(129), new java.math.BigDecimal("10.00"), "USD");
        assertThatThrownBy(() -> paymentService.create(invalid, "customer-123", "invalid-merchant"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.count()).isZero();
        assertThat(records.count()).isZero();
    }

    private void forceInitialLookupRace(int participants) {
        CyclicBarrier barrier = new CyclicBarrier(participants);
        var repositoryAnswer = mockingDetails(records).getMockCreationSettings().getDefaultAnswer();
        // Every contender observes an absent key before any contender can insert.
        // Recovery lookups see a committed record and do not re-enter the barrier.
        doAnswer(invocation -> {
            Optional<?> result = (Optional<?>) repositoryAnswer.answer(invocation);
            if (result.isEmpty()) barrier.await(15, TimeUnit.SECONDS);
            return result;
        }).when(records).findByCustomerIdAndIdempotencyKey(anyString(), anyString());
    }

    private String bearer(String username) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"local-development-only\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String authorization, String key, String amount) {
        return post("/api/payments").header("Authorization", authorization)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchantId\":\"merchant-456\",\"amount\":" + amount + ",\"currency\":\"USD\"}");
    }

    private String create(String authorization, String key, String amount, int expectedStatus) throws Exception {
        String response = mockMvc.perform(request(authorization, key, amount))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private int createStatus(String authorization, String key, String amount) throws Exception {
        return mockMvc.perform(request(authorization, key, amount)).andReturn().getResponse().getStatus();
    }
}
