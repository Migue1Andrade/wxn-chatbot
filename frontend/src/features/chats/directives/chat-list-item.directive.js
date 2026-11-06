import angular from 'angular';
import { contactInitials, contactTone } from '../../../shared/contact-identity.js';
import template from '../templates/chat-list-item.html?raw';

angular.module('wxn.chats').directive('chatListItem', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { conversation: '<', selected: '<', unread: '<', onSelect: '&', memberName: '@' },
    controller: function () {
      this.initials = contactInitials;
      this.tone = contactTone;
    },
    controllerAs: '$ctrl',
    template,
  };
});
