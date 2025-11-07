# Copilot Instructions for Payment Flow Service

## Project Overview

This is a Spring Boot microservice that manages payment processing and reconciliation for the platform. The service implements:

- **Payment initiation, status tracking, and reconciliation**
- **Integration with external payment gateways and internal services**
- **Multi-tenant data isolation** for payment records using PostgreSQL RLS
- **Worker and employer payment workflows**
- **Comprehensive audit logging** for compliance and debugging

## Technology Stack

- **Java 17** (OpenJDK)
- **Spring Boot 3.2.5** with Spring Data JPA, Spring Web, jOOQ
- **Maven** for build and dependency management
- **PostgreSQL** as the primary database (with RLS policies)
- **jOOQ** for type-safe SQL queries and SQL templates
- **Docker** for containerization
- **OpenAPI/Swagger** for API documentation

## Development Environment Setup

### Prerequisites

- Java 17 or later
- Maven 3.8+
- Docker Desktop (for PostgreSQL container)
- PostgreSQL client (psql) for database setup
- IDE with Java support (IntelliJ IDEA, Eclipse, or VS Code with Java extensions)

### Initial Setup

1. **Clone the repository** and create a feature branch
2. **Install dependencies**:
   ```bash
   mvn dependency:go-offline
   ```
3. **Build the project**:
   ```bash
   mvn clean package
   ```
4. **Set up the database** following `documentation/LBE/guides/local-environment.md`:
   - Run PostgreSQL via Docker or connect to a PostgreSQL instance
   - Execute SQL scripts for `payment_flow` schema
   - Load seed data for testing

### Environment Configuration

- Configuration files are in `src/main/resources/`
- Use `application-dev.yml` for local development
- Never commit secrets; use environment variables:
  - Database credentials via `SPRING_DATASOURCE_*` variables
  - Payment gateway API keys/secrets
  - Internal service authentication keys

### Running the Service

```bash
# Run locally with dev profile
mvn spring-boot:run

# Or specify a profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Access health endpoint
curl http://localhost:8080/actuator/health

# Access API documentation
http://localhost:8080/swagger-ui.html
```

### Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn verify

# Run specific test class
mvn test -Dtest=PaymentFlowServiceTest
```

## Code Organization

### Package Structure

```
com.example.paymentflow/
├── config/           # Spring configuration classes (JPA, jOOQ, etc.)
├── controller/       # REST API endpoints for payments and reconciliation
├── dao/              # Data Access Objects for complex queries (jOOQ-based)
├── dto/              # Data Transfer Objects (requests, responses)
├── entity/           # JPA entities (Payment, Worker, Employer, etc.)
├── repository/       # Spring Data JPA repositories
├── service/          # Business logic layer (payment processing, reconciliation)
└── util/             # Utility classes (SqlTemplateLoader, helpers)
```

### Key Components

- **PaymentService** - Handles payment initiation, status updates, gateway integration
- **ReconciliationService** - Manages reconciliation logic and matching payments
- **WorkerService** / **EmployerService** - Manages worker/employer data and workflows
- **AuditLogService** - Records audit events for compliance
- **ExternalGatewayService** - Integrates with external payment providers
- **SqlTemplateLoader** - Loads and caches SQL templates from resources

## Coding Standards

### Java Code Style

- **Follow Spring Boot conventions** and existing code patterns
- Use **constructor injection** for dependencies
- Add **JavaDoc comments** for public APIs and complex business logic
- Use **meaningful variable names** (e.g., `paymentId`, `workerId`, `employerId`, `tenantId`)
- Keep methods **focused and small** (single responsibility)
- Use **Optional** for potentially null return values
- Handle exceptions appropriately with **custom exception classes**

### REST API Design

- Follow REST principles with proper HTTP methods (GET, POST, PUT, DELETE)
- Use appropriate HTTP status codes (200, 201, 400, 403, 404, 500)
- Return consistent response structures using DTOs
- Document all endpoints with OpenAPI annotations (@Operation, @ApiResponse)
- Version APIs if making breaking changes

## Database Access Patterns ⭐ CRITICAL

**ALWAYS consult `documentation/LBE/guides/data-access-patterns.md` before writing any database code.**

### Pattern Selection for Payment Flow Service

This service uses **all three patterns** extensively:

```
┌─────────────────────────────────────────┐
│ What type of operation are you doing?  │
└─────────────┬───────────────────────────┘
              │
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
┌───────┐         ┌──────────┐
│ WRITE │         │   READ   │
└───┬───┘         └────┬─────┘
    │                  │
    │            ┌─────┴─────────────────────┐
    │            │                           │
    │            ▼                           ▼
    │    ┌──────────────┐          ┌────────────────┐
    │    │ Simple       │          │ Complex        │
    │    │ Lookup       │          │ Multi-join     │
    │    └──────┬───────┘          └────────┬───────┘
    │           │                           │
    │           │                    ┌──────┴──────┐
    │           │                    │             │
    │           │                    ▼             ▼
    │           │          ┌────────────┐  ┌──────────────┐
    │           │          │ Aggregation│  │  Dynamic     │
    │           │          │ Report     │  │  Filters     │
    │           │          └──────┬─────┘  └──────┬───────┘
    │           │                 │                │
    ▼           ▼                 ▼                ▼
