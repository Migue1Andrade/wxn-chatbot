import angular from 'angular';

export const MemberEvents = Object.freeze({
  UPDATED: 'wxn:member:updated',
});
angular.module('wxn.core').constant('MemberEvents', MemberEvents);
