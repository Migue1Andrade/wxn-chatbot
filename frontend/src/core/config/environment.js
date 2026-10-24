export const environment = Object.freeze({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:4001',
  wsUrl: import.meta.env.VITE_WS_URL || 'ws://localhost:4001/ws',
  companyId: Number(import.meta.env.VITE_DEV_COMPANY_ID || 1),
  // This is an explicitly temporary browser-exposed development credential.
  devApiKey: import.meta.env.DEV ? import.meta.env.VITE_DEV_API_KEY || '' : '',
});
