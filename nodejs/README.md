# Node.js — Portico Donation Form (One-Time & Recurring)

Node.js/Express implementation of a donation form supporting both one-time and recurring payments using the Global Payments Portico gateway. Uses Heartland Hosted Fields for PCI SAQ-A compliant tokenization — card data never touches your server.

## Requirements

- Node.js 18+
- npm
- Global Payments Portico account with API credentials

## Project Structure

```
nodejs/
├── server.js       # Express server — GET /config and POST /process-donation
├── index.html      # Donation form frontend
├── package.json    # globalpayments-api + dotenv
├── .env.sample
├── Dockerfile
├── run.sh
├── .devcontainer/
└── .codesandbox/
```

## Setup

**1. Install dependencies**
```bash
npm install
```

**2. Configure credentials**
```bash
cp .env.sample .env
```

Edit `.env`:
```env
PUBLIC_API_KEY=pkapi_cert_jKc1FtuyAydZhZfbB3
SECRET_API_KEY=skapi_cert_MTyMAQBiHVEAewvIzXVFcmUd2UcyBge_eCpaASUp0A
PORT=8000
```

**3. Start the server**
```bash
npm start
# Open http://localhost:8000
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
| `PORT` | Server port | ❌ | `8000` (default) |

## SDK Configuration

Configured once at startup in `server.js`:

```javascript
import {
    ServicesContainer,
    PorticoConfig,
    CreditCardData,
    Customer,
    RecurringPaymentMethod,
    ScheduleFrequency,
} from 'globalpayments-api';
import dotenv from 'dotenv';

dotenv.config();

const config = new PorticoConfig();
config.secretApiKey = process.env.SECRET_API_KEY.trim();
config.developerId = '000000';
config.versionNumber = '0000';
config.serviceUrl = 'https://cert.api2.heartlandportico.com';

ServicesContainer.configureService(config);
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

```javascript
const card = new CreditCardData();
card.token = paymentReference;
card.cardHolderName = `${firstName} ${lastName}`;

const address = new Address();
address.postalCode = sanitizePostalCode(billingZip);

const response = await card.charge(parseFloat(amount))
    .withCurrency('USD')
    .withAddress(address)
    .execute();

return { transactionId: response.transactionId };
```

## Recurring Payment Flow

```javascript
// Step 1 — Create customer
const customer = new Customer();
customer.id = generateUuid();
customer.firstName = firstName;
customer.email = donorEmail;
customer.address = new Address();
customer.address.streetAddress1 = streetAddress;
// ... other fields ...
const savedCustomer = await customer.create();

// Step 2 — Store payment method
const savedMethod = await savedCustomer
    .addPaymentMethod(generateUuid(), card)
    .create();

// Step 3 — Create schedule
const schedule = savedMethod.addSchedule(generateUuid())
    .withStatus('Active')
    .withAmount(parseFloat(amount))
    .withCurrency('USD')
    .withFrequency(mapFrequency(frequency))  // ScheduleFrequency.Monthly etc.
    .withStartDate(new Date(startDate));

// Apply duration constraint
if (durationType === 'end_date') schedule.withEndDate(new Date(endDate));
if (durationType === 'num_payments') schedule.withNumberOfPayments(parseInt(numPayments));

const savedSchedule = await schedule.create();
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
docker build -t portico-donation-nodejs .
docker run -p 8001:8000 \
  -e PUBLIC_API_KEY=your_key \
  -e SECRET_API_KEY=your_key \
  portico-donation-nodejs
# Open http://localhost:8001
```

Or via docker-compose from the project root:
```bash
docker-compose up nodejs
```

## Troubleshooting

**Hosted Fields not loading**
Verify `GET /config` returns a 200 with a valid `publicApiKey`. Check the browser console for Heartland.js initialization errors. Ensure `PUBLIC_API_KEY` is set in `.env` and the server was restarted after editing it.

**"Missing required fields" (400)**
Recurring requires additional fields beyond one-time: `phone`, `street_address`, `city`, `state`, `country`. Confirm all fields are included in the request body and are non-empty strings.

**"Payment processing failed" — Portico error**
Confirm `SECRET_API_KEY` in `.env` starts with `skapi_cert_`. Trim any trailing whitespace — the `getEnvVar()` helper in `server.js` handles this automatically. Check the server console for the full Portico error message.

**`import` syntax error on startup**
The project uses ES module syntax. Ensure `"type": "module"` is present in `package.json` and you're running Node.js 18+. Check with `node --version`.

**Frequency value not recognized**
`frequency` must be exactly `"monthly"`, `"quarterly"`, or `"annually"`. The `mapFrequency()` function maps these to SDK constants. Any other value will be unmapped and cause the schedule creation to fail.

**Schedule created but no immediate charge**
Recurring schedule creation does not immediately charge the donor. The first charge occurs on `start_date`. If omitted, the default is the first day of the following month.
