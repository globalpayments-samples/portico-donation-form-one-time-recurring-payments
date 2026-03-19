<?php

declare(strict_types=1);

/**
 * Donation Payment Router
 *
 * This script routes donation requests to the appropriate payment processor
 * based on the payment type (one-time or recurring).
 *
 * PHP version 7.4 or higher
 *
 * @category  Payment_Processing
 * @package   GlobalPayments_Sample
 * @author    Global Payments
 * @license   MIT License
 * @link      https://github.com/globalpayments
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

ini_set('display_errors', '0');

$inputData = json_decode(file_get_contents('php://input'), true);

if (empty($inputData['payment_type'])) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Missing payment_type',
        'error' => 'payment_type must be "one-time" or "recurring"'
    ]);
    exit;
}

$paymentType = $inputData['payment_type'];

error_log('[donation] Request received: payment_type=' . ($inputData['payment_type'] ?? 'missing'));

if ($paymentType === 'one-time') {
    error_log('[donation] Routing to one-time processor');
    require __DIR__ . '/process-one-time.php';
} elseif ($paymentType === 'recurring') {
    error_log('[donation] Routing to recurring processor');
    require __DIR__ . '/process-recurring.php';
} else {
    error_log('[donation] ERROR: Invalid payment_type: ' . $paymentType);
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Invalid payment_type',
        'error' => 'payment_type must be "one-time" or "recurring"'
    ]);
}
