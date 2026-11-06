import angular from 'angular';
angular.module('wxn.chats').factory('SlaPolicyService', [
  'ApiClientService',
  function (api) {
    return {
      load: () => api.get('/admin/settings/sla'),
      save: (policy) => api.put('/admin/settings/sla', policy),
    };
  },
]);
