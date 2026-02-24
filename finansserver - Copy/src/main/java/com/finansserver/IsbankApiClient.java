package com.finansserver;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class IsbankApiClient {
    
    private static final String BASE_URL = "https://api.test.isbank.com.tr/api/sandbox-isbank/v1";
    
    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        
        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Isbank-Client-Id", "your-client-id");
        headers.set("X-Isbank-Client-Secret", "your-client-secret");
        headers.set("Authorization", "Bearer your-access-token");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Parameters
        String accountId = "879070";
        String fromDate = "24-06-2024";
        String toDate = "24-06-2025";
        String minAmount = "0";
        String maxAmount = "1000000";
        
        // URL
        String url = BASE_URL + "/accounts/" + accountId + "/transactions" +
                    "?from=" + fromDate + 
                    "&to=" + toDate + 
                    "&min=" + minAmount + 
                    "&max=" + maxAmount;
        
        // Request
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, String.class);
        
        System.out.println(response.getBody());
    }
}