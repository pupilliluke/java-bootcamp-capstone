import type { CustomerStatus } from '../types/customer'

export default function StatusBadge({ status }: { status: CustomerStatus }) {
  return <span className={`badge badge-${status}`}>{status}</span>
}
