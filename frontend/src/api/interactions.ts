import type { CreateInteraction, Interaction } from '../types/customer'
import { http } from './http'

// NOTE: the backend interaction endpoint isn't built yet (InteractionController
// is empty). This POST will 404 until Tim finishes Lab 49 — the UI handles it.
export const interactionsApi = {
  create(
    customerId: string,
    body: CreateInteraction,
    signal?: AbortSignal,
  ): Promise<Interaction> {
    return http<Interaction>(
      `/api/customers/${encodeURIComponent(customerId)}/interactions`,
      { method: 'POST', body: JSON.stringify(body) },
      signal,
    )
  },
}
