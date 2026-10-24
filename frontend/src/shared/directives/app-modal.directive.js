import angular from 'angular';
import template from '../templates/app-modal.html?raw';

angular.module('wxn.core').directive('appModal', [
  '$timeout',
  function ($timeout) {
    return {
      restrict: 'E',
      scope: {},
      transclude: true,
      bindToController: { open: '<', title: '@', busy: '<', onClose: '&' },
      controllerAs: '$modal',
      template,
      controller: function () {
        this.titleId = 'dialog-' + crypto.randomUUID();
        this.close = () => {
          if (!this.busy) this.onClose();
        };
        this.backdrop = (event) => {
          if (event.target === event.currentTarget) this.close();
        };
      },
      link(scope, element, attributes, ctrl) {
        let previousFocus;
        let focusTimer;
        function keyboard(event) {
          if (!ctrl.open) return;
          if (event.key === 'Escape') {
            event.preventDefault();
            scope.$evalAsync(ctrl.close);
          }
          if (event.key !== 'Tab') return;
          const buttons = [
            ...element[0].querySelectorAll(
              'button:not([disabled]), select:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex="0"]',
            ),
          ].filter((node) => node.getClientRects().length);
          if (!buttons.length) return;
          const first = buttons[0];
          const last = buttons.at(-1);
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
          }
        }
        const unsubscribe = scope.$watch(
          () => ctrl.open,
          (open) => {
            $timeout.cancel(focusTimer);
            if (open) {
              previousFocus = document.activeElement;
              focusTimer = $timeout(() => element[0].querySelector('select, input, button')?.focus(), 0);
            } else if (previousFocus?.isConnected) previousFocus.focus();
          },
        );
        document.addEventListener('keydown', keyboard);
        scope.$on('$destroy', () => {
          unsubscribe();
          $timeout.cancel(focusTimer);
          document.removeEventListener('keydown', keyboard);
        });
      },
    };
  },
]);
