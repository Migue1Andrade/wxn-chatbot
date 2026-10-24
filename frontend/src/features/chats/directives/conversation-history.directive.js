import angular from 'angular';
import template from '../templates/conversation-history.html?raw';
import { operationalEventLabel } from '../operational-event-label.js';
angular.module('wxn.chats').directive('conversationHistory', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { events: '<', loading: '<', error: '<', memberName: '&', onRetry: '&' },
    controllerAs: '$history',
    template,
    controller: function () {
      this.label = (event) => operationalEventLabel(event, (id) => this.memberName({ id }));
    },
  };
});
