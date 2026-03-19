import express from 'express';
import * as dotenv from 'dotenv';
import { randomUUID } from 'crypto';
import {
    ServicesContainer,
    PorticoConfig,
    Address,
    CreditCardData,
    Customer,
    RecurringPaymentMethod,
    ScheduleFrequency,
    ApiError,
} from 'globalpayments-api';

dotenv.config();

const app = express();
const port = process.env.PORT || 8000;

app.use(express.static('.'));
app.use(express.json());

const config = new PorticoConfig();
config.secretApiKey = process.env.SECRET_API_KEY;
config.developerId = '000000';
config.versionNumber = '0000';
config.serviceUrl = 'https://cert.api2.heartlandportico.com';
ServicesContainer.configureService(config);

const sanitizePostalCode = (postalCode) => {
    if (!postalCode) return '';
    return postalCode.replace(/[^a-zA-Z0-9-]/g, '').slice(0, 10);
};

const mapFrequency = (frequency) => {
    const map = {
        monthly: ScheduleFrequency.Monthly,
        quarterly: ScheduleFrequency.Quarterly,
        annually: ScheduleFrequency.Annually,
    };
    return map[(frequency || '').toLowerCase()] ?? ScheduleFrequency.Monthly;
};

app.get('/config', (req, res) => {
    const publicApiKey = process.env.PUBLIC_API_KEY;
    if (!publicApiKey) {
        return res.status(500).json({
            success: false,
            message: 'Payment configuration is unavailable',
        });
    }
    res.json({
        success: true,
        data: { publicApiKey },
    });
});

app.post('/process-donation', async (req, res) => {
    const body = req.body;
    const paymentType = body.payment_type;

    if (!paymentType) {
        return res.status(400).json({
            success: false,
            message: 'Missing payment_type',
            error: 'payment_type must be "one-time" or "recurring"',
        });
    }

    console.log(`[donation] Request received: payment_type=${paymentType}`);

    if (paymentType === 'one-time') {
        console.log('[donation] Routing to one-time processor');
        return processOneTime(body, res);
    } else if (paymentType === 'recurring') {
        console.log('[donation] Routing to recurring processor');
        return processRecurring(body, res);
    } else {
        console.log(`[donation] ERROR: Invalid payment_type: ${paymentType}`);
        return res.status(400).json({
            success: false,
            message: 'Invalid payment_type',
            error: 'payment_type must be "one-time" or "recurring"',
        });
    }
});

async function processOneTime(body, res) {
    try {
        if (!body.payment_reference) throw new Error('Missing payment reference');
        if (!body.amount || parseFloat(body.amount) <= 0) throw new Error('Invalid amount');
        if (!body.first_name) throw new Error('Missing first name');
        if (!body.last_name) throw new Error('Missing last name');
        if (!body.donor_email) throw new Error('Missing donor email');
        if (!body.billing_zip) throw new Error('Missing billing zip');

        const amount = parseFloat(body.amount);
        const firstName = body.first_name.trim();
        const lastName = body.last_name.trim();
        const donorEmail = body.donor_email;

        console.log(`[one-time] Processing charge: amount=${amount} donor=${donorEmail}`);

        const card = new CreditCardData();
        card.token = body.payment_reference;
        card.cardHolderName = `${firstName} ${lastName}`;

        const address = new Address();
        address.postalCode = sanitizePostalCode(body.billing_zip);

        const response = await card.charge(amount)
            .withAllowDuplicates(true)
            .withCurrency('USD')
            .withAddress(address)
            .execute();

        if (response.responseCode !== '00') {
            console.log(`[one-time] Charge declined: ${response.responseMessage}`);
            return res.status(400).json({
                success: false,
                message: 'Payment processing failed',
                error: { code: 'PAYMENT_DECLINED', details: response.responseMessage },
            });
        }

        console.log(`[one-time] Charge success: transactionId=${response.transactionId} responseCode=00`);
        return res.json({
            success: true,
            message: 'Thank you for your donation!',
            data: {
                transactionId: response.transactionId,
                status: response.responseMessage,
                amount,
                currency: 'USD',
                firstName,
                lastName,
                donorEmail,
                timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
            },
        });
    } catch (e) {
        if (e instanceof ApiError) {
            console.log(`[one-time] ApiException: ${e.message}`);
            return res.status(400).json({
                success: false,
                message: 'Payment processing failed',
                error: { code: 'API_ERROR', details: e.message },
            });
        }
        console.log(`[one-time] Unexpected error: ${e.message}`);
        return res.status(500).json({
            success: false,
            message: 'An unexpected error occurred',
            error: { code: 'SYSTEM_ERROR', details: e.message },
        });
    }
}

