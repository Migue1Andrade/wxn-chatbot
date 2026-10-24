import angular from 'angular';
angular.module('wxn.chats').factory('ChatsService', [
  'ApiClientService',
  function (api) {
    return {
      listConversations(filters, page = 0) {
        return api.get('/conversations', { ...filters, page, size: 30, paged: true });
      },
      getConversation(id) {
        return api.get('/conversations/' + encodeURIComponent(id));
      },
    };
  },
]);
