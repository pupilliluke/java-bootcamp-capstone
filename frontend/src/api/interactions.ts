import type { CreateInteraction, Interaction } from '../types/customer'
import { http } from './http'

// POST returns an InteractionEvent and GET returns an InteractionResponseDTO.
// Their durable interaction fields deliberately share the same shape.
interface InteractionResponse {
  interactionId: string
  customerId: string
  channel: Interaction['channel']
  notes: string
  occurredAt: string
}

export const interactionsApi = {
  async create(body: CreateInteraction, signal?: AbortSignal): Promise<Interaction> {
    const event = await http<InteractionResponse>(
      '/api/v1/interactions',
      { method: 'POST', body: JSON.stringify(body) },
      signal,
    )
    return toInteraction(event)
  },

  async list(customerId: string, signal?: AbortSignal): Promise<Interaction[]> {
    const interactions = await http<InteractionResponse[]>(
      `/api/v1/customers/${encodeURIComponent(customerId)}/interactions`,
      {},
      signal,
    )
    return interactions.map(toInteraction)
  },
}

function toInteraction(response: InteractionResponse): Interaction {
  return {
    interactionId: response.interactionId,
    customerId: response.customerId,
    channel: response.channel,
    notes: response.notes,
    createdAt: response.occurredAt,
  }
}
