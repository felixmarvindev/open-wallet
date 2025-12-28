# Missing and Deferred Features

## Overview

This document catalogs features that are missing, deferred, or considered post-MVP. It serves as a reference for future development and helps prioritize enhancements.

## Missing Features by Service

### Auth Service

#### ❌ Not Implemented
1. **Password Reset**
   - Forgot password endpoint
   - Password reset token generation
   - Password reset email
   - Password reset completion
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Email service

2. **Email Verification**
   - Email verification on registration
   - Verification token management
   - Resend verification email
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: Email service

3. **Two-Factor Authentication (2FA)**
   - TOTP generation
   - 2FA verification
   - Backup codes
   - **Priority**: Low
   - **Complexity**: High
   - **Dependencies**: TOTP library

4. **Account Lockout**
   - Failed login attempt tracking
   - Account lockout after N failures
   - Lockout duration and unlock
   - **Priority**: Medium
   - **Complexity**: Low
   - **Dependencies**: None

5. **Session Management**
   - Active session tracking
   - Session revocation
   - Multiple device management
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: Session storage

6. **Token Revocation**
   - Refresh token blacklist
   - Token revocation endpoint
   - Logout from all devices
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Token storage

---

### Customer Service

#### ❌ Not Implemented
1. **Customer Deletion/Deactivation**
   - Soft delete customer records
   - Deactivation workflow
   - Data retention policies
   - **Priority**: Medium
   - **Complexity**: Low
   - **Dependencies**: None

2. **Address Management**
   - Multiple addresses per customer
   - Address validation
   - Primary address designation
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: Address validation service

3. **Document Management**
   - Document upload endpoints
   - Document storage (S3/local)
   - Document verification details
   - Document expiration tracking
   - **Priority**: Medium
   - **Complexity**: High
   - **Dependencies**: File storage service

4. **KYC Enhancements**
   - KYC retry mechanism
   - KYC history (multiple attempts)
   - KYC document type validation
   - KYC provider integration (real provider)
   - **Priority**: Medium
   - **Complexity**: High
   - **Dependencies**: KYC provider API

5. **Customer Search**
   - Admin search endpoint
   - Search by name, email, phone
   - Pagination and filtering
   - **Priority**: Low
   - **Complexity**: Low
   - **Dependencies**: None

6. **Customer Analytics**
   - Customer activity tracking
   - Profile completion metrics
   - KYC conversion rates
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: Analytics service

---

### Wallet Service

#### ❌ Not Implemented
1. **Wallet Lifecycle Management**
   - Wallet suspension (`PUT /api/v1/wallets/{id}/suspend`)
   - Wallet activation (`PUT /api/v1/wallets/{id}/activate`)
   - Wallet closure (`PUT /api/v1/wallets/{id}/close`)
   - Wallet deletion (soft delete)
   - **Priority**: High
   - **Complexity**: Low
   - **Dependencies**: None

2. **Limit Management**
   - Update wallet limits (`PUT /api/v1/wallets/{id}/limits`)
   - Limit history tracking
   - Limit change approval workflow
   - **Priority**: Medium
   - **Complexity**: Low
   - **Dependencies**: None

3. **Transaction History**
   - Get wallet transactions (`GET /api/v1/wallets/{id}/transactions`)
   - Transaction filtering (by date, type, status)
   - Transaction pagination
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Ledger Service

4. **Balance Operations**
   - Balance freeze/unfreeze
   - Balance hold/release
   - Balance adjustment (admin)
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: None

5. **Multi-Currency Support**
   - Currently KES-only (MVP restriction)
   - Currency conversion
   - Multi-currency wallet support
   - **Priority**: Low
   - **Complexity**: High
   - **Dependencies**: Currency conversion service

6. **Wallet Analytics**
   - Balance history tracking
   - Transaction statistics
   - Usage metrics
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: Analytics service

---

### Ledger Service

#### ❌ Not Implemented
1. **Transaction History/Listing**
   - Get transactions by wallet (`GET /api/v1/transactions?walletId={id}`)
   - Get transactions by date range
   - Get transactions by status
   - Transaction pagination and filtering
   - Transaction search
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: None

2. **Transaction Cancellation**
   - Cancel pending transaction (`POST /api/v1/transactions/{id}/cancel`)
   - Cancellation workflow
   - Refund processing
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: None

3. **Transaction Reversal/Refund**
   - Reverse completed transaction
   - Refund processing
   - Reversal ledger entries
   - **Priority**: Medium
   - **Complexity**: High
   - **Dependencies**: None

4. **Bulk Transactions**
   - Batch transaction creation
   - Bulk processing
   - Batch validation
   - **Priority**: Low
   - **Complexity**: High
   - **Dependencies**: None

5. **Transaction Fees**
   - Fee calculation
   - Fee ledger entries
   - Fee configuration
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: None

6. **Pending Transaction Management**
   - Timeout handling for pending transactions
   - Automatic cleanup of stale pending transactions
   - Pending transaction status updates
   - **Priority**: Low
   - **Complexity**: Low
   - **Dependencies**: Scheduled jobs

7. **Transaction Export**
   - Export transactions to CSV/Excel
   - Transaction reports
   - Audit trail export
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: None

