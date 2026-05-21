# Portico Donation Form — One-Time and Recurring Payments

> Build a donation form that accepts one-time charges and recurring schedules using Portico Hosted Fields tokenization across Node.js, PHP, Java, and .NET.

## Critical Patterns

1. **Use the Portico keys, not the GP API template keys.** Every implementation configures `PorticoConfig` with `SECRET_API_KEY` and returns `PUBLIC_API_KEY` to the browser (`nodejs/server.js`, `php/config.php`, `php/process-one-time.php`, `php/process-recurring.php`, `java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`, `dotnet/Program.cs`). The `GP_API_APP_ID` and `GP_API_APP_KEY` values in every `.env.sample` are unused leftovers.
2. **Keep `withAllowDuplicates(true)` / `WithAllowDuplicates(true)` on one-time charges.** Node.js `processOneTime()`, PHP `process-one-time.php`, Java `processOneTime()`, and .NET `ProcessOneTime()` all explicitly allow duplicates before charging. Removing that flag can reject legitimate repeat donations that reuse the same amount and donor details.
3. **Recurring donations are a Portico-managed three-step flow.** Each recurring handler creates a `Customer`, attaches the Hosted Fields token with `addPaymentMethod(...)` / `AddPaymentMethod(...)`, then creates the billing schedule with `addSchedule(...)` / `AddSchedule(...)`. Nothing is stored locally; the returned keys come from Portico.
4. **Recurring defaults are business rules.** If `start_date` is missing, all four implementations schedule the first payment for the first day of the next month. If `frequency` is missing, validation fails; if it is present but unrecognized, each `mapFrequency()` / `MapFrequency()` helper falls back to monthly billing.
5. **Currency is fixed to USD on the server.** The frontends send a `currency` field, but every backend hardcodes `USD` when charging or creating schedules. Changing the browser payload alone will not change settlement currency.

## Repository Structure

### Node.js (Express)
- [`nodejs/server.js`](nodejs/server.js) — JavaScript reference implementation; `sanitizePostalCode()`, `mapFrequency()`, `processOneTime()`, and `processRecurring()` contain the payment logic, with anonymous `app.get('/config')` and `app.post('/process-donation')` route handlers.
- [`nodejs/index.html`](nodejs/index.html) — Hosted Fields frontend; fetches `/config`, tokenizes card data, and posts donations to `/process-donation`.
- [`nodejs/package.json`](nodejs/package.json) — Node runtime metadata and the `globalpayments-api` dependency.
- Storage layer: none; recurring customers, payment methods, and schedules are created directly in Portico.

### PHP (built-in server)
- [`php/process-one-time.php`](php/process-one-time.php) — one-time reference flow; `configureSdk()` and `sanitizePostalCode()` lead into the Portico `charge()` call.
- [`php/process-recurring.php`](php/process-recurring.php) — recurring reference flow; `configureSdk()`, `generateUuidV4()`, `sanitizePostalCode()`, and `mapFrequency()` build the customer → payment method → schedule chain.
- [`php/process-donation.php`](php/process-donation.php) — request router for `payment_type`.
- [`php/config.php`](php/config.php) — config endpoint that returns `PUBLIC_API_KEY`.
- [`php/index.html`](php/index.html) — frontend entry point; posts to `config.php` and `process-donation.php`.
- Storage layer: none; recurring entities live in Portico.

### Java (Jakarta Servlet on embedded Tomcat)
- [`java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`](java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java) — Java reference implementation; `init()` configures `PorticoConfig`, `doGet()` serves `/config`, `doPost()` routes requests, and `processOneTime()` / `processRecurring()` handle payment logic.
- [`java/src/main/webapp/index.html`](java/src/main/webapp/index.html) — frontend entry point.
- [`java/src/main/webapp/WEB-INF/web.xml`](java/src/main/webapp/WEB-INF/web.xml) — welcome-file config for the web root.
- [`java/pom.xml`](java/pom.xml) — Maven dependencies plus embedded Tomcat port `8000` via the Cargo plugin.
- Storage layer: none; recurring entities are created in Portico.

### .NET (ASP.NET Core minimal API)
- [`dotnet/Program.cs`](dotnet/Program.cs) — .NET reference implementation; `GetEnvVar()`, `ConfigureGlobalPaymentsSDK()`, `ConfigureEndpoints()`, `ProcessOneTime()`, `ProcessRecurring()`, `SanitizePostalCode()`, and `MapFrequency()` contain the full flow.
- [`dotnet/wwwroot/index.html`](dotnet/wwwroot/index.html) — frontend entry point served as static content.
- [`dotnet/dotnet.csproj`](dotnet/dotnet.csproj) — .NET dependencies, including `GlobalPayments.Api`.
- Storage layer: none; recurring entities live in Portico.

### Shared
- [`README.md`](README.md) — repo-level overview, request/response shapes, and sandbox test cards.
- [`docker-compose.yml`](docker-compose.yml) — multi-service runner; it still references `python/` and `go/` build contexts that are not present on this branch.
- [`Dockerfile.tests`](Dockerfile.tests) — shared browser-test image.
- [`index.html`](index.html) — generic root sample page; it is not the donation UI used by the language implementations.
- [`.env.sample`](nodejs/.env.sample), [`.env.sample`](php/.env.sample), [`.env.sample`](java/.env.sample), [`.env.sample`](dotnet/.env.sample) — checked-in env templates with identical contents that should stay synchronized.

