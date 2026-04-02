# Portico Donation Form — One-Time & Recurring Payments

A comprehensive multi-language demonstration of donation payment processing using the Global Payments Portico API. This example showcases both one-time and recurring donation flows with Heartland Hosted Fields for PCI SAQ-A compliant card tokenization, donor management, and automated recurring billing schedules.

## 🚀 Features

### Core Payment Capabilities
- **One-Time Donations** - Immediate card charges for single donations
- **Recurring Donations** - Automated recurring billing with customer and schedule management
- **Heartland Hosted Fields** - PCI SAQ-A compliant client-side card tokenization
- **Flexible Scheduling** - Monthly, quarterly, and annual billing frequencies
- **Duration Options** - Ongoing, end date, or fixed number of payments
- **Donor Management** - Automatic customer entity creation for recurring donors

### Development & Testing
- **Portico Sandbox** - Full certification environment for development
- **Heartland Test Cards** - Built-in test card numbers for sandbox testing
- **Comprehensive Web Interface** - Unified donation form for both payment types
- **Consistent API Design** - Identical endpoints and behavior across all implementations

### Technical Features
- **Single Endpoint Processing** - Routes one-time and recurring donations through `/process-donation`
- **Customer + Schedule Creation** - Automatically creates Portico customer, payment method, and schedule entities
- **Hosted Fields Tokenization** - Card data never touches your server
- **Environment Configuration** - Secure credential management with .env files

## 🌐 Available Implementations

Each implementation provides identical functionality with language-specific best practices:

| Language | Framework | Requirements | Status |
|----------|-----------|--------------|--------|
| **[PHP](./php/)** - ([Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/php)) | Native PHP | PHP 7.4+, Composer | ✅ Complete |
| **[Node.js](./nodejs/)** - ([Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/nodejs)) | Express.js | Node.js 18+, npm | ✅ Complete |
| **[.NET](./dotnet/)** - ([Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/dotnet)) | ASP.NET Core | .NET 9.0+ | ✅ Complete |
| **[Java](./java/)** - ([Preview](https://githubbox.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments/tree/main/java)) | Jakarta EE | Java 11+, Maven | ✅ Complete |

## 🏗️ Architecture Overview

### Frontend Architecture
- **Heartland Hosted Fields** - Secure card data capture via hosted payment fields
- **Unified Donation Form** - Single form supporting both one-time and recurring donations
- **Real-Time Validation** - Client-side form validation and donation type switching
- **Responsive Design** - Clean interface with amount presets and frequency selection

### Backend Architecture
- **RESTful API Design** - Consistent endpoints across all implementations
- **Token-Based Processing** - Server charges the token from Heartland Hosted Fields
- **Recurring Entity Management** - Creates customer, payment method, and schedule in Portico
- **Error Handling** - Structured responses with categorized error codes (`PAYMENT_DECLINED`, `API_ERROR`, `SYSTEM_ERROR`)

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/config` | Return public API key for Hosted Fields initialization |
| `POST` | `/process-donation` | Process one-time or recurring donation |

## 🚀 Quick Start

### Prerequisites
- Global Payments Portico account with API credentials ([Sign up here](https://developer.globalpay.com/))
- Development environment for your chosen language
- Package manager (npm, composer, maven, or dotnet)

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/globalpayments-samples/portico-donation-form-one-time-recurring-payments.git
   cd portico-donation-form-one-time-recurring-payments
   ```

2. **Choose your implementation**
   ```bash
   cd php  # or nodejs, dotnet, java
   ```

3. **Configure environment**
   ```bash
   cp .env.sample .env
   # Edit .env with your Portico credentials:
   # PUBLIC_API_KEY=pkapi_cert_xxxxx
   # SECRET_API_KEY=skapi_cert_xxxxx
   ```

4. **Install dependencies and run**
   ```bash
   ./run.sh
   ```

   Or manually per language:
   ```bash
   # PHP
   composer install && php -S localhost:8000

   # Node.js
   npm install && npm start

   # .NET
   dotnet restore && dotnet run

   # Java
   mvn clean compile cargo:run
   ```

5. **Access the application**
   Open [http://localhost:8000](http://localhost:8000) in your browser

## 🧪 Development & Testing

### Test Cards (Portico Sandbox)

| Card | Number | CVV | Expiry |
|------|--------|-----|--------|
| **Visa** | 4012002000060016 | 123 | 12/2028 |
| **MasterCard** | 2223000010005780 | 123 | 12/2028 |
| **Discover** | 6011000990156527 | 123 | 12/2028 |
| **Amex** | 372700699251018 | 1234 | 12/2028 |

### Duration Configuration

| Duration Type | Additional Field | Description |
|---------------|-----------------|-------------|
| `ongoing` | None | Charges indefinitely until manually cancelled |
| `end_date` | `end_date` (YYYY-MM-DD) | Stops on the specified date |
| `num_payments` | `num_payments` (integer) | Stops after N payments |

## 💳 Payment Flow

### One-Time Donation
1. Donor enters amount and card details via Hosted Fields
2. Hosted Fields tokenizes card data client-side
3. Frontend sends token + amount to `POST /process-donation` with `payment_type: "one-time"`
4. Backend charges token via Portico SDK
5. Returns transaction ID and confirmation

### Recurring Donation
1. Donor enters amount, frequency, duration, and card details
2. Hosted Fields tokenizes card data client-side
3. Frontend sends token + recurring params to `POST /process-donation` with `payment_type: "recurring"`
4. Backend creates Portico customer entity with donor information
5. Backend creates payment method linked to customer
6. Backend creates recurring schedule with frequency and duration
7. Returns schedule key, customer key, and payment method key

## 🔧 API Reference

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
  "duration_type": "ongoing",
  "start_date": "2025-05-01"
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

## 🔧 Customization

### Extending Functionality
Each implementation provides a solid foundation for:
- **Custom Donation Amounts** - Modify presets and currency options
- **Donor Receipts** - Add email confirmation and tax receipt generation
- **Campaign Integration** - Associate donations with fundraising campaigns
- **Donor Portal** - Add schedule management and payment history views
- **Reporting** - Add donation analytics and export capabilities

### Production Considerations
Before deploying to production:
- **Security** - Store credentials in `.env`, never commit to version control
- **HTTPS** - Always use HTTPS in production environments
- **PCI Compliance** - Hosted Fields handles card data; maintain SAQ-A compliance
- **Logging** - Add secure logging with PII protection
- **Error Handling** - Implement comprehensive error recovery and donor notifications

## 🤝 Contributing

This project serves as a reference implementation for Portico donation form integration. When contributing:
- Maintain consistency across all language implementations
- Follow each language's best practices and conventions
- Ensure thorough testing in the sandbox environment
- Update documentation to reflect any changes

## 📄 License

MIT License — see [LICENSE](./LICENSE) for details.

## 🆘 Support

- **Global Payments Developer Portal**: [https://developer.globalpay.com/](https://developer.globalpay.com/)
- **Portico API Documentation**: [https://developer.globalpay.com/ecommerce](https://developer.globalpay.com/ecommerce)
- **Heartland Hosted Fields Guide**: [https://developer.globalpay.com/ecommerce/payments/sdk/heartland-hosted-fields](https://developer.globalpay.com/ecommerce/payments/sdk/heartland-hosted-fields)
- **Email**: sdksupport@globalpay.com

---

**Note**: This is a demonstration application for development and testing purposes. For production use, implement additional security measures, error handling, and compliance requirements specific to your use case.
