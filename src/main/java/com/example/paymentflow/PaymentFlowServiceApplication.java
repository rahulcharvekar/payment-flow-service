package com.example.paymentflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.shared.security.EnableSharedSecurity;


@SpringBootApplication
@EnableAspectJAutoProxy
@EnableSharedSecurity
public class PaymentFlowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentFlowServiceApplication.class, args);
    }
}
