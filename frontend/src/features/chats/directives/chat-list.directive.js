import angular from 'angular';
import template from '../templates/chat-list.html?raw';

angular.module('wxn.chats').directive('chatList', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      items: '<',
      query: '<',
      selectedId: '<',
      unread: '<',
      onSelect: '&',
      memberName: '&',
      loading: '<',
      error: '<',
    },
    controller: function () {},
    controllerAs: '$ctrl',
    template,
  };
});
