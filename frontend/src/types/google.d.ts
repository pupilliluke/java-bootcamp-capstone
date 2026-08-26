// Minimal typings for the Google Identity Services client (loaded from
// https://accounts.google.com/gsi/client in index.html). Only the ID-token
// ("Sign in with Google") surface we use is declared.
export {}

interface GoogleCredentialResponse {
  credential: string
  select_by?: string
}

interface GoogleButtonOptions {
  type?: 'standard' | 'icon'
  theme?: 'outline' | 'filled_blue' | 'filled_black'
  size?: 'small' | 'medium' | 'large'
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
  shape?: 'rectangular' | 'pill' | 'circle' | 'square'
  logo_alignment?: 'left' | 'center'
  width?: number
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string
            callback: (response: GoogleCredentialResponse) => void
            auto_select?: boolean
            cancel_on_tap_outside?: boolean
          }) => void
          renderButton: (parent: HTMLElement, options: GoogleButtonOptions) => void
          prompt: () => void
        }
      }
    }
  }
}
