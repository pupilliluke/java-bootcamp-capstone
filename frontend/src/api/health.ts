import { http } from './http'

// Actuator's health payload, narrowed to what the connection panel reads.
//
// Only `status` is guaranteed. `components` appears when the caller is
// authorized -- application.yml sets show-details: when-authorized, so an
// anonymous request gets UP/DOWN and nothing else, by design. Everything below
// the top level is therefore optional rather than assumed.
export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

export interface HealthComponent {
  status: HealthStatus
  details?: Record<string, unknown>
}

export interface HealthResponse {
  status: HealthStatus
  groups?: string[]
  components?: Record<string, HealthComponent>
}

export const healthApi = {
  // Relative, so it stays same-origin in every environment: the Vite proxy in
  // development, vercel.json's /actuator rewrite in a deployment, and the
  // ingress on the cluster. Never point this at an absolute backend host --
  // that is the CORS surface the same-origin stance in docs/threat-model.md
  // exists to avoid.
  //
  // intercept401: false because a 401 here should not sign the user out. The
  // panel reports what it found; it is a diagnostic, and a diagnostic that
  // logs you out when it fails is worse than one that says "unauthorized".
  get(signal?: AbortSignal): Promise<HealthResponse> {
    return http<HealthResponse>('/actuator/health', {}, signal, { intercept401: false })
  },
}
