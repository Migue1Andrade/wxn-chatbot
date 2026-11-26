import angular from 'angular';
angular.module('wxn.chats').factory('ReportsService', [
  '$http',
  'environment',
  function ($http, environment) {
    return {
      exportConversations: (filters) =>
        $http
          .get(environment.apiBaseUrl + '/api/admin/analytics/conversations.csv', {
            responseType: 'blob',
            timeout: 120000,
            params: filters,
          })
          .then((response) => response.data),
    };
  },
]);
