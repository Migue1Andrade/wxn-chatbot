import angular from 'angular';
let locks = 0;
angular.module('wxn.core').directive('focusRegion', [
  '$parse',
  '$timeout',
  function ($parse, $timeout) {
    return {
      restrict: 'A',
      link(scope, element, attrs) {
        let previous;
        let timer;
        let active = false;
        const nodes = () =>
          [
            ...element[0].querySelectorAll(
              'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]',
            ),
          ].filter((node) => node.getClientRects().length);
        function release() {
          $timeout.cancel(timer);
          if (active) {
            active = false;
            locks--;
            document.body.classList.toggle('wxn-overlay-open', locks > 0);
          }
          if (previous?.isConnected) {
            previous.focus();
            previous = null;
          }
        }
        const off = scope.$watch(
          () => $parse(attrs.focusRegion)(scope),
          (open) => {
            if (!open) {
              release();
              return;
            }
            if (active) {
              return;
            }
            active = true;
            previous = document.activeElement;
            locks++;
            document.body.classList.add('wxn-overlay-open');
            timer = $timeout(() => {
              const target = nodes()[0] || element[0];
              element.attr('tabindex', '-1');
              target.focus();
            }, 0);
          },
        );
        function keyboard(event) {
          if (!active) {
            return;
          }
          if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            scope.$evalAsync(() => $parse(attrs.onRegionClose)(scope));
          }
          if (event.key === 'Tab') {
            const list = nodes();
            const first = list[0];
            const last = list.at(-1);
            if (!first) {
              event.preventDefault();
              return;
            }
            if (
              event.shiftKey &&
              (document.activeElement === first || document.activeElement === element[0])
            ) {
              event.preventDefault();
              last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault();
              first.focus();
            }
          }
        }
        element.on('keydown', keyboard);
        scope.$on('$destroy', () => {
          off();
          release();
          element.off('keydown', keyboard);
        });
      },
    };
  },
]);
