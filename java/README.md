# Java — Portico Donation Form (One-Time & Recurring)

Jakarta EE/Servlet implementation of a donation form supporting both one-time and recurring payments using the Global Payments Portico gateway. Uses Heartland Hosted Fields for PCI SAQ-A compliant tokenization — card data never touches your server.

## Requirements

- Java 17+
- Maven 3.8+
- Global Payments Portico account with API credentials

## Project Structure

```
java/
├── src/
│   └── main/
│       ├── java/com/globalpayments/example/
│       │   └── ProcessPaymentServlet.java  # Handles /config and /process-donation
│       └── webapp/
│           ├── index.html                  # Donation form frontend
│           └── WEB-INF/web.xml             # Servlet configuration
├── pom.xml         # com.globalpayments:java-sdk dependency
├── .env.sample
├── Dockerfile
├── run.sh
├── .devcontainer/
└── .codesandbox/
```

## Setup

**1. Build the project**
```bash
mvn clean package
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
mvn cargo:run
# Open http://localhost:8080
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

Configured at servlet initialization:

```java
import com.global.api.ServicesContainer;
import com.global.api.serviceConfigs.PorticoConfig;

PorticoConfig config = new PorticoConfig();
config.setSecretApiKey(System.getenv("SECRET_API_KEY"));
config.setDeveloperId("000000");
config.setVersionNumber("0000");
config.setServiceUrl("https://cert.api2.heartlandportico.com");

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

```java
CreditCardData card = new CreditCardData();
card.setToken(paymentReference);
card.setCardHolderName(firstName + " " + lastName);

Address address = new Address();
address.setPostalCode(sanitizePostalCode(billingZip));

Transaction response = card.charge(new BigDecimal(amount))
    .withCurrency("USD")
    .withAddress(address)
    .execute();

String transactionId = response.getTransactionId();
```

## Recurring Payment Flow

```java
// Step 1 — Create customer
Customer customer = new Customer();
customer.setId(UUID.randomUUID().toString());
customer.setFirstName(firstName);
customer.setEmail(donorEmail);
Address address = new Address();
address.setStreetAddress1(streetAddress);
// ... other fields ...
customer.setAddress(address);
Customer savedCustomer = customer.create();

// Step 2 — Store payment method
RecurringPaymentMethod savedMethod = savedCustomer
    .addPaymentMethod(UUID.randomUUID().toString(), card)
    .create();

// Step 3 — Create schedule
Schedule schedule = savedMethod.addSchedule(UUID.randomUUID().toString())
    .withStatus("Active")
    .withAmount(new BigDecimal(amount))
    .withCurrency("USD")
    .withFrequency(mapFrequency(frequency))
    .withStartDate(parseDate(startDate));

// Apply duration constraint
if ("end_date".equals(durationType))    schedule.withEndDate(parseDate(endDate));
if ("num_payments".equals(durationType)) schedule.withNumberOfPayments(Integer.parseInt(numPayments));

Schedule savedSchedule = schedule.create();
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
docker build -t portico-donation-java .
docker run -p 8004:8000 \
  -e PUBLIC_API_KEY=your_key \
  -e SECRET_API_KEY=your_key \
  portico-donation-java
# Open http://localhost:8004
```

Or via docker-compose from the project root:
```bash
docker-compose up java
```

## Troubleshooting

**Hosted Fields not loading**
Verify `GET /config` returns a 200 with a valid `publicApiKey`. If the servlet throws at startup, check that `PUBLIC_API_KEY` and `SECRET_API_KEY` are set as environment variables before running.

**"Missing required fields" (400)**
Recurring requires additional fields beyond one-time: `phone`, `street_address`, `city`, `state`, `country`. Confirm all required fields are present in the JSON body.

**"Payment processing failed" — Portico error**
Confirm `SECRET_API_KEY` starts with `skapi_cert_`. Environment variables must be exported in the shell before running `mvn cargo:run`:
```bash
export SECRET_API_KEY=skapi_cert_...
export PUBLIC_API_KEY=pkapi_cert_...
mvn cargo:run
```

**Maven build fails**
Requires Java 17+ and Maven 3.8+. Confirm with `java -version` and `mvn -v`. If the `com.globalpayments:java-sdk` dependency fails to resolve, run `mvn clean package -U` to force a refresh.

**Port conflict on 8080**
The Java implementation defaults to port 8080 (Tomcat default), not 8000 like the other languages. If 8080 is occupied, update the `cargo` plugin port in `pom.xml` or stop the conflicting process with `lsof -i :8080`.

**Schedule created but no immediate charge**
Recurring schedule creation does not charge the donor immediately. The first charge occurs on `start_date`. If omitted, the default is the first day of the following month.
