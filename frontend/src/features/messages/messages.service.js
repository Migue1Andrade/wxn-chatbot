import angular from 'angular';
angular.module('wxn.messages').factory('MessagesService', [
  'ApiClientService',
  function (api) {
    const path = (id) => '/conversations/' + encodeURIComponent(id) + '/messages';
    return {
      listMessages(id, before) {
        return api.get(path(id), { limit: 30, ...(before ? { before } : {}) });
      },
      sendMessage(id, payload) {
        return api.post(path(id), payload);
      },
    };
  },
]);
