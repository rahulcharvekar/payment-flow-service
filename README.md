# Payment Flow Service

Spring Boot microservice for payment processing, status tracking, worker/employer workflows, and external gateway integration for the platform.

**Stack:** Java 17 | Spring Boot 3.2.5 | PostgreSQL | jOOQ | JWT

## Features

- Payment initiation and status tracking
- Worker and employer workflow management
- External payment gateway integration
- Row-level security (RLS) for multi-tenancy
- Audit logging (API & entity level)

## Key Docs

- See `documentation/LBE/README.md` for system overview
- See `copilot-instructions.md` for coding standards and audit rules

## Build & Run

- `mvn clean install` to build
- `docker build -t payment-flow-service:latest .` to build Docker image

## Folder Structure

- `src/main/java/com.example.paymentflow/` — code
- `src/main/resources/` — configs