## API Surface

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/config` | Node.js, Java, and .NET return the Hosted Fields `publicApiKey` used by the browser. |
| POST | `/process-donation` | Node.js, Java, and .NET route to one-time or recurring handling based on `payment_type`. |
| GET | `/config.php` | PHP returns the Hosted Fields `publicApiKey`. |
| POST | `/process-donation.php` | PHP routes to one-time or recurring handling based on `payment_type`. |

PHP exposes the same contract as the other languages, but its endpoint paths keep the `.php` suffix.

## Environment Variables

```bash
PUBLIC_API_KEY=pkapi_cert_...    # Hosted Fields public key returned by the config endpoint
SECRET_API_KEY=skapi_cert_...    # Portico secret key used by the server-side SDK calls

GP_API_APP_ID=...                # Present in every .env.sample, but unused template leftover
GP_API_APP_KEY=...               # Present in every .env.sample, but unused template leftover
```

`PORT` is runtime-only: `nodejs/server.js` and `dotnet/Program.cs` read it directly, `php/run.sh` honors it, and Java ignores it because `java/pom.xml` pins Tomcat to `8000`.

## Portico Sandbox Credentials

| Item | Value | Notes |
|------|-------|-------|
| Public key | `pkapi_cert_jKc1FtuyAydZhZfbB3` | Sample Hosted Fields key from the checked-in `.env.sample` files. |
| Secret key | `skapi_cert_MTyMAQBiHVEAewvIzXVFcmUd2UcyBge_eCpaASUp0A` | Sample Portico server key from the checked-in `.env.sample` files. |
| Visa | `4012002000060016` / `123` / any future date | Canonical Portico test card from the repo README files. |
| Mastercard | `5473500000000014` / `123` / any future date | Canonical Portico test card from the repo README files. |
| Discover | `6011000990156527` / `123` / any future date | Additional Portico test card documented in the repo README files. |
| Amex | `372700699251018` / `1234` / any future date | Additional Portico test card documented in the repo README files. |

Get real sandbox or production credentials from <https://developer.globalpayments.com/>.

## Architecture Summary

**Hosted Fields bootstrap:** language-specific `index.html` → config endpoint → `publicApiKey` → `GlobalPayments.creditCard.form(...)` tokenizes the card in the browser.

**One-time donation:** browser posts `payment_type=one-time` and a Hosted Fields `payment_reference` token → backend builds `CreditCardData` plus sanitized postal code → Portico `charge()` returns `transactionId`.

**Recurring donation:** browser posts `payment_type=recurring` plus donor profile fields → backend creates a Portico customer, attaches the token as a recurring payment method, then creates a schedule and returns `scheduleKey`, `customerKey`, and `paymentMethodKey`.

## Security Notes

This is demo code, not a production donation platform. There is no authentication, authorization, CSRF protection, webhook handling, or rate limiting. The implementations also log donor metadata, and Node.js / .NET log token prefixes, so tighten logging and request protections before real deployment.

## How to Run

```bash
cd nodejs && ./run.sh    # Node.js — http://localhost:8000
cd php && ./run.sh       # PHP — http://localhost:8000
cd java && ./run.sh      # Java — http://localhost:8000 (uses Maven Cargo + Tomcat)
cd dotnet && ./run.sh    # .NET — http://localhost:8000
```

`docker-compose.yml` is not runnable as-is on this branch: it references `./python` and `./go` services that do not exist in the repo snapshot checked into this branch.

## How to Verify

Hosted Fields tokens are created in the browser, so you cannot produce a real `payment_reference` with curl alone. Use the UI first to capture a valid token, then replay backend requests if you need endpoint-only verification.

```bash
# Node.js / Java / .NET config endpoint
curl http://localhost:8000/config
# Expected: {"success":true,"data":{"publicApiKey":"pkapi_cert_..."}}

# PHP config endpoint
curl http://localhost:8000/config.php
# Expected: {"success":true,"data":{"publicApiKey":"pkapi_cert_..."}}

# Node.js / Java / .NET donation endpoint (one-time example)
curl -X POST http://localhost:8000/process-donation \
  -H "Content-Type: application/json" \
  -d '{"payment_type":"one-time","payment_reference":"supt_from_browser","amount":"50.00","first_name":"Jane","last_name":"Doe","donor_email":"jane@example.com","billing_zip":"12345"}'
# Expected success shape: {"success":true,"data":{"transactionId":"...","currency":"USD"}}
# Expected recurring shape when payment_type=recurring: {"success":true,"data":{"scheduleKey":"...","customerKey":"...","paymentMethodKey":"..."}}

# PHP donation endpoint
curl -X POST http://localhost:8000/process-donation.php \
  -H "Content-Type: application/json" \
  -d '{"payment_type":"one-time","payment_reference":"supt_from_browser","amount":"50.00","first_name":"Jane","last_name":"Doe","donor_email":"jane@example.com","billing_zip":"12345"}'
# Expected success shape: {"success":true,"data":{"transactionId":"...","currency":"USD"}}
```

## Making Changes

All language implementations expose the same donation behavior. If you change validation, request fields, currency handling, recurring defaults, or response shapes in one language, replicate the change in Node.js, PHP, Java, and .NET.

Do not modify `docker-compose.yml`, `README.md`, or only one checked-in `.env.sample` in isolation. Each language has its own frontend file (`nodejs/index.html`, `php/index.html`, `java/src/main/webapp/index.html`, `dotnet/wwwroot/index.html`), so UI changes belong in all four language implementations; the root `index.html` is a separate generic sample page.

## SDK Versions

- Node.js: `globalpayments-api` `^3.10.6` (`nodejs/package-lock.json` resolves `3.10.12`)
- PHP: `globalpayments/php-sdk` `^13.1`
- Java: `com.heartlandpaymentsystems:globalpayments-sdk` `14.2.20`
- .NET: `GlobalPayments.Api` `9.0.16`
