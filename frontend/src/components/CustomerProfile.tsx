import type { Customer } from '../types/customer'

export default function CustomerProfile({ customer }: { customer: Customer }) {
  return (
    <div>
      <p>
        <strong>{customer.fullName}</strong> ({customer.customerId})
      </p>
      <p>Status: {customer.status}</p>
      <p>Email: {customer.email}</p>
      <p>Phone: {customer.phone || '—'}</p>
    </div>
  )
}
