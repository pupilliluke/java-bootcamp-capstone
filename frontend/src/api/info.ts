import { http } from './http'

// /actuator/info, narrowed to what the connection panel renders. The server
// side curates this (ConnectionInfoContributor): profile, a sanitized database
// target, the Kafka names, the build block from build-info.properties, and the
// image revision when one exists. Everything optional -- an older backend
// without the contributor answers with an empty object, and the panel must
// degrade to "not reported" rather than break.
export interface ConnectionsInfo {
  profile?: string
  database?: string
  kafka?: {
    bootstrap?: string
    topic?: string
    consumerGroup?: string
  }
}

export interface InfoResponse {
  connections?: ConnectionsInfo
  build?: {
    artifact?: string
    version?: string
    time?: string
  }
  revision?: string
}

export const infoApi = {
  // intercept401: false for the same reason health does it -- a diagnostic
  // that signs you out when it fails is worse than one that says so.
  get(signal?: AbortSignal): Promise<InfoResponse> {
    return http<InfoResponse>('/actuator/info', {}, signal, { intercept401: false })
  },
}
