import angular from 'angular';

export const MessageEvents = Object.freeze({
  RECEIVED: 'wxn:message:received',
  VISIBLE: 'wxn:message:visible',
  SEND_REQUESTED: 'wxn:message:send-requested',
  SENT: 'wxn:message:sent',
  STATUS_UPDATED: 'wxn:message:status-updated',
});
angular.module('wxn.core').constant('MessageEvents', MessageEvents);
