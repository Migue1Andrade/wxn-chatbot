import angular from 'angular';
angular.module('wxn.channels').factory('ChannelsService', [
  'ApiClientService',
  function (api) {
    return {
      simulate: (outside, input, fromApi = false) =>
        api.post('/dev/whatsapp/mock/' + (outside ? (fromApi ? 'api-echo' : 'from-me') : 'inbound'), input),
      scenario: (channelId, scenario) => api.post('/dev/whatsapp/mock/scenario', { channelId, scenario }),
      reconcile: (id, input) => api.post('/admin/channels/' + id + '/reconcile', input),
      webhooks: (id) => api.get('/admin/channels/' + id + '/webhook-inbox'),
      retryWebhook: (id, key) => api.post('/admin/channels/' + id + '/webhook-inbox/retry', { key }),
      diagnostic: (id) => api.get('/admin/channels/' + id + '/diagnostic'),
      qr: (id) => api.post('/admin/channels/' + id + '/qr', {}),
      operate: (id, input) => api.post('/admin/channels/' + id + '/operation', input),
      policy: (id, enabled) => api.post('/admin/channels/' + id + '/remote-read-policy', { enabled }),
      register: (id, input) => api.post('/admin/channels/' + id + '/webhooks', input),
      create: (input) => api.post('/whatsapp/channels', input),
      listChannels() {
        return api.get('/whatsapp/channels');
      },
    };
  },
]);
