import angular from 'angular';
angular.module('wxn.channels').factory('ChannelsService', [
  'ApiClientService',
  function (api) {
    return {
      listChannels() {
        return api.get('/whatsapp/channels');
      },
    };
  },
]);
