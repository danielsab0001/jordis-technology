package com.jordis.jordis.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerarHashes {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String hashAdmin  = encoder.encode("admin123");
        String hashCajero = encoder.encode("cajero123");

        System.out.println("=== HASHES BCRYPT ===");
        System.out.println("admin123  -> " + hashAdmin);
        System.out.println("cajero123 -> " + hashCajero);
    }
}