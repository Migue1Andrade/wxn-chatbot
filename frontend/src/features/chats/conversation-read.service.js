import angular from 'angular';
angular.module('wxn.chats').factory('ConversationReadService', [
  'ApiClientService',
  function (api) {
    return {
      markRead(id, payload) {
        return api.post('/conversations/' + encodeURIComponent(id) + '/read', payload);
      },
    };
  },
]);
