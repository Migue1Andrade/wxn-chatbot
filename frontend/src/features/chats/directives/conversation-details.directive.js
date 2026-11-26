import angular from 'angular';
import { conversationStatusLabel } from '../conversation-filters.js';
import template from '../templates/conversation-details.html?raw';
angular.module('wxn.chats').directive('conversationDetails', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { conversation: '<', channel: '<', responsible: '@' },
    controllerAs: '$details',
    template,
    controller: function () {
      this.statusLabel = conversationStatusLabel;
    },
  };
});
