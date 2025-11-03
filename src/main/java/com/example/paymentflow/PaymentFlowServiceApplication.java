package com.example.paymentflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.shared.security.EnableSharedSecurity;


@SpringBootApplication
@EnableAspectJAutoProxy
@EnableSharedSecurity
@ComponentScan(basePackages = {
    "com.example.paymentflow",
    "com.shared"  // Scan shared-lib components including RLS
})
public class PaymentFlowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentFlowServiceApplication.class, args);
    }
}
