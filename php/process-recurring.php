<?php

declare(strict_types=1);

/**
 * Recurring Donation Processing Script
 *
 * This script demonstrates recurring donation processing using the Global Payments SDK.
 * It creates a Customer entity, adds a RecurringPaymentMethod, and sets up a Schedule
 * for recurring billing through the Portico API.
 *
 * PHP version 7.4 or higher
 *
 * @category  Payment_Processing
 * @package   GlobalPayments_Sample
 * @author    Global Payments
 * @license   MIT License
 * @link      https://github.com/globalpayments
 */

require_once 'vendor/autoload.php';

use Dotenv\Dotenv;
use GlobalPayments\Api\Entities\Address;
use GlobalPayments\Api\Entities\Customer;
use GlobalPayments\Api\Entities\Enums\ScheduleFrequency;
use GlobalPayments\Api\Entities\Exceptions\ApiException;
use GlobalPayments\Api\PaymentMethods\CreditCardData;
use GlobalPayments\Api\ServiceConfigs\Gateways\PorticoConfig;
use GlobalPayments\Api\ServicesContainer;
use DateTime;

ini_set('display_errors', '0');

/**
 * Configure the SDK
 *
 * Sets up the Global Payments SDK with necessary credentials and settings
 * loaded from environment variables.
 *
 * @return void
 */
function configureSdk(): void
{
    $dotenv = Dotenv::createImmutable(__DIR__);
    $dotenv->load();

    $config = new PorticoConfig();
    $config->secretApiKey = $_ENV['SECRET_API_KEY'];
    $config->developerId = '000000';
    $config->versionNumber = '0000';
    $config->serviceUrl = 'https://cert.api2.heartlandportico.com';

    ServicesContainer::configureService($config);
}

/**
 * Sanitize postal code by removing invalid characters
 *
 * @param string|null $postalCode The postal code to sanitize
 *
 * @return string Sanitized postal code containing only alphanumeric
 *                characters and hyphens, limited to 10 characters
 */
function sanitizePostalCode(?string $postalCode): string
{
    if ($postalCode === null) {
        return '';
    }

    $sanitized = preg_replace('/[^a-zA-Z0-9-]/', '', $postalCode);
    return substr($sanitized, 0, 10);
}

/**
 * Generate a UUID v4 string
 *
 * @return string UUID v4
 */
function generateUuidV4(): string
{
    $data = random_bytes(16);
    $data[6] = chr(ord($data[6]) & 0x0f | 0x40);
    $data[8] = chr(ord($data[8]) & 0x3f | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
}

/**
 * Map frequency string to ScheduleFrequency enum
 *
 * @param string $frequency The frequency string (monthly, quarterly, annually)
 *
 * @return string The corresponding ScheduleFrequency constant
 */
function mapFrequency(string $frequency): string
{
    $frequencyMap = [
        'monthly' => ScheduleFrequency::MONTHLY,
        'quarterly' => ScheduleFrequency::QUARTERLY,
        'annually' => ScheduleFrequency::ANNUALLY
    ];

    return $frequencyMap[strtolower($frequency)] ?? ScheduleFrequency::MONTHLY;
}

configureSdk();

try {
    if (empty($inputData['amount']) || floatval($inputData['amount']) <= 0) {
        throw new ApiException('Invalid amount');
    }

    $requiredFields = [
        'payment_reference', 'first_name', 'last_name',
        'donor_email', 'frequency', 'billing_zip',
        'phone', 'street_address', 'city', 'state', 'country'
    ];
    foreach ($requiredFields as $field) {
        if (empty($inputData[$field])) {
            throw new ApiException("Missing required field: $field");
        }
    }

    $paymentReference = $inputData['payment_reference'];
    $amount = floatval($inputData['amount']);
    $donorEmail = $inputData['donor_email'];
    $frequency = $inputData['frequency'];

    error_log('[recurring] Processing schedule: amount=' . $amount . ' frequency=' . $frequency . ' donor=' . $donorEmail);

    $customer = new Customer();
    $customer->id = generateUuidV4();
    $customer->firstName = trim($inputData['first_name']);
    $customer->lastName = trim($inputData['last_name']);
    $customer->email = $donorEmail;
    $customer->status = 'Active';
    $customer->address = new Address();
    $customer->address->streetAddress1 = trim($inputData['street_address']);
    $customer->address->city = trim($inputData['city']);
    $customer->address->province = trim($inputData['state']);
    $customer->address->postalCode = sanitizePostalCode($inputData['billing_zip']);
    $customer->address->country = trim($inputData['country']);
    $customer->workPhone = trim($inputData['phone']);

    $savedCustomer = $customer->create();
    error_log('[recurring] Customer created: key=' . $savedCustomer->key);

    $card = new CreditCardData();
    $card->token = $paymentReference;

    $paymentMethod = $savedCustomer->addPaymentMethod(generateUuidV4(), $card)->create();
    error_log('[recurring] Payment method created: key=' . $paymentMethod->key);

    $startDateStr = !empty($inputData['start_date'])
        ? $inputData['start_date']
        : (new DateTime('first day of next month'))->format('Y-m-d');
    $startDate = DateTime::createFromFormat('Y-m-d', $startDateStr);

    $scheduleBuilder = $paymentMethod->addSchedule(generateUuidV4())
        ->withStatus('Active')
        ->withAmount($amount)
        ->withCurrency('USD')
        ->withStartDate($startDate)
        ->withFrequency(mapFrequency($frequency));

    $durationType = $inputData['duration_type'] ?? '';
    if ($durationType === 'end_date' && !empty($inputData['end_date'])) {
        $scheduleBuilder->endDate = DateTime::createFromFormat('Y-m-d', $inputData['end_date']);
    } elseif ($durationType === 'num_payments' && !empty($inputData['num_payments'])) {
        $scheduleBuilder->numberOfPaymentsRemaining = intval($inputData['num_payments']);
    }

    $savedSchedule = $scheduleBuilder->create();
    error_log('[recurring] Schedule created: key=' . $savedSchedule->key . ' startDate=' . $startDate->format('Y-m-d'));

    echo json_encode([
        'success' => true,
        'message' => 'Recurring donation created successfully!',
        'data' => [
            'scheduleKey' => $savedSchedule->key,
            'customerKey' => $savedCustomer->key,
            'paymentMethodKey' => $paymentMethod->key,
            'amount' => $amount,
            'currency' => 'USD',
            'frequency' => $frequency,
            'startDate' => $startDate->format('Y-m-d'),
            'firstName' => trim($inputData['first_name']),
            'lastName' => trim($inputData['last_name']),
            'donorEmail' => $donorEmail,
            'timestamp' => date('Y-m-d H:i:s')
        ]
    ]);
} catch (ApiException $e) {
    error_log('[recurring] ApiException: ' . $e->getMessage());
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Recurring donation setup failed',
        'error' => [
            'code' => 'API_ERROR',
            'details' => $e->getMessage()
        ]
    ]);
} catch (\Throwable $e) {
    error_log('[recurring] Unexpected error: ' . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'An unexpected error occurred',
        'error' => [
            'code' => 'SYSTEM_ERROR',
            'details' => $e->getMessage()
        ]
    ]);
}