async function processRecurring(body, res) {
    try {
        if (!body.amount || parseFloat(body.amount) <= 0) throw new Error('Invalid amount');

        const requiredFields = [
            'payment_reference', 'first_name', 'last_name',
            'donor_email', 'frequency', 'billing_zip',
            'phone', 'street_address', 'city', 'state', 'country',
        ];
        for (const field of requiredFields) {
            if (!body[field]) throw new Error(`Missing required field: ${field}`);
        }

        const amount = parseFloat(body.amount);
        const donorEmail = body.donor_email;
        const frequency = body.frequency;
        const firstName = body.first_name.trim();
        const lastName = body.last_name.trim();

        console.log(`[recurring] Processing schedule: amount=${amount} frequency=${frequency} donor=${donorEmail}`);

        const customer = new Customer();
        customer.id = randomUUID();
        customer.firstName = firstName;
        customer.lastName = lastName;
        customer.email = donorEmail;
        customer.status = 'Active';
        customer.address = new Address();
        customer.address.streetAddress1 = body.street_address.trim();
        customer.address.city = body.city.trim();
        customer.address.province = body.state.trim();
        customer.address.postalCode = sanitizePostalCode(body.billing_zip);
        customer.address.country = body.country.trim();
        customer.workPhone = body.phone.trim();

        await customer.create();
        const savedCustomer = await Customer.find(customer.id);
        console.log(`[recurring] Customer created: key=${savedCustomer.key}`);

        const card = new CreditCardData();
        card.token = body.payment_reference;

        const pmId = randomUUID();
        await savedCustomer.addPaymentMethod(pmId, card).create();
        const paymentMethod = await RecurringPaymentMethod.find(pmId);
        console.log(`[recurring] Payment method created: key=${paymentMethod.key}`);

        let startDate;
        if (body.start_date) {
            startDate = new Date(body.start_date);
        } else {
            const now = new Date();
            startDate = new Date(now.getFullYear(), now.getMonth() + 1, 1);
        }

        const scheduleBuilder = paymentMethod.addSchedule(randomUUID())
            .withStatus('Active')
            .withAmount(amount)
            .withCurrency('USD')
            .withStartDate(startDate)
            .withFrequency(mapFrequency(frequency));

        const durationType = body.duration_type || '';
        if (durationType === 'end_date' && body.end_date) {
            scheduleBuilder.withEndDate(new Date(body.end_date));
        } else if (durationType === 'num_payments' && body.num_payments) {
            scheduleBuilder.withNumberOfPayments(parseInt(body.num_payments, 10));
        }

        const savedSchedule = await scheduleBuilder.create();
        const startDateStr = startDate.toISOString().slice(0, 10);
        console.log(`[recurring] Schedule created: key=${savedSchedule.key} startDate=${startDateStr}`);

        return res.json({
            success: true,
            message: 'Recurring donation created successfully!',
            data: {
                scheduleKey: savedSchedule.key,
                customerKey: savedCustomer.key,
                paymentMethodKey: paymentMethod.key,
                amount,
                currency: 'USD',
                frequency,
                startDate: startDateStr,
                firstName,
                lastName,
                donorEmail,
                timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
            },
        });
    } catch (e) {
        if (e instanceof ApiError) {
            console.log(`[recurring] ApiException: ${e.message}`);
            return res.status(400).json({
                success: false,
                message: 'Recurring donation setup failed',
                error: { code: 'API_ERROR', details: e.message },
            });
        }
        console.log(`[recurring] Unexpected error: ${e.message}`);
        return res.status(500).json({
            success: false,
            message: 'An unexpected error occurred',
            error: { code: 'SYSTEM_ERROR', details: e.message },
        });
    }
}

app.listen(port, '0.0.0.0', () => {
    console.log(`Server running at http://localhost:${port}`);
});