8. **Advanced Validation**
   - Minimum transaction amount
   - Maximum transaction amount
   - Transaction velocity checks
   - Suspicious transaction detection
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Fraud detection service

---

## Cross-Service Missing Features

### Event-Driven Architecture

#### ❌ Not Implemented
1. **Dead Letter Queue (DLQ)**
   - Failed event handling
   - DLQ topic configuration
   - DLQ monitoring and alerting
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Kafka configuration

2. **Event Replay**
   - Event replay mechanism
   - Event replay API
   - Event replay validation
   - **Priority**: Medium
   - **Complexity**: High
   - **Dependencies**: Event store

3. **Event Versioning**
   - Event schema versioning
   - Backward compatibility
   - Schema evolution
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Schema registry

4. **Event Monitoring**
   - Event publishing metrics
   - Event consumption metrics
   - Event lag monitoring
   - Event processing time
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Metrics service

---

### Security and Compliance

#### ❌ Not Implemented
1. **Rate Limiting**
   - API rate limiting
   - Per-user rate limits
   - Rate limit configuration
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Rate limiting library

2. **API Key Management**
   - API key generation
   - API key rotation
   - API key revocation
   - **Priority**: Low
   - **Complexity**: Medium
   - **Dependencies**: None

3. **IP Whitelisting**
   - IP whitelist configuration
   - IP-based access control
   - **Priority**: Low
   - **Complexity**: Low
   - **Dependencies**: None

4. **Audit Logging**
   - Comprehensive audit logs
   - Who did what, when
   - Audit log storage
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Logging service

5. **Security Headers**
   - CSP, HSTS, X-Frame-Options
   - Security header configuration
   - **Priority**: Medium
   - **Complexity**: Low
   - **Dependencies**: None

---

### Observability and Monitoring

#### ❌ Not Implemented
1. **Metrics Collection**
   - Prometheus metrics
   - Custom business metrics
   - Metrics aggregation
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Prometheus

2. **Distributed Tracing**
   - Jaeger/Zipkin integration
   - Request tracing across services
   - Trace correlation
   - **Priority**: Medium
   - **Complexity**: High
   - **Dependencies**: Tracing service

3. **Alerting**
   - Alert configuration
   - Alert notifications
   - Alert escalation
   - **Priority**: High
   - **Complexity**: Medium
   - **Dependencies**: Alerting service

4. **Dashboard**
   - Grafana dashboards
   - Service health dashboards
   - Business metrics dashboards
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Grafana

5. **Log Aggregation**
   - ELK stack integration
   - Centralized logging
   - Log search and analysis
   - **Priority**: Medium
   - **Complexity**: High
   - **Dependencies**: ELK stack

---

### Testing

#### ❌ Not Implemented
1. **Performance Testing**
   - Load testing
   - Stress testing
   - Performance benchmarks
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Load testing tools

2. **Security Testing**
   - Penetration testing
   - Security vulnerability scanning
   - OWASP Top 10 testing
   - **Priority**: High
   - **Complexity**: High
   - **Dependencies**: Security testing tools

3. **Chaos Engineering**
   - Service failure scenarios
   - Network partition scenarios
   - Database failure scenarios
   - **Priority**: Low
   - **Complexity**: High
   - **Dependencies**: Chaos engineering tools

4. **Contract Testing**
   - API contract testing (Pact)
   - Event schema contract testing
   - **Priority**: Medium
   - **Complexity**: Medium
   - **Dependencies**: Contract testing tools

---

## Deferred Features (Post-MVP)

### Low Priority
- Email verification
- Two-factor authentication
- Multi-currency support
- Customer analytics
- Wallet analytics
- Bulk transactions
- Transaction export
- IP whitelisting
- API key management

### Future Enhancements
- Advanced fraud detection
- Machine learning for transaction patterns
- Real-time notifications (WebSocket)
- Mobile app support
- Third-party integrations
- Advanced reporting and analytics

## Priority Classification

### High Priority (Next Phase)
1. Transaction history/listing
2. Wallet lifecycle management
3. Dead letter queue
4. Event monitoring
5. Rate limiting
6. Audit logging
7. Metrics collection
8. Alerting

### Medium Priority (Future Phases)
1. Password reset
2. Transaction cancellation
3. Transaction fees
4. Limit management
5. Document management
6. Event replay
7. Distributed tracing
8. Security testing

### Low Priority (Post-MVP)
1. Email verification
2. Two-factor authentication
3. Multi-currency support
4. Customer analytics
5. Wallet analytics
6. Bulk transactions
7. Transaction export
8. IP whitelisting

## Dependencies and Blockers

### External Dependencies
- Email service (for password reset, email verification)
- File storage service (for document management)
- KYC provider API (for real KYC integration)
- Currency conversion service (for multi-currency)
- Fraud detection service (for advanced validation)

### Infrastructure Dependencies
- Prometheus (for metrics)
- Grafana (for dashboards)
- ELK stack (for log aggregation)
- Jaeger/Zipkin (for distributed tracing)
- Load testing tools (for performance testing)

### Internal Dependencies
- Some features depend on other missing features
- Example: Transaction cancellation depends on transaction history

## Next Steps

See [09_NEXT_TASKS.md](./09_NEXT_TASKS.md) for structured task definitions for implementing these features.

---

**Status**: 📋 Catalog Complete  
**Last Updated**: 2025-12-28

