import angular from 'angular';
import template from '../templates/chat-list-item.html?raw';

angular.module('wxn.chats').directive('chatListItem', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { conversation: '<', selected: '<', unread: '<', onSelect: '&', memberName: '@' },
    controller: function () {},
    controllerAs: '$ctrl',
    template,
  };
});
