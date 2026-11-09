# MediStock — Enterprise Pharmacy Operations & Inventory Platform

MediStock is a full-stack, enterprise-grade Pharmacy Operations Platform combining a high-performance **Spring Boot 3.3 (Java 21)** backend with a modern, responsive **React 18 + Vite + Tailwind CSS** frontend.

It provides real-time FEFO (First-Expired, First-Out) inventory allocation, concurrency-safe pessimistic locking, itemized sales traceability, sales returns, damaged/manual stock adjustments, 30/60/90-day expiry alerting, expired batch disposal, role-based access control (Admin & Pharmacist), and analytical reporting.

---

## 🏗️ System Architecture

```
pharmacy-inventory/
├── pharmacy-inventory/              # Backend (Spring Boot 3.3.4, Java 21, JPA/Hibernate, Flyway)
│   ├── src/main/java/               # Controllers, Services, Repositories, Security, Entities
│   ├── src/main/resources/          # application.properties, db/migration/ (V1, V2 Flyway scripts)
│   ├── src/test/java/               # Concurrency, Transaction Rollback, Sales Return & Flyway Tests
│   └── pom.xml                      # Maven dependencies (JPA, Security, JJWT 0.12.6, Flyway, H2, PostgreSQL)
│
└── frontend/                        # Frontend (React 18, Vite, Tailwind CSS, Lucide, Recharts)
    ├── src/pages/                   # Dashboard, Medicines, Batches, Sales POS, History, Alerts, Reports
    ├── src/components/              # Modals, DataTables, Badges, ConfirmDialogs, Layouts
    ├── src/context/                 # AuthContext (JWT & Roles), ToastContext (Global notifications)
    ├── src/services/                # Axios API services mapped to backend REST endpoints
    └── package.json                 # Frontend dependencies and build scripts
```

---

## ⚡ Quickstart Guide

### 1. Prerequisites
- **Java 21+** (`java -version`)
- **Maven 3.9+** (`mvn -v`)
- **Node.js 18+** & **npm** (`node -v`, `npm -v`)

---

### 2. Start the Backend

```powershell
cd pharmacy-inventory\pharmacy-inventory
mvn spring-boot:run
```

- **Port**: `http://localhost:8080`
- **Database**: H2 in-memory by default (or PostgreSQL via environment variables)
- **Database Migrations**: Flyway automatically executes `V1__init_schema.sql` and `V2__seed_initial_data.sql`
- **Default Seed Accounts**:
  - **Admin**: `admin` / `Admin@123` (`ROLE_ADMIN`)
  - **Pharmacist**: `pharmacist` / `Pharm@123` (`ROLE_PHARMACIST`)

---

### 3. Start the Frontend

In a separate terminal:

```powershell
cd pharmacy-inventory\frontend
npm.cmd run dev
```

- **Port**: `http://localhost:5173`
- **API Proxy**: Automatically proxies `/api/*` requests to `http://localhost:8080`

---

## 🛡️ Core Enterprise Features

### 1. Concurrency-Safe Stock & FEFO Logic
- **Pessimistic Row-Level Lock**: Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` during stock deduction and returns to eliminate overselling race conditions under high concurrent demand.
- **FEFO (First-Expired, First-Out)**: Automatically identifies and deducts batches with the earliest expiry dates first. Expired stock is filtered out and prohibited from sale.
- **Atomic Rollback**: `@Transactional(rollbackFor = Exception.class)` ensures that if any item in a multi-item checkout fails (e.g., due to stock exhaustion), all previous batch deductions in the request are immediately rolled back.

### 2. POS Dispensing & Thermal Invoicing
- Fast medicine search with live FEFO batch previews.
- Real-time customer cart, tax and subtotal computation using `BigDecimal` (scale 2, `HALF_UP`).
- Instant generation of printable thermal/standard invoices with itemized batch allocation breakdown.

### 3. Sales Returns & Traceability
- Complete invoice ledger with date filtering and pharmacist attribution.
- Pessimistic locking prevents double returns and over-returns beyond the original purchased quantity.
- Restores returned items directly to the source batch and creates a `RETURN` audit entry in `stock_adjustments`.

### 4. Proactive Expiry & Low-Stock Alerts
- **Low Stock Tab**: Real-time warnings for medicines falling below their configured threshold.
- **Expiry Tab**: 30, 60, and 90-day countdowns for approaching expirations.
- **Expired Stock in Storage**: Visual indicator of expired inventory with Admin-only write-off disposal (`POST /api/inventory/expired/{id}/dispose`).

---

## 🧪 Automated Backend Tests

Run all unit, integration, and concurrency tests via Maven:

```powershell
cd pharmacy-inventory\pharmacy-inventory
mvn test
```

- `ConcurrencySaleTest.java`: Verifies 2 concurrent threads attempting to sell the final 5 units; ensures strictly 1 succeeds, 1 receives `InsufficientStockException`, and 0 units remain (never oversold).
- `TransactionRollbackTest.java`: Verifies multi-item atomic rollback when a subsequent item fails.
- `SalesReturnTest.java`: Verifies partial returns, blocks duplicate/excess returns, and updates status to `REFUNDED`.
- `FlywayMigrationCleanDbTest.java`: Verifies clean baseline migration and seed execution on empty database.
