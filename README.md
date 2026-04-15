# Portico Donation Form — One-Time and Recurring Payments

Complete implementation of a donation form supporting both one-time and recurring payments using the Global Payments Portico gateway across 4 programming languages. Uses GlobalPayments Hosted Fields for PCI SAQ-A compliant card tokenization — card data never touches your server. All implementations use the official Global Payments SDK (`PorticoConfig`).

## Available Implementations

| Language | Framework | SDK |
|----------|-----------|-----|
| [**PHP**](./php/) | Built-in Server | globalpayments/php-sdk |
| [**Node.js**](./nodejs/) | Express.js | globalpayments-api |
| [**.NET**](./dotnet/) | ASP.NET Core | GlobalPayments.Api |
| [**Java**](./java/) | Jakarta Servlet | com.globalpayments:java-sdk |

Preview links (runs in browser via CodeSandbox):
- [PHP Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/php)
- [Node.js Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/nodejs)
- [.NET Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/dotnet)
- [Java Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/java)

## How It Works

```
Browser                        Backend                         Portico API
   │                               │                               │
   │── GET /config ───────────────>│                               │
   │<─ { publicApiKey } ───────────│                               │
   │                               │                               │
   │  [globalpayments.js loads]         │                               │
   │  [User fills donation form]   │                               │
   │  [Hosted Fields tokenize]     │                               │
   │                               │                               │
   │── POST /process-donation ────>│                               │
   │   payment_type: "one-time"    │                               │
   │   payment_reference: token    │── card.charge() ─────────────>│
   │   amount, name, email, zip    │<─ { transactionId } ──────────│
   │<─ { transactionId } ──────────│                               │
   │                               │                               │
   │   OR                          │                               │
   │                               │                               │
   │── POST /process-donation ────>│                               │
   │   payment_type: "recurring"   │── Customer.create() ─────────>│
   │   + full address, frequency   │── addPaymentMethod() ─────────>│
   │   + duration_type             │── addSchedule() ──────────────>│
   │<─ { scheduleKey, customerKey }│<─ { scheduleKey } ────────────│
```

## Payment Type Comparison

| Feature | One-Time | Recurring |
|---------|----------|-----------|
| Required fields | name, email, zip, amount | name, email, full address, phone, amount, frequency |
| Backend entities | `CreditCardData` → `charge()` | `Customer` → `RecurringPaymentMethod` → `Schedule` |
| Response | `transactionId` | `scheduleKey` + `customerKey` + `paymentMethodKey` |
| Duration options | N/A | Ongoing, end date, number of payments |
| Frequencies | N/A | Monthly, quarterly, annually |

## Prerequisites

- Global Payments Portico developer account
- Portico API credentials (`PUBLIC_API_KEY` and `SECRET_API_KEY`)
- Docker, or runtime for your chosen language (PHP 8.0+, Node.js 18+, .NET 9+, Java 17+)

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments.git
cd portico-donation-form-one-time-recurring-payments
```

### 2. Choose a Language and Configure

```bash
cd php   # or nodejs, dotnet, java
cp .env.sample .env
```

Edit `.env`:
```env
PUBLIC_API_KEY=pkapi_cert_your_key_here
SECRET_API_KEY=skapi_cert_your_key_here
```

### 3. Install, Build, and Run

**PHP:**
```bash
composer install && php -S localhost:8000
```

**Node.js:**
```bash
npm install && npm start
```

**.NET:**
```bash
dotnet restore && dotnet run
```

**Java:**
```bash
mvn clean package && mvn cargo:run
```

### 4. Test a Donation

1. Open the app in your browser
2. Enter a donation amount
3. Select **One-Time** or **Recurring**
4. Fill in donor info and enter a test card
5. Submit and verify the response

## Docker Setup

```bash
cp php/.env.sample .env   # all languages share the same variables

