package com.prism.notification.whatsapp;

import java.util.List;

public interface WhatsAppService {

    boolean enabled();

    /**
     * Sends a template message. Returns the provider message id.
     * Returns null when WhatsApp is disabled. Throws when a configured send fails.
     */
    String sendTemplate(String phoneNumber, String templateName, List<String> bodyParameters);
}
