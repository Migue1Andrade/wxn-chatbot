import angular from 'angular';
angular.module('wxn.chats').factory('ConversationActivitiesService', [
  'ApiClientService',
  function (api) {
    return {
      listEvents(id) {
        return api.allPages('/conversations/' + encodeURIComponent(id) + '/events');
      },
    };
  },
]);
