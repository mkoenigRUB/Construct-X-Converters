package org.constructx.converter.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Construct-X converter web service.
 *
 * <p>Exposes the converter library (core module) as a REST API and serves a
 * static frontend for interactive use.</p>
 */
@SpringBootApplication
public class ConverterWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConverterWebApplication.class, args);
    }
}
