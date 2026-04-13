# PHP — Portico Donation Form (One-Time & Recurring)

PHP implementation of a donation form supporting both one-time and recurring payments using the Global Payments Portico gateway. Uses Heartland Hosted Fields for PCI SAQ-A compliant tokenization — card data never touches your server.

## Requirements

- PHP 8.0+
- Composer
- Global Payments Portico account with API credentials

## Project Structure

```
php/
├── config.php            # GET /config — returns publicApiKey
├── process-donation.php  # POST /process-donation — routes by payment_type
├── process-one-time.php  # One-time charge logic
├── process-recurring.php # Customer → PaymentMethod → Schedule chain
├── index.html            # Donation form frontend
├── composer.json         # globalpayments/php-sdk + phpdotenv
├── .env.sample
├── Dockerfile
├── run.sh
├── .devcontainer/
└── .codesandbox/
```

## Setup

**1. Install dependencies**
```bash
composer install
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
php -S localhost:8000
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

## SDK Configuration

Each processor (`process-one-time.php`, `process-recurring.php`) initializes the SDK identically:

```php
use GlobalPayments\Api\ServiceConfigs\Gateways\PorticoConfig;
use GlobalPayments\Api\ServicesContainer;

$config = new PorticoConfig();
$config->secretApiKey = $_ENV['SECRET_API_KEY'];
$config->developerId = '000000';
$config->versionNumber = '0000';
$config->serviceUrl = 'https://cert.api2.heartlandportico.com';

ServicesContainer::configureService($config);
```

## API Endpoints

### GET /config.php

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

### POST /process-donation.php

Routes to `process-one-time.php` or `process-recurring.php` based on `payment_type`.

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

```php
// 1. Card tokenized by globalpayments.js in browser
$token = $inputData['payment_reference'];

// 2. Attach token to CreditCardData
$card = new CreditCardData();
$card->token = $token;
$card->cardHolderName = $firstName . ' ' . $lastName;

// 3. Create billing address
$address = new Address();
$address->postalCode = sanitizePostalCode($billingZip);

// 4. Charge through Portico
$response = $card->charge($amount)
    ->withCurrency('USD')
    ->withAddress($address)
    ->execute();
```

## Recurring Payment Flow

```php
// Step 1 — Create customer
$customer = new Customer();
$customer->id = generateUuidV4();
$customer->firstName = $firstName;
$customer->address = new Address();
$customer->address->streetAddress1 = $streetAddress;
// ... other fields ...
$customer = $customer->create();

// Step 2 — Store payment method
$paymentMethod = $customer->addPaymentMethod(
    generateUuidV4(),
    $card
)->create();

// Step 3 — Create schedule
$schedule = $paymentMethod->addSchedule(generateUuidV4())
    ->withStatus('Active')
    ->withAmount($amount)
    ->withCurrency('USD')
    ->withFrequency(mapFrequency($frequency))  // monthly/quarterly/annually
    ->withStartDate(new \DateTime($startDate))
    ->create();
```

**`duration_type` mapping:**
- `"ongoing"` — no constraint added
- `"end_date"` — `->withEndDate(new \DateTime($endDate))`
- `"num_payments"` — `->withNumberOfPayments((int)$numPayments)`

## Test Cards

| Brand | Card Number | CVV | Expiry |
|-------|-------------|-----|--------|
| Visa | 4012002000060016 | 123 | Any future date |
| Mastercard | 5473500000000014 | 123 | Any future date |
| Discover | 6011000990156527 | 123 | Any future date |
| Amex | 372700699251018 | 1234 | Any future date |

## Docker

```bash
docker build -t portico-donation-php .
docker run -p 8003:8000 \
  -e PUBLIC_API_KEY=your_key \
  -e SECRET_API_KEY=your_key \
  portico-donation-php
# Open http://localhost:8003
```

Or via docker-compose from the project root:
```bash
docker-compose up php
```

## Troubleshooting

**Hosted Fields not loading**
Verify `GET /config.php` returns a 200 with a valid `publicApiKey`. If it returns an error, check that `.env` exists and `composer install` completed. Restart `php -S` after editing `.env`.

**"Missing required fields" (400)**
Recurring requires `phone`, `street_address`, `city`, `state`, and `country` in addition to the one-time fields. Verify all required fields are in the JSON body before posting.

**"Payment processing failed" — Portico error**
Confirm `SECRET_API_KEY` starts with `skapi_cert_` for the certification environment. Test using the cert keys in `.env.sample`. Check PHP error logs (`error_log`) for the raw Portico exception message.

**Frequency value rejected**
`frequency` must be exactly `"monthly"`, `"quarterly"`, or `"annually"`. Any other string will not map to a valid `ScheduleFrequency` constant and will cause the request to fail before reaching Portico.

**Composer install fails**
Requires PHP 8.0+ and Composer 2.x. Confirm with `php -v` and `composer --version`. If `ext-curl` or `ext-json` are missing, install them via your OS package manager (e.g. `apt install php-curl php-json`).

**Schedule created but no charge taken**
A schedule creation only records the recurring agreement — it does not immediately charge the donor. The first charge occurs on `start_date`. If `start_date` was omitted, the default is the first day of the following month.
