package com.re.smart_cinema_booking_system.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility one-off class to generate BCrypt hash for seed data.
 * Run this main() method once, copy the hash, then delete this file.
 */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        String password = "123456";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        System.out.println("=== BCrypt Hash Generator ===");
        System.out.println("Password: " + password);
        System.out.println("Hash:     " + hash);
        System.out.println("Verify:   " + BCrypt.checkpw(password, hash));
    }
}
