import angular from 'angular';

angular.module('wxn.core').factory('WhatsAppBootstrapService', [
  'ApiClientService',
  function (api) {
    const state = { status: null };
    const headers = (token) => (token ? { 'X-WXN-Bootstrap-Token': token } : undefined);
    return {
      state,
      status: () => api.get('/bootstrap/whatsapp/status').then((result) => (state.status = result)),
      initialize: (token) =>
        api
          .post('/bootstrap/whatsapp/initialize', {}, headers(token))
          .then((result) => (state.status = result)),
      refresh: (token) =>
        api.post('/bootstrap/whatsapp/refresh', {}, headers(token)).then((result) => (state.status = result)),
    };
  },
]);
