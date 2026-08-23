import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import CustomerWorkspace from './pages/CustomerWorkspace'

// Composition root. The guard decides between the loading, login and workspace
// views; the journey itself lives in CustomerWorkspace.
export default function App() {
  return (
    <AuthProvider>
      <ProtectedRoute>
        <CustomerWorkspace />
      </ProtectedRoute>
    </AuthProvider>
  )
}
