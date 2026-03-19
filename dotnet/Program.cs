using System;
using System.Text.Json;
using GlobalPayments.Api;
using GlobalPayments.Api.Entities;
using GlobalPayments.Api.PaymentMethods;
using dotenv.net;

namespace DonationFormSample;

public class Program
{
    public static void Main(string[] args)
    {
        DotEnv.Load();

        var builder = WebApplication.CreateBuilder(args);
        var app = builder.Build();

        app.UseDefaultFiles();
        app.UseStaticFiles();

        ConfigureGlobalPaymentsSDK();
        ConfigureEndpoints(app);

        var port = System.Environment.GetEnvironmentVariable("PORT") ?? "8000";
        app.Urls.Add($"http://0.0.0.0:{port}");

        app.Run();
    }

    private static void ConfigureGlobalPaymentsSDK()
    {
        ServicesContainer.ConfigureService(new PorticoConfig
        {
            SecretApiKey = System.Environment.GetEnvironmentVariable("SECRET_API_KEY"),
            DeveloperId = "000000",
            VersionNumber = "0000",
            ServiceUrl = "https://cert.api2.heartlandportico.com"
        });
    }

    private static void ConfigureEndpoints(WebApplication app)
    {
        app.MapGet("/config", () =>
        {
            var publicApiKey = System.Environment.GetEnvironmentVariable("PUBLIC_API_KEY");
            if (string.IsNullOrEmpty(publicApiKey))
            {
                return Results.Json(new { success = false, message = "Payment configuration is unavailable" },
                    statusCode: 500);
            }
            return Results.Ok(new { success = true, data = new { publicApiKey } });
        });

        app.MapPost("/process-donation", async (HttpContext context) =>
        {
            using var doc = await JsonDocument.ParseAsync(context.Request.Body);
            var root = doc.RootElement;

            string GetStr(string key) =>
                root.TryGetProperty(key, out var v) && v.ValueKind != JsonValueKind.Null
                    ? v.GetString() ?? ""
                    : "";

            var paymentType = GetStr("payment_type");

            if (string.IsNullOrEmpty(paymentType))
            {
                return Results.BadRequest(new
                {
                    success = false,
                    message = "Missing payment_type",
                    error = "payment_type must be \"one-time\" or \"recurring\""
                });
            }

            Console.WriteLine($"[donation] Request received: payment_type={paymentType}");

            if (paymentType == "one-time")
            {
                Console.WriteLine("[donation] Routing to one-time processor");
                return ProcessOneTime(root, GetStr);
            }
            else if (paymentType == "recurring")
            {
                Console.WriteLine("[donation] Routing to recurring processor");
                return ProcessRecurring(root, GetStr);
            }
            else
            {
                Console.WriteLine($"[donation] ERROR: Invalid payment_type: {paymentType}");
                return Results.BadRequest(new
                {
                    success = false,
                    message = "Invalid payment_type",
                    error = "payment_type must be \"one-time\" or \"recurring\""
                });
            }
        });
    }

    private static IResult ProcessOneTime(JsonElement root, Func<string, string> GetStr)
    {
        try
        {
            var paymentReference = GetStr("payment_reference");
            var amountStr = GetStr("amount");
            var firstName = GetStr("first_name").Trim();
            var lastName = GetStr("last_name").Trim();
            var donorEmail = GetStr("donor_email");
            var billingZip = GetStr("billing_zip");

            if (string.IsNullOrEmpty(paymentReference))
                throw new ApiException("Missing payment reference");
            if (string.IsNullOrEmpty(amountStr) || !decimal.TryParse(amountStr, out var amount) || amount <= 0)
                throw new ApiException("Invalid amount");
            if (string.IsNullOrEmpty(firstName))
                throw new ApiException("Missing first name");
            if (string.IsNullOrEmpty(lastName))
                throw new ApiException("Missing last name");
            if (string.IsNullOrEmpty(donorEmail))
                throw new ApiException("Missing donor email");
            if (string.IsNullOrEmpty(billingZip))
                throw new ApiException("Missing billing zip");

            Console.WriteLine($"[one-time] Processing charge: amount={amount} donor={donorEmail}");

            var card = new CreditCardData
            {
                Token = paymentReference,
                CardHolderName = $"{firstName} {lastName}"
            };

            var address = new Address
            {
                PostalCode = SanitizePostalCode(billingZip)
            };

            var response = card.Charge(amount)
                .WithAllowDuplicates(true)
                .WithCurrency("USD")
                .WithAddress(address)
                .Execute();

            if (response.ResponseCode != "00")
            {
                Console.WriteLine($"[one-time] Charge declined: {response.ResponseMessage}");
                return Results.BadRequest(new
                {
                    success = false,
                    message = "Payment processing failed",
                    error = new { code = "PAYMENT_DECLINED", details = response.ResponseMessage }
                });
            }

            Console.WriteLine($"[one-time] Charge success: transactionId={response.TransactionId} responseCode=00");
            return Results.Ok(new
            {
                success = true,
                message = "Thank you for your donation!",
                data = new
                {
                    transactionId = response.TransactionId,
                    status = response.ResponseMessage,
                    amount,
                    currency = "USD",
                    firstName,
                    lastName,
                    donorEmail,
                    timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")
                }
            });
        }
        catch (ApiException ex)
        {
            Console.WriteLine($"[one-time] ApiException: {ex.Message}");
            return Results.BadRequest(new
            {
                success = false,
                message = "Payment processing failed",
                error = new { code = "API_ERROR", details = ex.Message }
            });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[one-time] Unexpected error: {ex.Message}");
            return Results.Json(new
            {
                success = false,
                message = "An unexpected error occurred",
                error = new { code = "SYSTEM_ERROR", details = ex.Message }
            }, statusCode: 500);
        }
    }

