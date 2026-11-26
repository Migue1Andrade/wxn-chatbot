import angular from 'angular';
import template from '../templates/chat-header.html?raw';
angular.module('wxn.chats').directive('chatHeader', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      conversation: '<',
      channelName: '@',
      connection: '<',
      onRefreshConnection: '&',
      members: '<',
      channels: '<',
      actor: '<',
      isAdmin: '<',
      onBack: '&',
      onTeam: '&',
    },
    controller: 'ConversationActionsController',
    controllerAs: '$ctrl',
    template,
  };
});
