import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://app:8080';
const workloadMode = __ENV.WORKLOAD_MODE || 'io';
const latencyMs = __ENV.LATENCY_MS || '75';
const cpuIterations = __ENV.CPU_ITERATIONS || '0';

export const options = {
  scenarios: {
    steady_ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },
        { duration: '15s', target: 30 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<750'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const response = http.get(
    `${baseUrl}/api/work?mode=${workloadMode}&latencyMs=${latencyMs}&cpuIterations=${cpuIterations}`,
    { tags: { endpoint: `simulated-${workloadMode}-work` } },
  );

  check(response, {
    'response status is 200': (res) => res.status === 200,
    'response describes selected mode': (res) => res.json('mode') === workloadMode,
  });

  sleep(0.1);
}

export function handleSummary(data) {
  return {
    '/results/summary.json': JSON.stringify(data, null, 2),
  };
}