docker-compose up
```

| Service | External Port | URL |
|---------|--------------|-----|
| nodejs  | 8001 | http://localhost:8001 |
| php     | 8003 | http://localhost:8003 |
| java    | 8004 | http://localhost:8004 |
| dotnet  | 8006 | http://localhost:8006 |

Run a single service:
```bash
docker-compose up php
```

## API Endpoints

### GET /config

Returns the public API key for GlobalPayments Hosted Fields initialization.

**Response:**
```json
{
  "success": true,
  "data": {
    "publicApiKey": "pkapi_cert_jKc1FtuyAydZhZfbB3"
  }
}
```

---

### POST /process-donation

Routes to the one-time or recurring processor based on `payment_type`.

**One-time request:**
```json
{
  "payment_type": "one-time",
  "payment_reference": "supt_xxxxxxxxxxxxxx",
  "amount": "50.00",
  "first_name": "Jane",
  "last_name": "Doe",
  "donor_email": "jane@example.com",
  "billing_zip": "12345"
}
```

**Recurring request:**
```json
{
  "payment_type": "recurring",
  "payment_reference": "supt_xxxxxxxxxxxxxx",
  "amount": "25.00",
  "first_name": "Jane",
  "last_name": "Doe",
  "donor_email": "jane@example.com",
  "billing_zip": "12345",
  "phone": "555-555-5555",
  "street_address": "123 Main St",
  "city": "Anytown",
  "state": "GA",
  "country": "US",
  "frequency": "monthly",
  "duration_type": "ongoing",
  "start_date": "2025-05-01"
}
```

**`duration_type` options:**

| Value | Additional Field | Description |
|-------|-----------------|-------------|
| `"ongoing"` | — | No end date, runs indefinitely |
| `"end_date"` | `end_date` (YYYY-MM-DD) | Stops on a specific date |
| `"num_payments"` | `num_payments` (integer) | Stops after N payments |

**`frequency` options:** `"monthly"`, `"quarterly"`, `"annually"`

**One-time success response:**
```json
{
  "success": true,
  "message": "Thank you for your donation!",
  "data": {
    "transactionId": "1234567890",
    "amount": 50.00,
    "currency": "USD"
  }
}
```

**Recurring success response:**
```json
{
  "success": true,
  "message": "Recurring donation created successfully!",
  "data": {
    "scheduleKey": "schedule_xxxxx",
    "customerKey": "customer_xxxxx",
    "paymentMethodKey": "pm_xxxxx",
    "amount": 25.00,
    "currency": "USD",
    "frequency": "monthly",
    "startDate": "2025-05-01"
  }
}
```

**Error response:**
```json
{
  "success": false,
  "message": "Payment processing failed",
  "error": {
    "code": "API_ERROR",
    "details": "Error message details"
  }
}
```

Error codes: `PAYMENT_DECLINED`, `API_ERROR`, `SYSTEM_ERROR`

## Recurring Donation Flow

Server-side recurring setup follows a three-step entity chain on every call:

**Step 1 — Create customer**
```
Customer record created with donor name, email, and full billing address.
Returns customerKey.
```

**Step 2 — Store payment method**
```
Tokenized card attached to the customer as a RecurringPaymentMethod.
Returns paymentMethodKey.
```

**Step 3 — Create schedule**
```
Schedule configured with:
  - frequency:     monthly / quarterly / annually
  - duration_type: ongoing / end_date / num_payments
  - start_date:    provided date, or first day of next month if omitted
  - currency:      USD

