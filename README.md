# Donation Form with One-Time and Recurring Payments

This project demonstrates a comprehensive donation form supporting both one-time and recurring payments using the Global Payments Portico API with Heartland Hosted Fields for PCI SAQ-A compliant card tokenization.

## Available Implementations

- [.NET](./dotnet/) - ASP.NET Core 9.0 Minimal API
- [Java](./java/) - Jakarta EE servlet-based web application
- [Node.js](./nodejs/) - Express.js web application
- [PHP](./php/) - PHP web application

## Features

- **Unified Donation Form** - Single form interface for both payment types
- **One-Time Donations** - Immediate one-time payment processing
- **Recurring Donations** - Automated recurring billing with flexible schedules
- **Flexible Scheduling** - Monthly, quarterly, and annual billing frequencies
- **Duration Options** - Ongoing, end date, or specific number of payments
- **Secure Tokenization** - PCI SAQ-A compliant via Heartland Hosted Fields
- **Donor Management** - Customer entity creation for recurring donors

## Quick Start

1. Navigate to any implementation directory (`nodejs`, `php`, `java`, `dotnet`)
2. Copy `.env.sample` to `.env` and add your Global Payments Portico credentials
3. Run `./run.sh` to install dependencies and start the server

## API Endpoints

All implementations expose the same two endpoints:

### GET /config

Returns the public API key for Heartland Hosted Fields initialization.

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

Routes to the appropriate processor based on `payment_type`.

**Common fields:**
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

**Additional fields for `payment_type: "recurring"`:**
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
  "duration_type": "ongoing",
  "start_date": "2025-05-01"
}
```

`duration_type` options: `"ongoing"`, `"end_date"` (requires `end_date`), `"num_payments"` (requires `num_payments`)

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

## Prerequisites

- Global Payments Portico account with API credentials
- Language runtime for your chosen implementation
- Package manager (npm, Composer, Maven, or dotnet CLI)

## Additional Resources

- [Global Payments Developer Portal](https://developer.globalpay.com/)
- [Portico API Documentation](https://developer.globalpay.com/ecommerce)
- [Heartland Hosted Fields Guide](https://developer.globalpay.com/ecommerce/payments/sdk/heartland-hosted-fields)

## Support

- Email: sdksupport@globalpay.com
- Developer Portal: https://developer.globalpay.com/
