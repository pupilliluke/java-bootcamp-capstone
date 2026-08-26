// Google Sign-In OAuth 2.0 client id. Public by design — it ships in this bundle
// and is not a secret — so a working default is committed and VITE_GOOGLE_CLIENT_ID
// overrides it per environment. Must match the backend's GOOGLE_CLIENT_ID and the
// OAuth client's "Authorized JavaScript origins" in Google Cloud Console.
export const GOOGLE_CLIENT_ID =
  import.meta.env.VITE_GOOGLE_CLIENT_ID ||
  '398192114075-fb8j3jdt7m2qrqpe6957g5o57ajppfhn.apps.googleusercontent.com'

// Whether Google Sign-In is loaded at all. On by default; set VITE_ENABLE_GSI=false
// to keep the Google script and widget out of a build entirely. The Playwright e2e
// run sets it false: the third-party script and its network calls add nothing to
// the customer journey under test and only introduce flake. When off, the script is
// never injected and the button is not rendered.
export const GSI_ENABLED = import.meta.env.VITE_ENABLE_GSI !== 'false'
