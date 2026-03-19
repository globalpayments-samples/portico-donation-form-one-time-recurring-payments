<?php

declare(strict_types=1);

/**
 * One-Time Donation Processing Script
 *
 * This script demonstrates one-time donation processing using the Global Payments SDK.
 * It handles tokenized card data and billing information to process one-time donations
 * securely through the Portico API.
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
use GlobalPayments\Api\Entities\Exceptions\ApiException;
use GlobalPayments\Api\PaymentMethods\CreditCardData;
use GlobalPayments\Api\ServiceConfigs\Gateways\PorticoConfig;
use GlobalPayments\Api\ServicesContainer;

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

configureSdk();

try {
    if (empty($inputData['payment_reference'])) {
        throw new ApiException('Missing payment reference');
    }

    if (empty($inputData['amount']) || floatval($inputData['amount']) <= 0) {
        throw new ApiException('Invalid amount');
    }

    if (empty($inputData['first_name'])) {
        throw new ApiException('Missing first name');
    }

    if (empty($inputData['last_name'])) {
        throw new ApiException('Missing last name');
    }

    if (empty($inputData['donor_email'])) {
        throw new ApiException('Missing donor email');
    }

    if (empty($inputData['billing_zip'])) {
        throw new ApiException('Missing billing zip');
    }

    $paymentReference = $inputData['payment_reference'];
    $amount = floatval($inputData['amount']);
    $firstName = trim($inputData['first_name']);
    $lastName = trim($inputData['last_name']);
    $donorEmail = $inputData['donor_email'];

    error_log('[one-time] Processing charge: amount=' . $amount . ' donor=' . $donorEmail);

    $card = new CreditCardData();
    $card->token = $paymentReference;
    $card->cardHolderName = $firstName . ' ' . $lastName;

    $address = new Address();
    $address->postalCode = sanitizePostalCode($inputData['billing_zip']);

    $response = $card->charge($amount)
        ->withAllowDuplicates(true)
        ->withCurrency('USD')
        ->withAddress($address)
        ->execute();

    if ($response->responseCode !== '00') {
        error_log('[one-time] Charge declined: ' . $response->responseMessage);
        http_response_code(400);
        echo json_encode([
            'success' => false,
            'message' => 'Payment processing failed',
            'error' => [
                'code' => 'PAYMENT_DECLINED',
                'details' => $response->responseMessage
            ]
        ]);
        exit;
    }

    error_log('[one-time] Charge success: transactionId=' . $response->transactionId . ' responseCode=' . $response->responseCode);
    echo json_encode([
        'success' => true,
        'message' => 'Thank you for your donation!',
        'data' => [
            'transactionId' => $response->transactionId,
            'status' => $response->responseMessage,
            'amount' => $amount,
            'currency' => 'USD',
            'firstName' => $firstName,
            'lastName' => $lastName,
            'donorEmail' => $donorEmail,
            'timestamp' => date('Y-m-d H:i:s')
        ]
    ]);
} catch (ApiException $e) {
    error_log('[one-time] ApiException: ' . $e->getMessage());
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Payment processing failed',
        'error' => [
            'code' => 'API_ERROR',
            'details' => $e->getMessage()
        ]
    ]);
} catch (\Throwable $e) {
    error_log('[one-time] Unexpected error: ' . $e->getMessage());
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
