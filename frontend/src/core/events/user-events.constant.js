import angular from 'angular';

export const UserEvents = Object.freeze({
  UPDATED: 'wxn:user:updated',
});
angular.module('wxn.core').constant('UserEvents', UserEvents);