┌────────┐ ┌────────┐   ┌────────────┐   ┌────────────┐
│  JPA   │ │  JPA   │   │    jOOQ    │   │    jOOQ    │
│Repository││Repository│ │ +SQL File  │   │    DSL     │
└────────┘ └────────┘   └────────────┘   └────────────┘
```

### 1. Spring Data JPA - Use for:

✅ **When to use:**
- Payment record creation and updates
- Worker/employer entity CRUD
- Status transitions requiring entity callbacks
- Any mutation on JPA entities

📁 **Examples in this service:**
- `PaymentRepository` - Payment entity persistence
- `WorkerRepository` - Worker CRUD operations
- `EmployerRepository` - Employer management
- `ReconciliationRepository` - Reconciliation record writes

💡 **Rules:**
- Use for all write operations
- Keep repository interfaces focused on persistence
- Map to DTOs before returning from controllers
- Add `@DataJpaTest` for new repository methods

### 2. jOOQ DSL - Use for:

✅ **When to use:**
- Worker/employer list endpoints with dynamic filters (pagination, search, sorting)
- Payment status queries with multiple joins
- Complex reconciliation matching logic
- Multi-table queries needing type safety

📁 **Examples in this service:**
- `WorkerQueryDao` - Complex worker queries with filters
- `EmployerQueryDao` - Employer data with pagination
- `PaymentQueryDao` - Payment status and history lookups
- Dynamic filter queries for list endpoints

💡 **Rules:**
- Inject `DSLContext` for all jOOQ operations
- Use type-safe DSL for dynamic filters
- Map results to DTOs using small mappers
- Test with Testcontainers or H2

### 3. jOOQ + SQL Templates - Use for:

✅ **When to use:**
- Aggregation queries (`worker_payment_summary`, `employer_status_distribution`)
- Reporting queries maintained by analysts
- Complex CTEs and window functions
- Queries that change frequently

📁 **File locations in this service:**
- `src/main/resources/sql/worker/worker_payment_summary.sql`
- `src/main/resources/sql/employer/employer_status_distribution.sql`
- `src/main/resources/sql/reports/payment_reconciliation_report.sql`

📁 **Loading SQL templates:**
```java
@Component
public class PaymentReportDao {
    private final DSLContext dsl;
    private final SqlTemplateLoader templateLoader;
    
    public PaymentSummary getWorkerPaymentSummary(Long workerId) {
        String sql = templateLoader.load("sql/worker/worker_payment_summary.sql");
        return dsl.resultQuery(sql, workerId).fetchOneInto(PaymentSummary.class);
    }
}
```

💡 **Rules:**
- Load templates via `SqlTemplateLoader` (already available)
- Keep column aliases stable
- Document templates in README
- Test template loading and execution
- Analysts can modify templates without Java changes

### Database Access Rules (ALL PATTERNS)

🔒 **Security & RLS:**
- **ALWAYS** set PostgreSQL session context before queries
- Use `RLSContext` or similar mechanism
- Set both `app.current_user_id` and `app.current_tenant_id`
- Never bypass RLS policies

🔄 **Transactions:**
- Use `@Transactional` for all write operations
- Consider `@Transactional(readOnly = true)` for reads
- Coordinate transactions across services carefully

✅ **Testing:**
- Test with multiple user personas (worker, employer, board)
- Verify tenant isolation
- Test dynamic filters and pagination
- Test SQL templates load and execute correctly

## Security Guidelines

### Authorization & Data Access

- Validate all user input with **Bean Validation** annotations
- Check authorization before accessing resources:
  - Consult `documentation/LBE/reference/policy-matrix.md` for required policies
  - Use appropriate `@PreAuthorize` annotations
- **Never log sensitive data** (payment details, API keys, personal information)
- Implement **CORS** configuration properly for production

### RLS & Multi-Tenancy

- **Always** set tenant context before queries
- Test multi-tenancy isolation thoroughly
- Always include `tenantId` in audit logs
- Follow patterns in `documentation/LBE/foundations/data-guardrails-101.md`

## Building and Testing

### Build Commands

```bash
# Clean build
mvn clean install

