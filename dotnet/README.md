# .NET — Portico Donation Form (One-Time & Recurring)

ASP.NET Core implementation of a donation form supporting both one-time and recurring payments using the Global Payments Portico gateway. Uses Heartland Hosted Fields for PCI SAQ-A compliant tokenization — card data never touches your server.

## Requirements

- .NET 9.0+
- Global Payments Portico account with API credentials

## Project Structure

```
dotnet/
├── Program.cs          # ASP.NET Core minimal API — all endpoints and payment logic
├── wwwroot/
│   └── index.html      # Donation form frontend (served as static file)
├── appsettings.json    # ASP.NET Core app settings
├── dotnet.csproj       # GlobalPayments.Api + dotenv.net
├── .env.sample
├── Dockerfile
├── run.sh
├── .devcontainer/
└── .codesandbox/
```

## Setup

**1. Restore dependencies**
```bash
dotnet restore
```

**2. Configure credentials**
```bash
cp .env.sample .env
```

Edit `.env`:
```env
PUBLIC_API_KEY=pkapi_cert_jKc1FtuyAydZhZfbB3
SECRET_API_KEY=skapi_cert_MTyMAQBiHVEAewvIzXVFcmUd2UcyBge_eCpaASUp0A
```

**3. Start the server**
```bash
dotnet run
# Open http://localhost:5000
```

Or use the convenience script:
```bash
./run.sh
```

## Environment Variables

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `PUBLIC_API_KEY` | Public key for Heartland Hosted Fields (browser) | ✅ | `pkapi_cert_jKc1FtuyAydZhZfbB3` |
| `SECRET_API_KEY` | Secret key for server-side Portico API calls | ✅ | `skapi_cert_MTyMAQBiHVEA...` |

## SDK Configuration

Configured once at startup in `Program.cs`:

```csharp
using GlobalPayments.Api;
using GlobalPayments.Api.ServiceConfigs.Gateways;
using dotenv.net;

DotEnv.Load();

private static void ConfigureGlobalPaymentsSDK()
{
    var config = new PorticoConfig
    {
        SecretApiKey = GetEnvVar("SECRET_API_KEY"),
        DeveloperId = "000000",
        VersionNumber = "0000",
        ServiceUrl = "https://cert.api2.heartlandportico.com"
    };
    ServicesContainer.Configure(config);
}
```

## API Endpoints

### GET /config

Returns the public API key for Heartland Hosted Fields initialization.

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

Routes to one-time or recurring logic based on `payment_type`.

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
| `"ongoing"` | — | Runs indefinitely |
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

## One-Time Payment Flow

```csharp
var card = new CreditCardData
{
    Token = paymentReference,
    CardHolderName = $"{firstName} {lastName}"
};

var address = new Address { PostalCode = SanitizePostalCode(billingZip) };

var response = await card.Charge(amount)
    .WithCurrency("USD")
    .WithAddress(address)
    .Execute();

return new { transactionId = response.TransactionId };
```

## Recurring Payment Flow

```csharp
// Step 1 — Create customer
var customer = new Customer
{
    Id = Guid.NewGuid().ToString(),
    FirstName = firstName,
    Email = donorEmail,
    Address = new Address { StreetAddress1 = streetAddress, ... }
};
var savedCustomer = customer.Create();

// Step 2 — Store payment method
var savedMethod = savedCustomer
    .AddPaymentMethod(Guid.NewGuid().ToString(), card)
    .Create();

// Step 3 — Create schedule
var schedule = savedMethod.AddSchedule(Guid.NewGuid().ToString())
    .WithStatus("Active")
    .WithAmount(amount)
    .WithCurrency("USD")
    .WithFrequency(MapFrequency(frequency))
    .WithStartDate(DateTime.Parse(startDate));

// Apply duration constraint
if (durationType == "end_date")     schedule.WithEndDate(DateTime.Parse(endDate));
if (durationType == "num_payments") schedule.WithNumberOfPayments(numPayments);

var savedSchedule = schedule.Create();
```

## Test Cards

| Brand | Card Number | CVV | Expiry |
|-------|-------------|-----|--------|
| Visa | 4012002000060016 | 123 | Any future date |
| Mastercard | 5473500000000014 | 123 | Any future date |
| Discover | 6011000990156527 | 123 | Any future date |
| Amex | 372700699251018 | 1234 | Any future date |

## Docker

```bash
docker build -t portico-donation-dotnet .
docker run -p 8006:8000 \
  -e ASPNETCORE_URLS=http://+:8000 \
  -e PUBLIC_API_KEY=your_key \
  -e SECRET_API_KEY=your_key \
  portico-donation-dotnet
# Open http://localhost:8006
```

Or via docker-compose from the project root:
```bash
docker-compose up dotnet
```

## Troubleshooting

**Hosted Fields not loading**
Verify `GET /config` returns a 200 with a valid `publicApiKey`. If it fails, confirm `.env` is present and `PUBLIC_API_KEY` is set. The SDK reads env vars at startup via `DotEnv.Load()` — restart `dotnet run` after editing `.env`.

**"Missing required fields" (400)**
Recurring requires additional fields beyond one-time: `phone`, `street_address`, `city`, `state`, `country`. Confirm all required fields are in the JSON body.

**"Payment processing failed" — Portico error**
Confirm `SECRET_API_KEY` starts with `skapi_cert_`. The `GetEnvVar()` helper strips inline comments (e.g. `#gitleaks:allow`) automatically. Check the server console for the raw Portico exception message.

**`dotnet run` fails with package error**
Verify .NET 9+ is installed: `dotnet --version`. Run `dotnet restore` to pull fresh packages. If `GlobalPayments.Api` restore fails, clear the NuGet cache: `dotnet nuget locals all --clear`.

**Static files not served (index.html 404)**
The frontend is served from `wwwroot/`. Ensure `app.UseStaticFiles()` and `app.UseDefaultFiles()` are both present in `Program.cs` and that `wwwroot/index.html` exists.

**Schedule created but no immediate charge**
Recurring schedule creation does not charge the donor immediately. The first charge occurs on `start_date`. If omitted, the default is the first day of the following month.
