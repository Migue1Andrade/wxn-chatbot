import angular from 'angular';
import {
  conversationStatusLabel,
  isConversationFinal,
  visibleConversationStatus,
  waitingForReply,
} from '../conversation-filters.js';
import { contactInitials, contactTone } from '../../../shared/contact-identity.js';
import template from '../templates/chat-list-item.html?raw';

angular.module('wxn.chats').directive('chatListItem', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      conversation: '<',
      selected: '<',
      unread: '<',
      onSelect: '&',
      memberName: '@',
      showActiveOwner: '<',
    },
    controller: function () {
      this.statusLabel = conversationStatusLabel;
      this.visibleStatus = visibleConversationStatus;
      this.waitingForReply = waitingForReply;
      this.isFinal = isConversationFinal;
      this.initials = contactInitials;
      this.tone = contactTone;
    },
    controllerAs: '$ctrl',
    template,
  };
});
