import angular from 'angular';
angular.module('wxn.users').factory('UsersService', [
  'ApiClientService',
  function (api) {
    return {
      listUsers() {
        return api.get('/users');
      },
    };
  },
]);
