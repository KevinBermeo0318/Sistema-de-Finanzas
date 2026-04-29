package com.example.finance;

import com.example.finance.dto.TransactionDto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FinanceIntegrationTest {
    @LocalServerPort
    int port;

    TestRestTemplate rest = new TestRestTemplate();

    @Test
    public void smoke() {
        String base = "http://localhost:" + port + "/api/transactions";
        TransactionDto dto = new TransactionDto();
        dto.setAmount(10.0);
        dto.setType("expense");
        dto.setCategory("comida");
        dto.setDescription("test");
        dto.setDate(LocalDate.now());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // use basic auth
        headers.setBasicAuth("user", "password");
        HttpEntity<TransactionDto> req = new HttpEntity<>(dto, headers);
        ResponseEntity<String> res = rest.postForEntity(base, req, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
