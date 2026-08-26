// Google Sign-In OAuth 2.0 client id. Public by design — it ships in this bundle
// and is not a secret — so a working default is committed and VITE_GOOGLE_CLIENT_ID
// overrides it per environment. Must match the backend's GOOGLE_CLIENT_ID and the
// OAuth client's "Authorized JavaScript origins" in Google Cloud Console.
export const GOOGLE_CLIENT_ID =
  import.meta.env.VITE_GOOGLE_CLIENT_ID ||
  '398192114075-fb8j3jdt7m2qrqpe6957g5o57ajppfhn.apps.googleusercontent.com'
