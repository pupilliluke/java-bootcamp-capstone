// Google Sign-In OAuth 2.0 client id. Public by design — it ships in this bundle
// and is not a secret — so a working default is committed and VITE_GOOGLE_CLIENT_ID
// overrides it per environment. Must match the backend's GOOGLE_CLIENT_ID and the
// OAuth client's "Authorized JavaScript origins" in Google Cloud Console.
export const GOOGLE_CLIENT_ID =
  import.meta.env.VITE_GOOGLE_CLIENT_ID ||
  '398192114075-fb8j3jdt7m2qrqpe6957g5o57ajppfhn.apps.googleusercontent.com'

// Whether Google Sign-In is loaded at all. OFF by default (opt-in): the button
// and its accounts.google.com script are injected only when VITE_ENABLE_GSI is
// exactly 'true'.
//
// The default is off on purpose. Google refuses any serving origin that is not in
// the OAuth client's "Authorized JavaScript origins" list, returning a full-page
// "Error 400: origin_mismatch" — so a deploy to a new domain (neuralcrm.xyz, or
// any *.vercel.app preview) shows a broken button instead of a login. Enable it
// only in an environment whose exact origin is registered on the client in Google
// Cloud project 398192114075 — see frontend/README.md. CI and the Playwright e2e
// run leave it off. When off, the script is never injected and the button is not
// rendered.
export const GSI_ENABLED = import.meta.env.VITE_ENABLE_GSI === 'true'
