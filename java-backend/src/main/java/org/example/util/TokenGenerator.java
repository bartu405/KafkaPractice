package org.example.util;

import java.security.SecureRandom;

public class TokenGenerator {
    public static String generateToken() {
        byte[] randomBytes = new byte[16]; // 128 bits
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

// Standart Base64: Genel veri kodlaması için kullanılır.
// URL-safe Base64: Web URL'lerinde, JSON web token'larında (JWT), ya da query string içinde güvenli şekilde taşınması gereken durumlarda kullanılır.
// Padding (= işareti):
//
// Standart Base64 genelde = veya == ile padding yapar.
// URL-safe Base64 genellikle withoutPadding() ile padding kaldırılır çünkü = karakteri URL'de problem çıkarabilir.
