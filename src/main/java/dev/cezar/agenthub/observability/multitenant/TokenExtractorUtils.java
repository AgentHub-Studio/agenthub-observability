package dev.cezar.agenthub.observability.multitenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

@Log
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TokenExtractorUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern REALM_PATTERN = Pattern.compile("/realms/([^/]+)");

    public static String getTenantIdFromToken(String token) {
        try {
            var jsonNode = parseJwtPayload(token);
            var iss = jsonNode.path("iss").asText();
            var matcher = REALM_PATTERN.matcher(iss);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.info("Erro ao extrair tenantId do JWT: " + e.getMessage());
        }
        return null;
    }

    public static String getUserIdFromToken(String token) {
        try {
            var jsonNode = parseJwtPayload(token);
            // Keycloak JWT: subject (sub) contains the userId
            return jsonNode.path("sub").asText(null);
        } catch (Exception e) {
            log.info("Erro ao extrair userId do JWT: " + e.getMessage());
        }
        return null;
    }

    private static com.fasterxml.jackson.databind.JsonNode parseJwtPayload(String token) throws Exception {
        var parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Token JWT inválido");
        }
        var payloadBase64Url = parts[1];
        var payloadBytes = Base64.getUrlDecoder().decode(payloadBase64Url);
        var payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
        return OBJECT_MAPPER.readTree(payloadJson);
    }
}
