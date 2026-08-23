import type { CreateInteraction, Interaction } from '../types/customer'
import { http } from './http'

// Backend contract (InteractionController): POST /api/interactions with a
// CreateInteractionRequest { customerId, channel, notes }. Returns 202 Accepted
// with an InteractionEvent. Fire-and-forget over Kafka — there is no GET to list
// interactions, so the timeline is kept locally in the UI.
interface InteractionEvent {
  interactionId?: string
  channel: string
  notes: string
  occurredAt?: string
}

export const interactionsApi = {
  async create(body: CreateInteraction, signal?: AbortSignal): Promise<Interaction> {
    const event = await http<InteractionEvent>(
      '/api/interactions',
      { method: 'POST', body: JSON.stringify(body) },
      signal,
    )
    return {
      channel: body.channel,
      notes: body.notes,
      interactionId: event?.interactionId,
      createdAt: event?.occurredAt ?? new Date().toISOString(),
    }
  },
}
