import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    concurrent_transfers: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 1,
      maxDuration: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'], // SLA: latencia p95 menor a 500 ms
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080/api/v1';
const SOURCE_ACCOUNT_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'; // Saldo 1000.00 USD
const DEST_ACCOUNT_ID = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22';

export default function () {
  const vuId = __VU;
  const iter = __ITER;
  const idempotencyKey = `K6-CONCURRENCY-KEY-VU${vuId}-IT${iter}-${Date.now()}`;

  const payload = JSON.stringify({
    sourceAccountId: SOURCE_ACCOUNT_ID,
    destinationAccountId: DEST_ACCOUNT_ID,
    amount: 50.00,
    currency: 'USD',
    description: `Prueba de carga k6 - VU ${vuId}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  };

  const res = http.post(`${BASE_URL}/transactions/transfers`, payload, params);

  check(res, {
    'Status es 201 (Creado) o 422 (Saldo Insuficiente)': (r) => r.status === 201 || r.status === 422,
    'Respuesta contiene JSON válido': (r) => r.headers['Content-Type'] && r.headers['Content-Type'].includes('application/json'),
  });

  sleep(0.1);
}