    private static IResult ProcessRecurring(JsonElement root, Func<string, string> GetStr)
    {
        try
        {
            var amountStr = GetStr("amount");
            if (string.IsNullOrEmpty(amountStr) || !decimal.TryParse(amountStr, out var amount) || amount <= 0)
                throw new ApiException("Invalid amount");

            var requiredFields = new[] {
                "payment_reference", "first_name", "last_name",
                "donor_email", "frequency", "billing_zip",
                "phone", "street_address", "city", "state", "country"
            };
            foreach (var field in requiredFields)
            {
                if (string.IsNullOrEmpty(GetStr(field)))
                    throw new ApiException($"Missing required field: {field}");
            }

            var paymentReference = GetStr("payment_reference");
            var firstName = GetStr("first_name").Trim();
            var lastName = GetStr("last_name").Trim();
            var donorEmail = GetStr("donor_email");
            var frequency = GetStr("frequency");

            Console.WriteLine($"[recurring] Processing schedule: amount={amount} frequency={frequency} donor={donorEmail}");

            var customer = new Customer
            {
                Id = Guid.NewGuid().ToString(),
                FirstName = firstName,
                LastName = lastName,
                Email = donorEmail,
                Status = "Active",
                Address = new Address
                {
                    StreetAddress1 = GetStr("street_address").Trim(),
                    City = GetStr("city").Trim(),
                    Province = GetStr("state").Trim(),
                    PostalCode = SanitizePostalCode(GetStr("billing_zip")),
                    Country = GetStr("country").Trim()
                },
                WorkPhone = GetStr("phone").Trim()
            };

            var savedCustomer = customer.Create();
            Console.WriteLine($"[recurring] Customer created: key={savedCustomer.Key}");

            var card = new CreditCardData { Token = paymentReference };
            var paymentMethod = savedCustomer.AddPaymentMethod(Guid.NewGuid().ToString(), card).Create();
            Console.WriteLine($"[recurring] Payment method created: key={paymentMethod.Key}");

            var startDateStr = GetStr("start_date");
            DateTime startDate;
            if (!string.IsNullOrEmpty(startDateStr))
            {
                startDate = DateTime.ParseExact(startDateStr, "yyyy-MM-dd", null);
            }
            else
            {
                var now = DateTime.Now;
                startDate = new DateTime(now.Year, now.Month, 1).AddMonths(1);
            }

            var scheduleBuilder = paymentMethod.AddSchedule(Guid.NewGuid().ToString())
                .WithStatus("Active")
                .WithAmount(amount)
                .WithCurrency("USD")
                .WithStartDate(startDate)
                .WithFrequency(MapFrequency(frequency));

            var durationType = GetStr("duration_type");
            if (durationType == "end_date")
            {
                var endDateStr = GetStr("end_date");
                if (!string.IsNullOrEmpty(endDateStr))
                    scheduleBuilder.EndDate = DateTime.ParseExact(endDateStr, "yyyy-MM-dd", null);
            }
            else if (durationType == "num_payments")
            {
                var numPaymentsStr = GetStr("num_payments");
                if (!string.IsNullOrEmpty(numPaymentsStr) && int.TryParse(numPaymentsStr, out var numPayments))
                    scheduleBuilder.NumberOfPayments = numPayments;
            }

            var savedSchedule = scheduleBuilder.Create();
            var startDateFormatted = startDate.ToString("yyyy-MM-dd");
            Console.WriteLine($"[recurring] Schedule created: key={savedSchedule.Key} startDate={startDateFormatted}");

            return Results.Ok(new
            {
                success = true,
                message = "Recurring donation created successfully!",
                data = new
                {
                    scheduleKey = savedSchedule.Key,
                    customerKey = savedCustomer.Key,
                    paymentMethodKey = paymentMethod.Key,
                    amount,
                    currency = "USD",
                    frequency,
                    startDate = startDateFormatted,
                    firstName,
                    lastName,
                    donorEmail,
                    timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")
                }
            });
        }
        catch (ApiException ex)
        {
            Console.WriteLine($"[recurring] ApiException: {ex.Message}");
            return Results.BadRequest(new
            {
                success = false,
                message = "Recurring donation setup failed",
                error = new { code = "API_ERROR", details = ex.Message }
            });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[recurring] Unexpected error: {ex.Message}");
            return Results.Json(new
            {
                success = false,
                message = "An unexpected error occurred",
                error = new { code = "SYSTEM_ERROR", details = ex.Message }
            }, statusCode: 500);
        }
    }

    private static string SanitizePostalCode(string postalCode)
    {
        if (string.IsNullOrEmpty(postalCode)) return string.Empty;
        var sanitized = new string(postalCode.Where(c => char.IsLetterOrDigit(c) || c == '-').ToArray());
        return sanitized.Length > 10 ? sanitized[..10] : sanitized;
    }

    private static string MapFrequency(string frequency) =>
        (frequency ?? "").ToLower() switch
        {
            "quarterly" => ScheduleFrequency.QUARTERLY,
            "annually" => ScheduleFrequency.ANNUALLY,
            _ => ScheduleFrequency.MONTHLY
        };
}
