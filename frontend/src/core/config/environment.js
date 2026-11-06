export const environment = Object.freeze({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:4001',
  wsUrl: import.meta.env.VITE_WS_URL || 'ws://localhost:4001/ws',

  // This is an explicitly temporary browser-exposed development credential.
  // The local Docker frontend is served as a production build by Nginx, so it
  // must still receive the development key when running the local compose stack.
  devApiKey: import.meta.env.VITE_DEV_API_KEY || '',
  skipWhatsAppSetup: import.meta.env.VITE_SKIP_WHATSAPP_SETUP === 'true',
});
