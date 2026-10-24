import angular from 'angular';
import template from '../templates/chat-header.html?raw';
angular.module('wxn.chats').directive('chatHeader', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      conversation: '<',
      channelName: '@',
      members: '<',
      channels: '<',
      actor: '<',
      onBack: '&',
    },
    controller: 'ConversationActionsController',
    controllerAs: '$ctrl',
    template,
  };
});
