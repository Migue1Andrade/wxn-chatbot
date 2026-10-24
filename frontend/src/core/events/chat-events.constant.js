import angular from 'angular';

export const ChatEvents = Object.freeze({
  SELECTED: 'wxn:chat:selected',
  CREATED: 'wxn:chat:created',
  UPDATED: 'wxn:chat:updated',
  LIST_UPDATED: 'wxn:chat:list-updated',
  ASSIGNED: 'wxn:chat:assigned',
  TRANSFERRED: 'wxn:chat:transferred',
  CLOSED: 'wxn:chat:closed',
  REOPENED: 'wxn:chat:reopened',
  READ_UPDATED: 'wxn:chat:read-updated',
});
angular.module('wxn.core').constant('ChatEvents', ChatEvents);
