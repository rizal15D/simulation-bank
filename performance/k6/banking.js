import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const workload = (__ENV.WORKLOAD || 'history').toLowerCase();
const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/+$/, '');
const targetEnvironment = (__ENV.TARGET_ENV || 'local').toLowerCase();
const virtualUsers = positiveInteger('VUS', __ENV.VUS || '4');
const historyEntries = positiveInteger('HISTORY_ENTRIES', __ENV.HISTORY_ENTRIES || '1000');
const warmupDuration = __ENV.WARMUP_DURATION || '15s';
const measurementDuration = __ENV.DURATION || '30s';
const requestTimeout = __ENV.REQUEST_TIMEOUT || '30s';
const initialBalance = Number(__ENV.INITIAL_BALANCE || '1000000000');
const transferAccountMode = (__ENV.TRANSFER_ACCOUNT_MODE || 'isolated').toLowerCase();

if (!['history', 'transfer'].includes(workload)) {
  throw new Error('WORKLOAD must be history or transfer');
}
if (!Number.isFinite(initialBalance) || initialBalance <= 0) {
  throw new Error('INITIAL_BALANCE must be a positive number');
}
if (!['isolated', 'shared'].includes(transferAccountMode)) {
  throw new Error('TRANSFER_ACCOUNT_MODE must be isolated or shared');
}

const measuredLatency = new Trend('ledgerbank_latency', true);
const measuredErrors = new Rate('ledgerbank_errors');
const measuredRequests = new Counter('ledgerbank_requests');

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      exec: workload === 'history' ? 'warmHistory' : 'warmTransfer',
      vus: virtualUsers,
      duration: warmupDuration,
      gracefulStop: '0s',
      tags: { phase: 'warmup', flow: workload },
    },
    measurement: {
      executor: 'constant-vus',
      exec: workload === 'history' ? 'measureHistory' : 'measureTransfer',
      vus: virtualUsers,
      startTime: warmupDuration,
      duration: measurementDuration,
      gracefulStop: '0s',
      tags: { phase: 'measurement', flow: workload },
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    ledgerbank_errors: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'],
};

export function setup() {
  if (!['local', 'development', 'test'].includes(targetEnvironment)) {
    throw new Error('TARGET_ENV must be local, development, or test; production targets are refused');
  }

  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1000000000)}`;
  const email = `performance-${suffix}@example.test`;
  const password = 'performance-only-password';
  const register = jsonRequest('POST', '/api/v1/auth/register', {
    fullName: 'Performance Test User',
    email,
    password,
  }, null, 'register');
  const customerId = register.json('customerId');
  if (!customerId) {
    throw new Error('Registration response did not contain customerId');
  }

  const login = jsonRequest('POST', '/api/v1/auth/login', { email, password }, null, 'login');
  const token = login.json('accessToken');
  if (!token) {
    throw new Error('Login response did not contain accessToken');
  }

  // k6 allocates different VU ids to sequential scenarios. Two pairs per configured
  // VU keep both warm-up and measurement free from artificial cross-VU account locks.
  const pairs = [];
  const pairCount = workload === 'transfer' && transferAccountMode === 'shared' ? 1 : virtualUsers * 2;
  for (let index = 0; index < pairCount; index += 1) {
    const source = createAccount(customerId, token, `source-${index}`);
    const destination = createAccount(customerId, token, `destination-${index}`);
    deposit(source, initialBalance, token, `fund-source-${index}`);
    deposit(destination, initialBalance, token, `fund-destination-${index}`);
    pairs.push({ source, destination });
  }

  if (workload === 'history') {
    // The initial funding already creates one history entry.
    for (let index = 1; index < historyEntries; index += 1) {
      deposit(pairs[0].source, 1, token, `seed-history-${index}`);
    }
  }

  return { token, pairs, historyAccountId: pairs[0].source };
}

export function warmHistory(data) {
  historyRequest(data, false);
}

export function measureHistory(data) {
  historyRequest(data, true);
}

export function warmTransfer(data) {
  transferRequest(data, false);
}

export function measureTransfer(data) {
  transferRequest(data, true);
}

function historyRequest(data, measured) {
  const response = http.get(
    `${baseUrl}/api/v1/accounts/${data.historyAccountId}/transactions?page=0&size=20`,
    requestParameters(data.token, measured ? 'measurement' : 'warmup', 'history'),
  );
  record(response, 200, 'history', measured);
}

function transferRequest(data, measured) {
  const pairIndex = transferAccountMode === 'shared' ? 0 : (exec.vu.idInTest - 1) % data.pairs.length;
  const pair = data.pairs[pairIndex];
  const key = `perf-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
  const parameters = requestParameters(data.token, measured ? 'measurement' : 'warmup', 'transfer');
  parameters.headers['Idempotency-Key'] = key;
  const response = http.post(`${baseUrl}/api/v1/transfers`, JSON.stringify({
    sourceAccountId: pair.source,
    destinationAccountId: pair.destination,
    amount: 1,
    description: 'Performance baseline transfer',
  }), parameters);
  record(response, 201, 'transfer', measured);
}

function record(response, expectedStatus, operation, measured) {
  if (!measured) {
    if (response.status !== expectedStatus) {
      fail(`${operation} warm-up failed with HTTP ${response.status}`);
    }
    return;
  }

  const successful = check(response, {
    [`${operation} returned HTTP ${expectedStatus}`]: (result) => result.status === expectedStatus,
  }, { phase: 'measurement', flow: workload });
  const metricTags = { flow: workload, operation, account_mode: transferAccountMode };
  measuredLatency.add(response.timings.duration, metricTags);
  measuredErrors.add(!successful, metricTags);
  measuredRequests.add(1, metricTags);
}

function createAccount(customerId, token, label) {
  return jsonRequest('POST', '/api/v1/accounts', { customerId }, token, label).json('id');
}

function deposit(accountId, amount, token, label) {
  jsonRequest('POST', `/api/v1/accounts/${accountId}/deposit`, { amount }, token, label);
}

function jsonRequest(method, path, body, token, label) {
  const response = http.request(
    method,
    `${baseUrl}${path}`,
    JSON.stringify(body),
    requestParameters(token, 'setup', label),
  );
  const expectedStatus = path.endsWith('/login') ? 200 : 201;
  if (response.status !== expectedStatus) {
    throw new Error(`${label} failed with HTTP ${response.status}: ${response.body.slice(0, 500)}`);
  }
  return response;
}

function requestParameters(token, phase, operation) {
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return {
    headers,
    tags: { phase, flow: workload, operation },
    timeout: requestTimeout,
  };
}

function positiveInteger(name, value) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}
