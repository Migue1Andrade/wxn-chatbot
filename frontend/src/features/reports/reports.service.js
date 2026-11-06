import angular from 'angular';
angular.module('wxn.chats').factory('ReportsService', [
  '$http',
  'environment',
  function ($http, environment) {
    return {
      exportConversations: () =>
        $http
          .get(environment.apiBaseUrl + '/api/admin/analytics/conversations.csv', {
            responseType: 'blob',
            timeout: 120000,
          })
          .then((response) => response.data),
    };
  },
]);
