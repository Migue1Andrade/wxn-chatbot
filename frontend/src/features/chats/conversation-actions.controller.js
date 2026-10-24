import angular from 'angular';
import { AsyncState } from '../../core/state/async-state.js';
import { ConversationActions } from './conversation-actions.service.js';

angular.module('wxn.chats').controller('ConversationActionsController', [
  '$scope',
  '$timeout',
  'ChatsService',
  'ConversationActionsService',
  'ConversationActivitiesService',
  'EventBusService',
  'ChatEvents',
  function ($scope, $timeout, chats, actions, activities, bus, events) {
    const vm = this;
    vm.operation = new AsyncState();
    vm.historyState = new AsyncState();
    vm.panel = null;
    vm.selection = null;
    vm.reason = '';
    vm.success = null;
    vm.history = [];
    const titles = {
      assume: 'Assumir atendimento',
      assign: 'Atribuir atendente',
      transfer: 'Transferir atendimento',
      close: 'Encerrar atendimento',
      reopen: 'Reabrir atendimento',
      details: 'Detalhes da conversa',
      history: 'Histórico de atendimento',
    };
    const eventTypes = {
      assume: events.ASSIGNED,
      assign: events.ASSIGNED,
      transfer: events.TRANSFERRED,
      close: events.CLOSED,
      reopen: events.REOPENED,
    };
    let epoch = 0;
    let destroyed = false;
    let historyTimer;
    vm.memberName = (id) =>
      (vm.members || []).find((member) => member.id === id)?.name || (id ? 'Membro ' + id : 'Não atribuído');
    vm.responsibleName = () => vm.memberName(vm.conversation?.assignedMemberId);
    vm.channel = () => (vm.channels || []).find((channel) => channel.id === vm.conversation?.channelId);
    vm.panelTitle = () => titles[vm.panel] || '';
    vm.isSelection = () =>
      vm.panel === ConversationActions.ASSIGN || vm.panel === ConversationActions.TRANSFER;
    vm.$onChanges = (changes) => {
      if (changes.conversation && changes.conversation.previousValue?.id !== vm.conversation?.id) {
        epoch++;
        vm.panel = null;
        vm.operation.reset();
        vm.historyState.reset();
        vm.history = [];
        vm.success = null;
      }
    };
    vm.closePanel = () => {
      if (!vm.operation.loading) vm.panel = null;
    };
    vm.open = (panel) => {
      if (vm.operation.loading) return;
      vm.panel = panel;
      vm.selection = null;
      vm.reason = '';
      vm.operation.reset();
      vm.success = null;
      if (panel === 'history') vm.loadHistory();
    };
    vm.loadHistory = () => {
      const id = vm.conversation.id;
      const request = ++epoch;
      vm.historyState.begin();
      activities
        .listEvents(id)
        .then((rows) => {
          if (!destroyed && epoch === request && vm.conversation.id === id) {
            vm.history = rows;
            vm.historyState.succeed();
          }
        })
        .catch((error) => {
          if (epoch === request) vm.historyState.fail(error);
        });
    };
    vm.submit = () => {
      const action = vm.panel;
      if (vm.operation.loading || !vm.actor || !Object.values(ConversationActions).includes(action)) return;
      if (vm.isSelection() && (!vm.selection || !vm.selection.available)) return;
      if (action === ConversationActions.TRANSFER && vm.selection.id === vm.conversation.assignedMemberId) {
        vm.operation.fail(new Error('Selecione outro membro para transferir.'));
        return;
      }
      const id = vm.conversation.id;
      const request = ++epoch;
      const body = {
        actorMemberId: vm.actor.id,
        expectedVersion: vm.conversation.version || 0,
        reason: vm.reason.trim() || null,
      };
      if (vm.isSelection()) body.memberId = vm.selection.id;
      vm.operation.begin();
      actions
        .execute(id, action, body)
        .then((conversation) => {
          bus.emit(eventTypes[action], { conversationId: id, payload: conversation });
          if (!destroyed && epoch === request && vm.conversation.id === id) {
            vm.panel = null;
            vm.success = 'Atendimento atualizado.';
            vm.operation.succeed();
          }
        })
        .catch((error) => {
          if (!destroyed && epoch === request) vm.operation.fail(error);
          if (error.status === 409)
            chats
              .getConversation(id)
              .then((conversation) => bus.emit(events.UPDATED, { conversationId: id, payload: conversation }))
              .catch(() => {});
        });
    };
    const cleanup = [events.ASSIGNED, events.TRANSFERRED, events.CLOSED, events.REOPENED].map((type) =>
      bus.on(type, (event) => {
        if (vm.panel !== 'history' || event.conversationId !== vm.conversation?.id) return;
        $timeout.cancel(historyTimer);
        historyTimer = $timeout(vm.loadHistory, 120);
      }),
    );
    $scope.$on('$destroy', () => {
      destroyed = true;
      epoch++;
      $timeout.cancel(historyTimer);
      cleanup.forEach((unsubscribe) => unsubscribe());
    });
  },
]);
