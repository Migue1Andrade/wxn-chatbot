import angular from 'angular';

angular.module('wxn.messages').controller('MessagesController', [
  '$scope',
  '$timeout',
  'MessageHistoryService',
  'MessageSendingService',
  'MessageReadContextService',
  'ChatStateService',
  'DevMemberContextService',
  'EventBusService',
  'ChatEvents',
  'MessageEvents',
  'AppEvents',
  'MemberEvents',
  'MessageActionsService',
  function (
    $scope,
    $timeout,
    histories,
    senders,
    readers,
    chats,
    members,
    bus,
    chatEvents,
    messageEvents,
    appEvents,
    memberEvents,
    actions,
  ) {
    const vm = this;
    vm.actions = actions;
    vm.history = histories.create();
    vm.sender = senders.create();
    vm.reader = readers.create();
    vm.member = members.state;
    vm.state = vm.history.state;
    let eventTimer;
    vm.memberName = (id) =>
      vm.member.members.find((member) => member.id === id)?.name || (id ? 'Membro ' + id : 'Contato');
    vm.operation = () => vm.sender.operation(chats.state.selected?.id);
    vm.error = () => vm.operation().error || vm.history.error || vm.reader.state.error;
    vm.reload = () => {
      if (!chats.state.selected) {
        return;
      }
      actions.capabilities();
      vm.reader.select(chats.state.selected.id);
      vm.history.select(chats.state.selected);
    };
    vm.loadOlder = () => vm.history.loadOlder();
    function incoming(event) {
      vm.history.upsert(event.payload);
    }
    function management(event) {
      if (event.conversationId !== vm.history.conversationId) {
        return;
      }
      actions.capabilities();
      $timeout.cancel(eventTimer);
      eventTimer = $timeout(() => vm.history.loadEvents(), 120);
    }
    function send(command) {
      const conversation = chats.state.selected;
      if (!conversation || command.conversationId !== conversation.id) {
        return;
      }
      const pending = vm.sender.send(
        conversation,
        actions.actor(),
        command.content,
        command.reply,
        command.operationId,
      );
      pending?.then((message) => {
        if (!message) {
          return;
        }
        vm.history.upsert(message);
        if (!['FAILED', 'UNKNOWN'].includes(message.status)) {
          command.onSuccess?.();
        }
      });
    }
    const cleanup = [
      bus.on(chatEvents.SELECTED, vm.reload),
      bus.on('wxn:channel:changed', (event) => {
        if (event.payload?.channelId === chats.state.selected?.channelId) {
          actions.capabilities();
        }
      }),
      bus.on(appEvents.SOCKET_CONNECTED, () => {
        actions.capabilities();
        vm.history.reconcile();
        vm.history.loadEvents();
      }),
      bus.on(messageEvents.RECEIVED, incoming),
      bus.on(messageEvents.SENT, incoming),
      bus.on(messageEvents.STATUS_UPDATED, incoming),
      bus.on(messageEvents.SEND_REQUESTED, send),
      bus.on(messageEvents.VISIBLE, vm.reader.visible),
      bus.on(memberEvents.UPDATED, vm.reader.memberChanged),
    ];
    [chatEvents.ASSIGNED, chatEvents.TRANSFERRED, chatEvents.CLOSED, chatEvents.REOPENED].forEach((type) =>
      cleanup.push(bus.on(type, management)),
    );
    vm.reload();
    $scope.$on('$destroy', () => {
      $timeout.cancel(eventTimer);
      vm.history.dispose();
      vm.reader.dispose();
      cleanup.forEach((unsubscribe) => unsubscribe());
    });
  },
]);
