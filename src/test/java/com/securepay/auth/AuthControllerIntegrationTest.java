package com.securepay.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.securepay.SecurePayApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SecurePayApplication.class)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersCustomerAndLogsInWithJwt() throws Exception {
        String username = "auth-" + UUID.randomUUID();
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"correct horse battery staple","role":"ADMIN"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"correct horse battery staple"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejectsWrongPasswordAndDuplicateRegistration() throws Exception {
        String username = "auth-" + UUID.randomUUID();
        String registration = """
                {"username":"%s","password":"correct horse battery staple"}
                """.formatted(username);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong password"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesRegistrationInput() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"invalid","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