Returns scheduleKey identifying the active recurring billing agreement.
```

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `PUBLIC_API_KEY` | Public key for GlobalPayments Hosted Fields (browser) | `pkapi_cert_jKc1FtuyAydZhZfbB3` |
| `SECRET_API_KEY` | Secret key for server-side Portico API calls | `skapi_cert_MTyMAQBiHVEA...` |

Obtain credentials from your [Global Payments developer account](https://developer.globalpayments.com/).

## Test Cards

| Brand | Card Number | CVV | Expiry |
|-------|-------------|-----|--------|
| Visa | 4012002000060016 | 123 | Any future date |
| Mastercard | 5473500000000014 | 123 | Any future date |
| Discover | 6011000990156527 | 123 | Any future date |
| Amex | 372700699251018 | 1234 | Any future date |

Additional test cards: [developer.globalpayments.com/resources/test-cards](https://developer.globalpayments.com/resources/test-cards)

## Project Structure

```
portico-donation-form-one-time-recurring-payments/
├── index.html              # Shared frontend (globalpayments.js + donation form)
├── docker-compose.yml      # Multi-service Docker config
├── README.md               # This file
├── LICENSE
├── php/                    # PHP implementation (Docker: 8003)
│   ├── config.php          # GET /config
│   ├── process-donation.php  # POST /process-donation — router
│   ├── process-one-time.php  # One-time charge logic
│   ├── process-recurring.php # Recurring schedule logic
│   ├── composer.json
│   ├── .env.sample
│   └── README.md
├── nodejs/                 # Node.js implementation (Docker: 8001)
│   ├── server.js           # Express server with both payment types
│   ├── package.json
│   ├── .env.sample
│   └── README.md
├── dotnet/                 # .NET implementation (Docker: 8006)
│   ├── Program.cs          # ASP.NET Core minimal API
│   ├── dotnet.csproj
│   ├── .env.sample
│   └── README.md
└── java/                   # Java implementation (Docker: 8004)
    ├── src/
    ├── pom.xml
    ├── .env.sample
    └── README.md
```

## Troubleshooting

**Hosted Fields not loading / blank card fields**
The `publicApiKey` returned by `GET /config` is invalid or missing. Open browser DevTools → Network to confirm `/config` returns a 200 with a valid key. Restart the server after editing `.env`.

**"Missing required fields" (400)**
Check that all required fields for the selected `payment_type` are present. Recurring requires additional fields (`phone`, `street_address`, `city`, `state`, `country`) not needed for one-time. Review the request body against the field tables above.

**"Payment processing failed" — API error**
Verify `SECRET_API_KEY` in `.env` is correct and starts with `skapi_cert_`. Ensure the cert service URL (`cert.api2.heartlandportico.com`) is reachable from your environment. Check server logs for the raw Portico error message.

**Recurring schedule not created**
Confirm `frequency` is one of `monthly`, `quarterly`, or `annually` (exact strings). If `duration_type` is `end_date`, an `end_date` in `YYYY-MM-DD` format must be included. If `duration_type` is `num_payments`, `num_payments` must be a positive integer.

**Composer install fails (PHP)**
Requires PHP 8.0+ and Composer 2.x. Check `php -v` and `composer --version`. Missing `ext-curl` or `ext-json` will cause install failures — install via your OS package manager.

**Maven build fails (Java)**
Requires Java 17+ and Maven 3.8+. Run `java -version` and `mvn -v`. If dependencies fail to resolve, try `mvn clean package -U` to force a fresh dependency download.

## Per-Language Documentation

- [PHP README](./php/README.md)
- [Node.js README](./nodejs/README.md)
- [.NET README](./dotnet/README.md)
- [Java README](./java/README.md)

## External Resources

- [Global Payments Developer Portal](https://developer.globalpayments.com/)
- [GlobalPayments Hosted Fields Guide](https://developer.globalpayments.com/api/references-overview)
- [Portico API Documentation](https://developer.globalpayments.com/api/references-overview)
- [Test Cards](https://developer.globalpayments.com/resources/test-cards)

## Community

- 🌐 **Developer Portal** — [developer.globalpayments.com](https://developer.globalpayments.com)
- 💬 **Discord** — [Join the community](https://discord.gg/myER9G9qkc)
- 📋 **GitHub Discussions** — [github.com/orgs/globalpayments/discussions](https://github.com/orgs/globalpayments/discussions)
- 📧 **Newsletter** — [Subscribe](https://www.globalpayments.com/en-gb/modals/newsletter)
- 💼 **LinkedIn** — [Global Payments for Developers](https://www.linkedin.com/showcase/global-payments-for-developers/posts/?feedView=all)

Have a question or found a bug? [Open an issue](https://github.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/issues) or reach out at [communityexperience@globalpay.com](mailto:communityexperience@globalpay.com).

## License

[MIT](./LICENSE)