# Build without tests (use sparingly)
mvn clean install -DskipTests

# Build Docker image
docker build -t payment-flow-service:latest .

# Package for deployment
mvn clean package spring-boot:repackage

# Run jOOQ codegen (if configured)
mvn clean generate-sources
```

### Running Tests

- Use Testcontainers or H2 for database interactions
- Mock external dependencies (payment gateways) using **Mockito**
- Write tests for:
  - Service layer business logic (payment processing, reconciliation)
  - jOOQ queries (integration tests)
  - API endpoints (use MockMvc)
  - SQL template loading and execution
  - RLS isolation

## Common Tasks

### Adding a New API Endpoint (e.g., GET /api/payments/by-worker/{workerId})

**Step 1: Consult Documentation**
- Read `documentation/LBE/reference/raw/RBAC/MAPPINGS/PHASE5_ENDPOINT_POLICY_MAPPINGS.md` (sections 13-16)
- Check payment workflow policies in `documentation/LBE/reference/policy-matrix.md`
- Review `documentation/LBE/guides/data-access-patterns.md`

**Step 2: Determine Data Access Pattern**
1. Simple payment lookup by ID? → Use JPA Repository
2. List with filters (status, date range, pagination)? → Use jOOQ DSL
3. Aggregation or analyst-maintained report? → Use jOOQ + SQL Template

**Step 3: Implement**
1. Create DTO classes in `dto/` package
2. Create appropriate DAO/Repository
3. Implement service layer business logic
4. Add controller method with OpenAPI annotations
5. Add authorization checks
6. Ensure RLS context is set

**Step 4: Register in Auth Catalog** (via auth-service)
1. Create migration to register endpoint in `auth.endpoints`
2. Link to policies via `auth.endpoint_policies`
3. Update `documentation/LBE/reference/raw/RBAC/MAPPINGS/PHASE5_ENDPOINT_POLICY_MAPPINGS.md`

**Step 5: Test & Document**
1. Write unit tests for business logic
2. Write integration tests for database queries
3. Test with worker/employer/board personas
4. Test tenant isolation
5. Update `documentation/LBE/reference/recent-updates.md`

### Adding a New Payment Type or Reconciliation Rule

1. Create SQL migration with new type/rule definitions
2. Update entity classes if needed
3. Implement business logic in service layer
4. Add tests for new scenarios
5. Document in relevant guides
6. Update `documentation/LBE/reference/TABLE_NAMES_REFERENCE.md`

### Adding a New SQL Template (e.g., Employer Payment Summary)

1. Create SQL file: `src/main/resources/sql/employer/payment_summary.sql`
2. Use named parameters or positional placeholders
3. Keep column aliases stable and documented
4. Create DAO method to load and execute:
   ```java
   public EmployerSummary getPaymentSummary(Long employerId) {
       String sql = templateLoader.load("sql/employer/payment_summary.sql");
       return dsl.resultQuery(sql, employerId).fetchOneInto(EmployerSummary.class);
   }
   ```
5. Write integration test for template
6. Document template in README with parameter descriptions

### Debugging Payment/Reconciliation Issues

1. Check payment status and gateway responses
2. Verify reconciliation records and matching logic
3. Review audit logs for transaction history
4. Check RLS context: `SELECT current_setting('app.current_user_id')`
5. Consult `documentation/LBE/playbooks/troubleshoot-auth.md`
6. Review payment gateway logs if external issue

## Important Considerations

### Multi-Tenancy and Data Isolation

- Tenant isolation enforced at database level via RLS
- Always include `tenantId` in audit logs and queries
- Never bypass tenant checks in application code
- Test isolation between different employers/workers

### Performance

- Use pagination for all list endpoints
- Consider caching for frequently accessed payment/reconciliation data
- Monitor database connection pool usage
- Use database indexes appropriately (especially for payment lookups)
- Profile SQL templates for optimization

### Migrations and Schema Changes

- PostgreSQL is the primary database
- Schema changes via SQL migration scripts
- Test migrations on copy of production data
- Keep `ddl-auto: update` for development only
- Document in `documentation/LBE/reference/TABLE_NAMES_REFERENCE.md`

## Additional Resources

- Spring Boot: https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/
- jOOQ: https://www.jooq.org/doc/latest/manual/
- PostgreSQL: https://www.postgresql.org/docs/current/
- Payment Gateway API docs (as relevant)

---

# Payment Flow Service — Documentation Reference 📚

The canonical product, RBAC, and data docs live in the shared documentation project (`documentation/LBE`). **Always consult this documentation before implementing features or making changes**.

## Essential Reading (Start Here) 🎯

### Platform & Security Baseline
1. **`documentation/LBE/README.md`** – Guided path through Auth + RLS journey
   - Follow Steps 1–3 before touching payment endpoints
   - Ensures JWT/RLS assumptions stay aligned

2. **`documentation/LBE/architecture/overview.md`** – How payment data sits behind auth service
3. **`documentation/LBE/architecture/data-map.md`** – Table relationships including payment_flow schema
4. **`documentation/LBE/guides/login-to-data.md`** – Worker, employer, board personas: login → policy → RLS

### Payment Flow Architecture
- **`documentation/LBE/architecture/request-lifecycle.md`** – Request flow including payment operations
- **`documentation/LBE/architecture/policy-binding.md`** – Permission interconnections for payment workflows
- **`documentation/LBE/architecture/audit-design.md`** – Payment Flow section: audit logging requirements

## Implementation Guides (Use While Coding) 💻

### Data Access Patterns ⭐ CRITICAL ⭐
- **`documentation/LBE/guides/data-access-patterns.md`** – **Read before writing ANY database code**
  - Payment Flow service examples showing all three patterns
  - When to use JPA vs jOOQ DSL vs jOOQ + SQL templates
  - Migration guidance between patterns

### Security & Authorization
- **`documentation/LBE/foundations/access-control-101.md`** – RBAC fundamentals
- **`documentation/LBE/foundations/data-guardrails-101.md`** – RLS primer for payment data
- **`documentation/LBE/guides/integrate-your-service.md`** – Connecting to auth service
- **`documentation/LBE/guides/verify-permissions.md`** – Testing payment workflow permissions

### Setup & Local Development
- **`documentation/LBE/guides/local-environment.md`** – Local setup instructions
- **`documentation/LBE/guides/setup/rbac.md`** – RBAC setup for payment endpoints
- **`documentation/LBE/guides/setup/vpd.md`** – RLS setup for payment data

## Quick Reference (Use During Development) 📖

### Payment Flow Specific References

#### Endpoint Mappings
- **`documentation/LBE/reference/raw/RBAC/MAPPINGS/PHASE5_ENDPOINT_POLICY_MAPPINGS.md`**
  - **Section 13** – Worker upload endpoints and policies
  - **Section 14** – Payment record endpoints
  - **Section 15** – Receipt endpoints
  - **Section 16** – Employer validation endpoints
  - Update these sections when adding/modifying payment endpoints

#### Endpoint Categorization
- **`documentation/LBE/reference/raw/RBAC/MAPPINGS/PHASE1_ENDPOINTS_EXTRACTION.md`**
  - Endpoint category counts for worker/employer/board workflows
  - Seeds onboarding scripts and regression matrices

#### Capability Mappings
- **`documentation/LBE/reference/raw/RBAC/MAPPINGS/PHASE4_POLICY_CAPABILITY_MAPPINGS.md`**
  - Payment Management/Request capability coverage per role
  - Update when new capabilities or UI actions added

#### Role Narratives
- **`documentation/LBE/reference/raw/ONBOARDING_ROLES.md`** – Role descriptions with payment workflow context
- **`documentation/LBE/reference/raw/RBAC/ROLES.md`** – Which payment flow screens/actions each persona owns

### General References
- **`documentation/LBE/reference/role-catalog.md`** – All roles (worker, employer, board, admin)
- **`documentation/LBE/reference/policy-matrix.md`** – Policy mappings for payment operations
- **`documentation/LBE/reference/TABLE_NAMES_REFERENCE.md`** – Canonical `payment_flow` schema
- **`documentation/LBE/reference/audit-quick-reference.md`** – Audit requirements for payment service
- **`documentation/LBE/reference/recent-updates.md`** – November 2025 audit tagging + entity auditing changes

## Troubleshooting & Operations 🔧

### Problem Resolution
- **`documentation/LBE/playbooks/troubleshoot-auth.md`** – Auth/authorization troubleshooting
  - JWT validation issues affecting payment access
  - RLS context problems
  - Policy resolution failures

### Operational References
- **`documentation/LBE/reference/postgres-operations.md`** – PostgreSQL operations for payment_flow schema
- **`documentation/LBE/foundations/postgres-for-auth.md`** – Database role management

## Maintenance Checklist ✅

### When Adding/Modifying Payment Endpoints
1. ✅ Determine data access pattern from `documentation/LBE/guides/data-access-patterns.md`
2. ✅ Implement with appropriate pattern (JPA/jOOQ DSL/jOOQ+SQL)
3. ✅ Define endpoint with OpenAPI annotations
4. ✅ Add authorization checks (consult policy-matrix.md)
5. ✅ Ensure RLS context is set
6. ✅ Register in auth-service: `auth.endpoints` + `auth.endpoint_policies`
7. ✅ Update sections 13–16 in `PHASE5_ENDPOINT_POLICY_MAPPINGS.md`
8. ✅ Update `documentation/LBE/reference/policy-matrix.md`
9. ✅ Test with worker/employer/board personas
10. ✅ Test tenant isolation
11. ✅ Document in `documentation/LBE/reference/recent-updates.md`

### When Changing Payment Schema
1. ✅ Write migration script
2. ✅ Update `documentation/LBE/reference/TABLE_NAMES_REFERENCE.md`
3. ✅ Update `documentation/LBE/architecture/data-map.md` if relationships change
4. ✅ Test RLS policies still work correctly
5. ✅ Update any affected SQL templates
6. ✅ Document in `documentation/LBE/reference/recent-updates.md`

### When Modifying Audit/Logging
1. ✅ Confirm config matches `documentation/LBE/reference/audit-quick-reference.md`
2. ✅ Ensure `service_name` = `payment-flow-service` and `source_schema` = `payment_flow`
3. ✅ Update Payment Flow subsection in `documentation/LBE/architecture/audit-design.md`
4. ✅ Verify compliance requirements still met

### When Adding SQL Templates
1. ✅ Create template in `src/main/resources/sql/<domain>/`
2. ✅ Use stable column aliases
3. ✅ Document parameters and expected results
4. ✅ Add integration test for template
5. ✅ Update README with template location and purpose
6. ✅ Note in `documentation/LBE/reference/recent-updates.md` if analyst-facing

### Major Releases
1. ✅ Capture summary in `documentation/LBE/reference/recent-updates.md`
2. ✅ Update any changed endpoint mappings
3. ✅ Review and update affected guides
4. ✅ Notify other services if payment APIs changed

## Key Principles 🎯

### Security First 🔒
- ✅ Always set RLS context before queries
- ✅ Validate payment authorization with policies
- ✅ Never bypass tenant checks
- ✅ Never log sensitive payment data
- ✅ Follow `documentation/LBE/foundations/data-guardrails-101.md`

### Data Access Pattern Discipline 💾
- ✅ **Always** consult `documentation/LBE/guides/data-access-patterns.md` first
- ✅ Use JPA for writes and simple reads
- ✅ Use jOOQ DSL for complex queries with dynamic filters
- ✅ Use jOOQ + SQL templates for analyst-maintained reports
- ✅ Test all patterns thoroughly

### Documentation Driven 📝
- ✅ Read relevant docs BEFORE coding
- ✅ Update docs WITH your code changes
- ✅ Keep endpoint mappings current
- ✅ Document SQL templates clearly

### Test Comprehensively 🧪
- ✅ Test with worker, employer, board personas
- ✅ Test tenant isolation
- ✅ Test authorization (RBAC)
- ✅ Test SQL templates load correctly
- ✅ Follow `documentation/LBE/guides/verify-permissions.md`

## Quick Links by Task 🔗

| Task | Primary Documentation |
|------|----------------------|
| Setting up local environment | `documentation/LBE/guides/local-environment.md` |
| Understanding payment architecture | `documentation/LBE/architecture/overview.md` |
| **Choosing data access pattern** | **`documentation/LBE/guides/data-access-patterns.md`** ⭐ |
| Finding payment endpoint policies | `documentation/LBE/reference/raw/RBAC/MAPPINGS/PHASE5_ENDPOINT_POLICY_MAPPINGS.md` (§13-16) |
| Adding new payment endpoint | `documentation/LBE/guides/extend-access.md` |
| Understanding payment roles | `documentation/LBE/reference/role-catalog.md` |
| Debugging authorization | `documentation/LBE/playbooks/troubleshoot-auth.md` |
| Understanding RLS for payments | `documentation/LBE/foundations/data-guardrails-101.md` |
| Payment schema reference | `documentation/LBE/reference/TABLE_NAMES_REFERENCE.md` |
| Checking recent changes | `documentation/LBE/reference/recent-updates.md` |

---

**Remember**: The documentation in `documentation/LBE/` is the single source of truth. Always consult it before making changes, and update it along with your code changes. Payment Flow service uses all three data access patterns—choose wisely!
