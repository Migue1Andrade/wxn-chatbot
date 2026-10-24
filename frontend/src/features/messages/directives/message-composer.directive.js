import angular from 'angular';
import template from '../templates/message-composer.html?raw';

angular.module('wxn.messages').directive('messageComposer', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { conversationId: '<', sending: '<', blocked: '<', reason: '@', memberName: '@' },
    controllerAs: '$ctrl',
    template,
    controller: [
      'EventBusService',
      'MessageEvents',
      function (bus, events) {
        const vm = this;
        vm.text = '';
        vm.$onChanges = (changes) => {
          if (changes.conversationId) vm.text = '';
        };
        vm.submit = () => {
          if (vm.blocked || vm.sending || !vm.text.trim()) return;
          const content = vm.text;
          const conversationId = vm.conversationId;
          bus.emit(events.SEND_REQUESTED, {
            conversationId: vm.conversationId,
            content,
            onSuccess: () => {
              if (vm.conversationId === conversationId && vm.text === content) vm.text = '';
            },
          });
        };
        vm.keydown = (event) => {
          if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            vm.submit();
          }
        };
      },
    ],
  };
});
