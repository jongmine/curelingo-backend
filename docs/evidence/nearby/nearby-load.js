import http from 'k6/http';
import { check } from 'k6';

const rate = Number(__ENV.RATE || 2);
const duration = __ENV.DURATION || '60s';
const baseUrl = __ENV.BASE_URL || 'http://10.40.0.6:8080';
const preAllocatedVUs = Number(__ENV.PREALLOCATED_VUS || 32);
const maxVUs = Number(__ENV.MAX_VUS || 256);
const observeOnly = __ENV.OBSERVE_ONLY === 'true';
const url = `${baseUrl}/api/hospitals/nearby?latitude=37.5665&longitude=126.9780&radiusMeters=3000&departmentCode=D001&limit=50`;

export const options = {
  systemTags: ['scenario', 'name', 'method', 'status', 'expected_response', 'error', 'error_code'],
  scenarios: {
    nearby_search: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  ...(observeOnly ? {} : { thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '20s' }],
    http_req_duration: [{ threshold: 'p(95)<5000', abortOnFail: true, delayAbortEval: '30s' }],
    dropped_iterations: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '20s' }],
    checks: [{ threshold: 'rate>0.99', abortOnFail: true, delayAbortEval: '20s' }],
  } }),
};

export default function () {
  const response = http.get(url, {
    timeout: '5s',
    tags: { endpoint: 'nearby-search', name: '/api/hospitals/nearby' },
  });

  let rows = null;
  try {
    rows = response.json();
  } catch (_) {
    // The failed parse is reported as a check failure below.
  }

  const isArray = Array.isArray(rows);
  const uniqueHpids = isArray && new Set(rows.map((row) => row.hpid)).size === rows.length;
  const inRadiusAndSorted = isArray && rows.every((row, index) =>
    Number.isFinite(row.distanceMeters)
      && row.distanceMeters <= 3000
      && (index === 0 || rows[index - 1].distanceMeters <= row.distanceMeters));

  check(response, {
    'HTTP 200': (res) => res.status === 200,
    'JSON array at most 50': () => isArray && rows.length <= 50,
    'unique hospital IDs': () => uniqueHpids,
    'distance ascending and within radius': () => inRadiusAndSorted,
  });
}

export function handleSummary(data) {
  const metrics = data.metrics || {};
  const names = ['http_reqs', 'http_req_duration', 'http_req_failed', 'dropped_iterations', 'checks', 'data_received'];
  const summary = Object.fromEntries(names.map((name) => [name, metrics[name] || null]));
  return {
    [__ENV.SUMMARY_FILE]: JSON.stringify(data, null, 2),
    stdout: `${JSON.stringify(summary, null, 2)}\n`,
  };
}
