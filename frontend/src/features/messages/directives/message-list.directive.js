import angular from 'angular';
import template from '../templates/message-list.html?raw';
import { operationalEventLabel } from '../../chats/operational-event-label.js';

angular.module('wxn.messages').directive('messageList', [
  '$timeout',
  'EventBusService',
  'MessageEvents',
  function ($timeout, bus, events) {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        messages: '<',
        events: '<',
        loading: '<',
        error: '<',
        conversationId: '<',
        memberName: '&',
        hasMore: '<',
        olderLoading: '<',
        onLoadOlder: '&',
        onRetry: '&',
      },
      controllerAs: '$ctrl',
      template,
      controller: function () {
        const vm = this;
        vm.newMessages = 0;
        vm.entries = [];
        vm.$onChanges = (changes) => {
          const before = changes.messages?.previousValue?.[0];
          const after = changes.messages?.currentValue?.[0];
          if (
            before &&
            after &&
            before.id !== after.id &&
            Date.parse(after.createdAt) <= Date.parse(before.createdAt)
          )
            vm.restore = vm.captureScroll?.();
          vm.entries = [
            ...(vm.messages || []).map((message) => ({
              id: 'message:' + message.id,
              kind: 'message',
              createdAt: message.createdAt,
              message,
            })),
            ...(vm.events || []).map((event) => ({
              id: 'activity:' + event.id,
              kind: 'activity',
              createdAt: event.createdAt,
              event,
            })),
          ].sort(
            (left, right) =>
              (Date.parse(left.createdAt) || 0) - (Date.parse(right.createdAt) || 0) ||
              left.id.localeCompare(right.id),
          );
        };
        vm.isNewDay = (index) =>
          index === 0 ||
          vm.entries[index - 1].createdAt?.slice(0, 10) !== vm.entries[index].createdAt?.slice(0, 10);
        vm.eventLabel = (event) => operationalEventLabel(event, (id) => vm.memberName({ id }));
      },
      link(scope, element, attributes, ctrl) {
        const viewport = element[0].querySelector('[data-message-scroll]');
        let following = true;
        let previousCount = 0;
        let timer;
        ctrl.captureScroll = () => ({ height: viewport.scrollHeight, top: viewport.scrollTop });
        function visible() {
          const latest = ctrl.messages?.at(-1);
          const canRead = following && latest && document.visibilityState === 'visible';
          bus.emit(events.VISIBLE, {
            conversationId: ctrl.conversationId,
            throughMessageId: canRead ? latest.id : null,
          });
        }
        function jump() {
          viewport.scrollTop = viewport.scrollHeight;
          following = true;
          ctrl.newMessages = 0;
          visible();
        }
        ctrl.jumpToLatest = jump;
        function scroll() {
          following = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 80;
          scope.$evalAsync(() => {
            if (following) ctrl.newMessages = 0;
            visible();
          });
          if (
            viewport.scrollTop < 60 &&
            viewport.scrollHeight > viewport.clientHeight &&
            ctrl.hasMore &&
            !ctrl.olderLoading
          )
            scope.$evalAsync(() => ctrl.onLoadOlder());
        }
        viewport.addEventListener('scroll', scroll, { passive: true });
        document.addEventListener('visibilitychange', visible);
        const offConversation = scope.$watch(
          () => ctrl.conversationId,
          () => {
            following = true;
            previousCount = 0;
            ctrl.newMessages = 0;
            ctrl.restore = null;
          },
        );
        const offEntries = scope.$watchCollection(
          () => ctrl.entries,
          () => {
            const count = ctrl.messages?.length || 0;
            if (!following && !ctrl.restore && count > previousCount)
              ctrl.newMessages += count - previousCount;
            previousCount = count;
            $timeout.cancel(timer);
            timer = $timeout(() => {
              if (ctrl.restore) {
                viewport.scrollTop = ctrl.restore.top + viewport.scrollHeight - ctrl.restore.height;
                ctrl.restore = null;
                following = false;
                visible();
              } else if (following) jump();
            }, 0);
          },
        );
        scope.$on('$destroy', () => {
          viewport.removeEventListener('scroll', scroll);
          document.removeEventListener('visibilitychange', visible);
          offConversation();
          offEntries();
          $timeout.cancel(timer);
        });
      },
    };
  },
]);
