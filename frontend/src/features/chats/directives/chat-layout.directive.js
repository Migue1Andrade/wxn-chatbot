import angular from 'angular';
import template from '../templates/chat-layout.html?raw';

angular.module('wxn.chats').directive('chatLayout', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {},
    controller: 'ChatsController',
    controllerAs: 'vm',
    template,
  };
});
