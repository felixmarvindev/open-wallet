# OpenWallet Microservices API

A production-ready fintech wallet platform built with Spring Boot microservices architecture.

## Architecture

This project implements a microservices-based wallet system with the following services:

- **Auth Service** (Port 8081) - Authentication and authorization
- **Customer & KYC Service** (Port 8082) - Customer profiles and KYC management
- **Wallet Service** (Port 8083) - Wallet lifecycle and state management
- **Ledger/Transactions Service** (Port 8084) - Transaction processing with double-entry accounting
- **Notification Service** (Port 8085) - Event-driven notifications

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker and Docker Compose
- Git

## Quick Start

### 1. Start Infrastructure Services

Start all infrastructure services (PostgreSQL, Redis, Kafka, Keycloak, Prometheus, Grafana):

```bash
docker-compose up -d
```

This will start:
- **PostgreSQL** on port `5432`
- **Redis** on port `6379`
- **Kafka** on port `9092`
- **Keycloak** on port `8080`
- **Prometheus** on port `9090`
- **Grafana** on port `3000`

### 2. Verify Infrastructure

Check that all services are running:

```bash
docker-compose ps
```

### 3. Access Services

- **Keycloak Admin Console**: http://localhost:8080 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

### 4. Run Application Services Locally

Each service can be run locally using Maven:

```bash
# Auth Service
cd auth-service
mvn spring-boot:run

# Customer Service
cd customer-service
mvn spring-boot:run

# Wallet Service
cd wallet-service
mvn spring-boot:run

# Ledger Service
cd ledger-service
mvn spring-boot:run

# Notification Service
cd notification-service
mvn spring-boot:run
```

Or build and run from the root:

```bash
mvn clean install
mvn spring-boot:run -pl auth-service
```

## Development Setup

### Database Connection

All services connect to PostgreSQL running in Docker:
- Host: `localhost`
- Port: `5432`
- Database: `openwallet`
- Username: `openwallet`
- Password: `openwallet`

### Redis Connection

Services that use Redis (auth-service, wallet-service):
- Host: `localhost`
- Port: `6379`

### Kafka Connection

Services connect to Kafka:
- Bootstrap servers: `localhost:9092`

## Project Structure

```
open-wallet/
├── auth-service/          # Authentication service
├── customer-service/      # Customer & KYC service
├── wallet-service/        # Wallet management service
├── ledger-service/        # Transaction & ledger service
├── notification-service/  # Notification service
├── docker-compose.yml     # Infrastructure services
├── prometheus/            # Prometheus configuration
└── grafana/               # Grafana provisioning
```

## Stopping Services

Stop all infrastructure services:

```bash
docker-compose down
```

To remove volumes (WARNING: deletes all data):

```bash
docker-compose down -v
```

## Next Steps

See [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) for detailed architecture and implementation plan.


