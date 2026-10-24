import angular from 'angular';
angular.module('wxn.members').factory('MembersService', [
  'ApiClientService',
  function (api) {
    return {
      listMembers() {
        return api.get('/members');
      },
    };
  },
]);
