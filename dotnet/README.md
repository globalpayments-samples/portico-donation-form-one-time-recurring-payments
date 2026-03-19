# .NET Donation Form — One-Time and Recurring Payments

This example demonstrates a comprehensive donation form supporting both one-time and recurring payments using ASP.NET Core 9.0 and the Global Payments Portico API.

## Features

- **One-Time Donations** - Immediate charge via tokenized card
- **Recurring Donations** - Customer → RecurringPaymentMethod → Schedule entity chain
- **Flexible Scheduling** - Monthly, quarterly, and annual billing frequencies
- **Duration Options** - Ongoing, end date, or specific number of payments
- **Secure Tokenization** - PCI SAQ-A compliant via Heartland Hosted Fields

## Requirements

- .NET 9.0
- Global Payments Portico account and API credentials

## Project Structure

```
dotnet/
├── Program.cs            # ASP.NET Core Minimal API with all payment logic
├── wwwroot/
│   └── index.html        # Donation form frontend (served as static files)
├── dotnet.csproj         # Project file with dependencies
├── appsettings.json      # Application configuration
├── .env.sample           # Environment variable template
├── run.sh                # Convenience startup script
└── Dockerfile            # Container build file
```

## Setup

### 1. Clone and Configure

```bash
cd dotnet/
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

### 3. Restore Dependencies

```bash
dotnet restore
```

### 4. Run the Application

```bash
./run.sh
```

Or manually:
```bash
dotnet run
```

Then open http://localhost:8000 in your browser.

## Implementation Details

### Architecture

ASP.NET Core 9.0 Minimal API (no controllers):
- Port 8000 (configurable via `PORT` environment variable)
- `DotEnv.Net 3.2.1` for environment variable loading
- `GlobalPayments.Api 9.0.16` for the Portico SDK
- Static files served from the `wwwroot/` directory

### Application Structure

All logic lives in `Program.cs` organized as static methods:
- `Main()` — bootstraps the app, loads env, configures SDK and endpoints
- `ConfigureGlobalPaymentsSDK()` — initializes `ServicesContainer` with `PorticoConfig`
- `ConfigureEndpoints(app)` — registers `GET /config` and `POST /process-donation` routes
- `ProcessOneTime(root, GetStr)` — one-time charge handler
- `ProcessRecurring(root, GetStr)` — recurring schedule handler

### SDK Configuration

`ConfigureGlobalPaymentsSDK()` sets up `PorticoConfig`:
- `SecretApiKey` from environment
- `ServiceUrl` pointed at `https://cert.api2.heartlandportico.com`
- `DeveloperId` and `VersionNumber` set for identification

### One-Time Payment Flow

1. Validates required fields: `payment_reference`, `amount`, `first_name`, `last_name`, `donor_email`, `billing_zip`
2. Creates `CreditCardData` with Hosted Fields token and cardholder name
3. Creates `Address` with sanitized postal code
4. Calls `card.Charge(amount).WithCurrency("USD").Execute()`
5. Returns transaction ID on success

### Recurring Payment Flow

1. Validates all required fields including full address: `phone`, `street_address`, `city`, `state`, `country`
2. Creates and saves a `Customer` entity with GUID-based ID
3. Adds tokenized card as a `RecurringPaymentMethod` to the customer
4. Builds a `Schedule` with frequency and optional duration constraints
5. Default start date: first day of next month if `start_date` not provided
6. Returns schedule key, customer key, and payment method key on success

### Utility Methods

- `SanitizePostalCode(string)` — strips non-alphanumeric/hyphen characters, truncates to 10 chars
- `MapFrequency(string)` — maps `"monthly"` / `"quarterly"` / `"annually"` to `ScheduleFrequency` constants

### Duration Types

- `"ongoing"` — no end date or payment limit set
- `"end_date"` — sets `scheduleBuilder.EndDate`
- `"num_payments"` — sets `scheduleBuilder.NumberOfPayments`

### Structured Logging

Console output uses prefixed log lines for traceability:
- `[donation]` — routing decisions
- `[one-time]` — one-time charge processing
- `[recurring]` — recurring schedule setup

## API Endpoints

### GET /config

Returns public API key for Heartland Hosted Fields initialization.

**Response (success):**
```json
{
  "success": true,
  "data": {
    "publicApiKey": "pkapi_cert_xxxxx"
  }
}
```

**Response (missing key, HTTP 500):**
```json
{
  "success": false,
  "message": "Payment configuration is unavailable"
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
- ✅ Amount validation (must be > 0, must parse as decimal)
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
- [.NET SDK on NuGet](https://www.nuget.org/packages/GlobalPayments.Api)
- [Heartland Hosted Fields Guide](https://developer.globalpay.com/ecommerce/payments/sdk/heartland-hosted-fields)

## Support

- Email: sdksupport@globalpay.com
- Developer Portal: https://developer.globalpay.com/
