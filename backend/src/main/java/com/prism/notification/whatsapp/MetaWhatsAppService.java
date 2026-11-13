package com.prism.notification.whatsapp;

import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetaWhatsAppService implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppService.class);

    private final PrismProperties properties;
    private final RestClient restClient;

    public MetaWhatsAppService(PrismProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public boolean enabled() {
        PrismProperties.Whatsapp whatsapp = properties.getWhatsapp();
        return whatsapp.isEnabled()
                && notBlank(whatsapp.getAccessToken())
                && notBlank(whatsapp.getPhoneNumberId())
                && notBlank(whatsapp.getApiVersion());
    }

    @Override
    public String sendTemplate(String phoneNumber, String templateName, List<String> bodyParameters) {
        if (!enabled()) {
            return null;
        }
        RestClientException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return post(phoneNumber, templateName, bodyParameters);
            } catch (RestClientException exception) {
                last = exception;
                log.warn("WhatsApp send attempt {} failed for template {}", attempt, templateName);
            }
        }
        log.error("WhatsApp send failed for template {}", templateName, last);
        throw ApiException.unavailable("The WhatsApp message could not be sent.");
    }

    private String post(String phoneNumber, String templateName, List<String> bodyParameters) {
        PrismProperties.Whatsapp whatsapp = properties.getWhatsapp();
        String url = trimSlash(whatsapp.getBaseUrl()) + "/" + whatsapp.getApiVersion()
                + "/" + whatsapp.getPhoneNumberId() + "/messages";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phoneNumber);
        body.put("type", "template");
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", "en"));
        if (bodyParameters != null && !bodyParameters.isEmpty()) {
            List<Map<String, String>> parameters = new ArrayList<>();
            for (String value : bodyParameters) {
                parameters.add(Map.of("type", "text", "text", value == null ? "" : value));
            }
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }
        body.put("template", template);
        Map<?, ?> response = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + whatsapp.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("messages") instanceof List<?> messages) || messages.isEmpty()) {
            return null;
        }
        Object first = messages.getFirst();
        if (first instanceof Map<?, ?> message && message.get("id") != null) {
            return message.get("id").toString();
        }
        return null;
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
