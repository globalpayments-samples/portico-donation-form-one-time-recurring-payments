# PHP Donation Form — One-Time and Recurring Payments

This example demonstrates a comprehensive donation form supporting both one-time and recurring payments using PHP and the Global Payments Portico API.

## Features

- **Unified Donation Form** - Single form interface for both payment types
- **One-Time Donations** - Immediate one-time payment processing
- **Recurring Donations** - Automated recurring billing with flexible schedules
- **Flexible Scheduling** - Monthly, quarterly, and annual billing frequencies
- **Duration Options** - Ongoing, end date, or specific number of payments
- **Secure Tokenization** - PCI SAQ-A compliant via Heartland Hosted Fields
- **Donor Management** - Customer entity creation for recurring donors

## Requirements

- PHP 7.4 or later
- Composer
- Global Payments Portico account and API credentials

## Project Structure

```
php/
├── process-donation.php      # Main router — dispatches to one-time or recurring processor
├── process-one-time.php      # One-time payment processor
├── process-recurring.php     # Recurring payment processor
├── config.php                # Configuration endpoint
├── index.html                # Donation form frontend
├── composer.json             # Dependencies
├── .env.sample               # Environment variable template
├── run.sh                    # Convenience startup script
└── Dockerfile                # Container build file
```

## Setup

### 1. Clone and Configure

```bash
cd php/
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

### 3. Install Dependencies

```bash
composer install
```

### 4. Run the Application

```bash
./run.sh
```

Or manually:
```bash
php -S localhost:8000
```

Then open http://localhost:8000 in your browser.

## Implementation Details

### Architecture

File-based router architecture:
- `process-donation.php` reads `payment_type` and dispatches to `process-one-time.php` or `process-recurring.php`
- `vlucas/phpdotenv ^5.5` for environment variable loading
- `globalpayments/php-sdk ^13.1` for the Portico SDK

### SDK Configuration

Each processor loads credentials via `vlucas/phpdotenv` and initializes `ServicesContainer` with `PorticoConfig`:
- `secretApiKey` from environment
- `serviceUrl` pointed at `https://cert.api2.heartlandportico.com`
- `developerId` and `versionNumber` set for identification

### One-Time Payment Flow

1. Validates required fields: `payment_reference`, `amount`, `first_name`, `last_name`, `donor_email`, `billing_zip`
2. Creates `CreditCardData` with Hosted Fields token and cardholder name
3. Creates `Address` with sanitized postal code
4. Calls `$card->charge($amount)->withCurrency('USD')->execute()`
5. Returns transaction ID on success

### Recurring Payment Flow

1. Validates all required fields including full address: `phone`, `street_address`, `city`, `state`, `country`
2. Creates and saves a `Customer` entity with UUID-based ID
3. Adds tokenized card as a `RecurringPaymentMethod` to the customer
4. Builds a `Schedule` with frequency and optional duration constraints
5. Default start date: first day of next month if `start_date` not provided
6. Returns schedule key, customer key, and payment method key on success

### Utility Functions

Each processor includes:
- `sanitizePostalCode($postalCode)` — strips non-alphanumeric/hyphen characters, truncates to 10 chars
- `generateUuidV4()` — generates a UUID v4 string for customer and payment method IDs
- `mapFrequency($frequency)` — maps `"monthly"` / `"quarterly"` / `"annually"` to `ScheduleFrequency` SDK constants

### Duration Types

- `"ongoing"` — no end date or payment limit set
- `"end_date"` — `$schedule->withEndDate(new \DateTime($endDate))`
- `"num_payments"` — `$schedule->withNumberOfPayments((int)$numPayments)`

## API Endpoints

### GET /config.php

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

### POST /process-donation.php

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
    "amount": 50.00
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
    "amount": 50.00,
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

## Security Considerations

### PCI Compliance

- ✅ **PCI SAQ-A Compliant** — Card data never touches your server
- ✅ **Tokenization** — Heartland Hosted Fields handle all sensitive card data
- ✅ **HTTPS Required** — Always use HTTPS in production

### Input Validation

- ✅ Server-side validation of all required fields
- ✅ Postal code sanitization (alphanumeric + hyphen only, max 10 chars)
- ✅ Amount validation (must be > 0)
- ✅ Email format validation
- ✅ Payment type validation before routing

### Production Checklist

- [ ] Replace test credentials with production API keys
- [ ] Enable HTTPS/SSL on your server
- [ ] Implement rate limiting on payment endpoints
- [ ] Add CSRF protection to forms
- [ ] Configure proper error logging
- [ ] Set up monitoring and alerts
- [ ] Review and update terms of service, privacy policy, and refund policy

## Troubleshooting

**"Error loading configuration"**
- Check that `.env` file exists and contains valid API keys
- Verify `composer install` completed successfully

**"Missing required fields"**
- Verify all form fields are being submitted
- Check browser console for JavaScript errors

**"Payment processing failed"**
- Check API credentials are correct
- Verify you're using cert credentials with cert service URL
- Review server error logs for detailed error messages

**Recurring schedule not created**
- Ensure all required recurring fields are provided
- Verify frequency value is valid: `monthly`, `quarterly`, or `annually`
- Check that start date format is `YYYY-MM-DD`

## Additional Resources

- [Global Payments Developer Portal](https://developer.globalpay.com/)
- [Portico API Documentation](https://developer.globalpay.com/ecommerce)
- [PHP SDK on GitHub](https://github.com/globalpayments/php-sdk)
- [Heartland Hosted Fields Guide](https://developer.globalpay.com/ecommerce/payments/sdk/heartland-hosted-fields)

## Support

- Email: sdksupport@globalpay.com
- Developer Portal: https://developer.globalpay.com/
