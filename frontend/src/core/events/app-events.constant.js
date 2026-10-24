import angular from 'angular';

export const AppEvents = Object.freeze({
  SOCKET_CONNECTED: 'wxn:app:socket-connected',
  SOCKET_DISCONNECTED: 'wxn:app:socket-disconnected',
  SOCKET_ERROR: 'wxn:app:socket-error',
  HTTP_ERROR: 'wxn:app:http-error',
});
angular.module('wxn.core').constant('AppEvents', AppEvents);
