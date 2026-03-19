# Java Donation Form — One-Time and Recurring Payments

This example demonstrates a comprehensive donation form supporting both one-time and recurring payments using Jakarta EE servlets and the Global Payments Portico API.

## Features

- **One-Time Donations** - Immediate charge via tokenized card
- **Recurring Donations** - Customer → RecurringPaymentMethod → Schedule entity chain
- **Flexible Scheduling** - Monthly, quarterly, and annual billing frequencies
- **Duration Options** - Ongoing, end date, or specific number of payments
- **Secure Tokenization** - PCI SAQ-A compliant via Heartland Hosted Fields

## Requirements

- Java 23
- Maven
- Global Payments Portico account and API credentials

## Project Structure

```
java/
├── src/
│   └── main/
│       ├── java/com/globalpayments/example/
│       │   └── ProcessPaymentServlet.java  # Main servlet (handles /config and /process-donation)
│       └── webapp/
│           ├── index.html                  # Donation form frontend
│           └── WEB-INF/
│               └── web.xml                 # Web application configuration
├── pom.xml           # Maven dependencies and build config
├── .env.sample       # Environment variable template
├── run.sh            # Convenience startup script
└── Dockerfile        # Container build file
```

## Setup

### 1. Clone and Configure

```bash
cd java/
cp .env.sample .env
```

### 2. Configure Environment Variables

Edit `.env` with your Portico API credentials:

```env
PUBLIC_API_KEY=pkapi_cert_xxxxxxxxxxxxx
SECRET_API_KEY=skapi_cert_xxxxxxxxxxxxx
```

**Test Credentials** (from `.env.sample`):
```env
PUBLIC_API_KEY=pkapi_cert_jKc1FtuyAydZhZfbB3
SECRET_API_KEY=skapi_cert_MTyMAQBiHVEAewvIzXVFcmUd2UcyBge_eCpaASUp0A
```

### 3. Build the Project

```bash
mvn clean install
```

### 4. Run the Application

```bash
./run.sh
```

Or manually:
```bash
mvn cargo:run
```

Then open http://localhost:8000 in your browser.

## Implementation Details

### Architecture

Jakarta EE servlet packaged as a WAR, deployed to an embedded Tomcat 10.x via the Cargo Maven plugin:
- Port 8000, context path `/`
- `dotenv-java 3.0.0` for environment variable loading
- `globalpayments-sdk 14.2.20` for the Portico SDK
- Gson for JSON serialization/deserialization

### Servlet Configuration

`ProcessPaymentServlet` is mapped to both `/config` and `/process-donation` via `@WebServlet`. The `init()` method configures the Portico SDK:
- `SECRET_API_KEY` from the dotenv file
- `serviceUrl` pointed at `https://cert.api2.heartlandportico.com`
- `developerId` and `versionNumber` set for identification

### Request Routing

- `doGet()` — handles `GET /config`, returns public API key
- `doPost()` — handles `POST /process-donation`, reads `payment_type` and routes to `processOneTime()` or `processRecurring()`

### One-Time Payment Flow

1. Validates required fields: `payment_reference`, `amount`, `first_name`, `last_name`, `donor_email`, `billing_zip`
2. Creates `CreditCardData` with Hosted Fields token and cardholder name
3. Creates `Address` with sanitized postal code
4. Calls `card.charge(amount).withCurrency("USD").execute()`
5. Returns transaction ID on success

### Recurring Payment Flow

1. Validates all required fields including full address: `phone`, `street_address`, `city`, `state`, `country`
2. Creates and saves a `Customer` entity with UUID-based ID
3. Adds tokenized card as a `RecurringPaymentMethod` to the customer
4. Builds a `Schedule` with frequency and optional duration constraints
5. Default start date: first day of next month if `start_date` not provided
6. Returns schedule key, customer key, and payment method key on success

### Utility Methods

- `sanitizePostalCode(String)` — strips non-alphanumeric/hyphen characters, truncates to 10 chars
- `mapFrequency(String)` — maps `"monthly"` / `"quarterly"` / `"annually"` to `ScheduleFrequency` SDK enums

### Duration Types

- `"ongoing"` — no end date or payment limit set
- `"end_date"` — `scheduleBuilder.withEndDate(parsedDate)`
- `"num_payments"` — `scheduleBuilder.withNumberOfPayments(n)`

### Structured Logging

Console output uses prefixed log lines for traceability:
- `[donation]` — routing decisions
- `[one-time]` — one-time charge processing
- `[recurring]` — recurring schedule setup

## API Endpoints

### GET /config

Returns public API key for Heartland Hosted Fields initialization.

**Response:**
```json
{
  "success": true,
  "data": {
    "publicApiKey": "pkapi_cert_xxxxx"
  }
}
```

### POST /process-donation

Routes to appropriate processor based on `payment_type`.

**One-time request:**
```json
{
  "payment_type": "one-time",
  "payment_reference": "<hosted-fields-token>",
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
  "payment_reference": "<hosted-fields-token>",
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
  "duration_type": "ongoing"
}
```

**One-time success response:**
```json
{
  "success": true,
  "message": "Thank you for your donation!",
  "data": {
    "transactionId": "1234567890",
    "status": "Approved",
    "amount": 50.00,
    "currency": "USD",
    "firstName": "Jane",
    "lastName": "Doe",
    "donorEmail": "jane@example.com",
    "timestamp": "2025-04-15 10:30:00"
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
    "startDate": "2025-05-01",
    "firstName": "Jane",
    "lastName": "Doe",
    "donorEmail": "jane@example.com",
    "timestamp": "2025-04-15 10:30:00"
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

## Security Considerations

### PCI Compliance

- ✅ **PCI SAQ-A Compliant** — Card data never touches your server
- ✅ **Tokenization** — Heartland Hosted Fields handle all sensitive card data
- ✅ **HTTPS Required** — Always use HTTPS in production

### Input Validation

- ✅ Server-side validation of all required fields
- ✅ Postal code sanitization (alphanumeric + hyphen only, max 10 chars)
- ✅ Amount validation (must be > 0)
- ✅ Payment type validation before routing

### Production Checklist

- [ ] Replace test credentials with production API keys
- [ ] Enable HTTPS/SSL on your server
- [ ] Implement rate limiting on payment endpoints
- [ ] Add CSRF protection to forms
- [ ] Configure proper error logging
- [ ] Set up monitoring and alerts

## Additional Resources

- [Global Payments Developer Portal](https://developer.globalpay.com/)
- [Portico API Documentation](https://developer.globalpay.com/ecommerce)
- [Java SDK on GitHub](https://github.com/globalpayments/java-sdk)
- [Heartland Hosted Fields Guide](https://developer.globalpay.com/ecommerce/payments/sdk/heartland-hosted-fields)

## Support

- Email: sdksupport@globalpay.com
- Developer Portal: https://developer.globalpay.com/
