package com.globalpayments.example;

import com.global.api.ServicesContainer;
import com.global.api.entities.Address;
import com.global.api.entities.Customer;
import com.global.api.entities.Schedule;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.ScheduleFrequency;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.exceptions.ConfigurationException;
import com.global.api.paymentMethods.CreditCardData;
import com.global.api.serviceConfigs.PorticoConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@WebServlet(urlPatterns = {"/process-donation", "/config"})
public class ProcessPaymentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Dotenv dotenv = Dotenv.load();
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        try {
            PorticoConfig config = new PorticoConfig();
            config.setSecretApiKey(dotenv.get("SECRET_API_KEY"));
            config.setDeveloperId("000000");
            config.setVersionNumber("0000");
            config.setServiceUrl("https://cert.api2.heartlandportico.com");
            ServicesContainer.configureService(config);
        } catch (ConfigurationException e) {
            throw new ServletException("Failed to configure Global Payments SDK", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/config".equals(request.getServletPath())) {
            response.setContentType("application/json");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("publicApiKey", dotenv.get("PUBLIC_API_KEY"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("data", data);
            response.getWriter().write(gson.toJson(result));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
        String paymentType = getStr(body, "payment_type");

        if (paymentType == null || paymentType.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(gson.toJson(simpleError("Missing payment_type",
                    "payment_type must be \"one-time\" or \"recurring\"")));
            return;
        }

        System.out.println("[donation] Request received: payment_type=" + paymentType);

        if ("one-time".equals(paymentType)) {
            System.out.println("[donation] Routing to one-time processor");
            processOneTime(body, response);
        } else if ("recurring".equals(paymentType)) {
            System.out.println("[donation] Routing to recurring processor");
            processRecurring(body, response);
        } else {
            System.out.println("[donation] ERROR: Invalid payment_type: " + paymentType);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(gson.toJson(simpleError("Invalid payment_type",
                    "payment_type must be \"one-time\" or \"recurring\"")));
        }
    }

    private void processOneTime(JsonObject body, HttpServletResponse response) throws IOException {
        try {
            String paymentReference = getStr(body, "payment_reference");
            String amountStr = getStr(body, "amount");
            String firstName = getStr(body, "first_name");
            String lastName = getStr(body, "last_name");
            String donorEmail = getStr(body, "donor_email");
            String billingZip = getStr(body, "billing_zip");

            if (paymentReference == null || paymentReference.isEmpty())
                throw new ApiException("Missing payment reference");
            if (amountStr == null || amountStr.isEmpty())
                throw new ApiException("Invalid amount");
            BigDecimal amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new ApiException("Invalid amount");
            if (firstName == null || firstName.isEmpty())
                throw new ApiException("Missing first name");
            if (lastName == null || lastName.isEmpty())
                throw new ApiException("Missing last name");
            if (donorEmail == null || donorEmail.isEmpty())
                throw new ApiException("Missing donor email");
            if (billingZip == null || billingZip.isEmpty())
                throw new ApiException("Missing billing zip");

            firstName = firstName.trim();
            lastName = lastName.trim();

            System.out.println("[one-time] Processing charge: amount=" + amount + " donor=" + donorEmail);

            CreditCardData card = new CreditCardData();
            card.setToken(paymentReference);
            card.setCardHolderName(firstName + " " + lastName);

            Address address = new Address();
            address.setPostalCode(sanitizePostalCode(billingZip));

            Transaction transaction = card.charge(amount)
                    .withAllowDuplicates(true)
                    .withCurrency("USD")
                    .withAddress(address)
                    .execute();

            if (!"00".equals(transaction.getResponseCode())) {
                System.out.println("[one-time] Charge declined: " + transaction.getResponseMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("code", "PAYMENT_DECLINED");
                error.put("details", transaction.getResponseMessage());
                response.getWriter().write(gson.toJson(failResponse("Payment processing failed", error)));
                return;
            }

            System.out.println("[one-time] Charge success: transactionId=" + transaction.getTransactionId() + " responseCode=00");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("transactionId", transaction.getTransactionId());
            data.put("status", transaction.getResponseMessage());
            data.put("amount", amount);
            data.put("currency", "USD");
            data.put("firstName", firstName);
            data.put("lastName", lastName);
            data.put("donorEmail", donorEmail);
            data.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Thank you for your donation!");
            result.put("data", data);
            response.getWriter().write(gson.toJson(result));

        } catch (ApiException e) {
            System.out.println("[one-time] ApiException: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "API_ERROR");
            error.put("details", e.getMessage());
            response.getWriter().write(gson.toJson(failResponse("Payment processing failed", error)));
        } catch (Exception e) {
            System.out.println("[one-time] Unexpected error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "SYSTEM_ERROR");
            error.put("details", e.getMessage());
            response.getWriter().write(gson.toJson(failResponse("An unexpected error occurred", error)));
        }
    }

    private void processRecurring(JsonObject body, HttpServletResponse response) throws IOException {
        try {
            String amountStr = getStr(body, "amount");
            if (amountStr == null || amountStr.isEmpty())
                throw new ApiException("Invalid amount");
            BigDecimal amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new ApiException("Invalid amount");

            String[] requiredFields = {
                "payment_reference", "first_name", "last_name",
                "donor_email", "frequency", "billing_zip",
                "phone", "street_address", "city", "state", "country"
            };
            for (String field : requiredFields) {
                String val = getStr(body, field);
                if (val == null || val.isEmpty())
                    throw new ApiException("Missing required field: " + field);
            }

            String paymentReference = getStr(body, "payment_reference");
            String firstName = getStr(body, "first_name").trim();
            String lastName = getStr(body, "last_name").trim();
            String donorEmail = getStr(body, "donor_email");
            String frequency = getStr(body, "frequency");
            String billingZip = getStr(body, "billing_zip");

            System.out.println("[recurring] Processing schedule: amount=" + amount
                    + " frequency=" + frequency + " donor=" + donorEmail);

            Customer customer = new Customer();
            customer.setId(UUID.randomUUID().toString());
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setEmail(donorEmail);
            customer.setStatus("Active");

            Address address = new Address();
            address.setStreetAddress1(getStr(body, "street_address").trim());
            address.setCity(getStr(body, "city").trim());
            address.setProvince(getStr(body, "state").trim());
            address.setPostalCode(sanitizePostalCode(billingZip));
            address.setCountry(getStr(body, "country").trim());
            customer.setAddress(address);
            customer.setWorkPhone(getStr(body, "phone").trim());

            Customer savedCustomer = customer.create();
            System.out.println("[recurring] Customer created: key=" + savedCustomer.getKey());

            CreditCardData card = new CreditCardData();
            card.setToken(paymentReference);

            var paymentMethod = savedCustomer.addPaymentMethod(UUID.randomUUID().toString(), card).create();
            System.out.println("[recurring] Payment method created: key=" + paymentMethod.getKey());

            Date startDate;
            String startDateStr = getStr(body, "start_date");
            if (startDateStr != null && !startDateStr.isEmpty()) {
                startDate = new SimpleDateFormat("yyyy-MM-dd").parse(startDateStr);
            } else {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                startDate = cal.getTime();
            }

            var scheduleBuilder = paymentMethod.addSchedule(UUID.randomUUID().toString())
                    .withStatus("Active")
                    .withAmount(amount)
                    .withCurrency("USD")
                    .withStartDate(startDate)
                    .withFrequency(mapFrequency(frequency));

            String durationType = getStr(body, "duration_type");
            if ("end_date".equals(durationType)) {
                String endDateStr = getStr(body, "end_date");
                if (endDateStr != null && !endDateStr.isEmpty()) {
                    scheduleBuilder.withEndDate(new SimpleDateFormat("yyyy-MM-dd").parse(endDateStr));
                }
            } else if ("num_payments".equals(durationType)) {
                String numPaymentsStr = getStr(body, "num_payments");
                if (numPaymentsStr != null && !numPaymentsStr.isEmpty()) {
                    scheduleBuilder.withNumberOfPayments(Integer.parseInt(numPaymentsStr));
                }
            }

            Schedule savedSchedule = scheduleBuilder.create();
            String startDateFormatted = new SimpleDateFormat("yyyy-MM-dd").format(startDate);
            System.out.println("[recurring] Schedule created: key=" + savedSchedule.getKey()
                    + " startDate=" + startDateFormatted);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scheduleKey", savedSchedule.getKey());
            data.put("customerKey", savedCustomer.getKey());
            data.put("paymentMethodKey", paymentMethod.getKey());
            data.put("amount", amount);
            data.put("currency", "USD");
            data.put("frequency", frequency);
            data.put("startDate", startDateFormatted);
            data.put("firstName", firstName);
            data.put("lastName", lastName);
            data.put("donorEmail", donorEmail);
            data.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Recurring donation created successfully!");
            result.put("data", data);
            response.getWriter().write(gson.toJson(result));

        } catch (ApiException e) {
            System.out.println("[recurring] ApiException: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "API_ERROR");
            error.put("details", e.getMessage());
            response.getWriter().write(gson.toJson(failResponse("Recurring donation setup failed", error)));
        } catch (Exception e) {
            System.out.println("[recurring] Unexpected error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "SYSTEM_ERROR");
            error.put("details", e.getMessage());
            response.getWriter().write(gson.toJson(failResponse("An unexpected error occurred", error)));
        }
    }

    private String sanitizePostalCode(String postalCode) {
        if (postalCode == null) return "";
        String sanitized = postalCode.replaceAll("[^a-zA-Z0-9-]", "");
        return sanitized.length() > 10 ? sanitized.substring(0, 10) : sanitized;
    }

    private ScheduleFrequency mapFrequency(String frequency) {
        if (frequency == null) return ScheduleFrequency.Monthly;
        switch (frequency.toLowerCase()) {
            case "quarterly": return ScheduleFrequency.Quarterly;
            case "annually":  return ScheduleFrequency.Annually;
            default:          return ScheduleFrequency.Monthly;
        }
    }

    private String getStr(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private Map<String, Object> simpleError(String message, String error) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("message", message);
        map.put("error", error);
        return map;
    }

    private Map<String, Object> failResponse(String message, Map<String, Object> error) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("message", message);
        map.put("error", error);
        return map;
    }
}
